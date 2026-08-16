package com.program.blindfoldtrainer.feature.followgame

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.listenForSquare
import com.program.blindfoldtrainer.core.chess.PgnGame
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.moduleapi.userReason
import com.program.blindfoldtrainer.feature.followgame.data.GameCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Faza kroz koju prolazi praćenje. */
enum class FollowPhase {
    /** Potezi stižu jedan po jedan. */
    FOLLOWING,

    /** Partija stoji, čeka se odgovor na pitanje. */
    QUESTION,

    /** Odgovor je stigao, pokazuje se tačno polje. */
    FEEDBACK
}

data class FollowGameUiState(
    val white: String = "",
    val black: String = "",
    val event: String = "",
    /** Poslednji odigrani potez, u obliku „21. bxc5" ili „21... Bg7". */
    val lastMoveLabel: String = "",
    val phase: FollowPhase = FollowPhase.FOLLOWING,
    val question: FollowGameQuestion? = null,
    val answerSquare: Square? = null,
    val wasCorrect: Boolean = false,
    val questionNumber: Int = 0,
    val questionCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val isLoading: Boolean = true,
    val infoMessage: String? = null,
    val isFinished: Boolean = false
) {
    val progress: Float
        get() = if (questionCount == 0) 0f else questionNumber.toFloat() / questionCount
}

/**
 * Podešavanja po težini. Razmak je ono što odlučuje: što je više poteza između
 * dva pitanja, to se duže mora držati cela pozicija u glavi.
 */
private data class Setup(val questionCount: Int, val plyGap: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(questionCount = 5, plyGap = 4)
    Difficulty.MEDIUM -> Setup(questionCount = 6, plyGap = 6)
    Difficulty.HARD -> Setup(questionCount = 8, plyGap = 8)
}

private const val FEEDBACK_MILLIS = 1_600L
private const val TAG = "FollowGameViewModel"

@HiltViewModel
class FollowGameViewModel @Inject constructor(
    private val catalog: GameCatalog,
    private val voiceInput: VoiceInput,
    private val speaker: Speaker,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    private val _isEyesFree = MutableStateFlow(Settings.DEFAULT.eyesFree)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */
    val isEyesFree: StateFlow<Boolean> = _isEyesFree.asStateFlow()

    /** Ponavlja poslednje izgovoreno — potez ili pitanje, šta je poslednje bilo. */
    fun onRepeat() = speaker.repeat()

    /** Prvi dodir na zonu za prekid — traži potvrdu, jer je nepovratno. */
    fun onQuitArmed() = speaker.say("Dodirni ponovo da prekineš.")

    /** Prekid sesije — broji se dokle se stiglo, ali ne kao završena sesija. */
    fun onQuit() {
        if (_uiState.value.isFinished) return
        wasQuit = true
        finish()
    }

    /** Odgovor na pitanje sme i da se izgovori; ide kroz isti put kao i dodir. */
    fun onVoiceInput() {
        voiceInput.listenForSquare { square -> onSquareClicked(square) }
    }

    /** Prekid slušanja na dodir — bez toga se upaljen mikrofon ne može ugasiti. */
    fun onVoiceStop() = voiceInput.stop()

    private val _uiState = MutableStateFlow(FollowGameUiState())
    val uiState: StateFlow<FollowGameUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var game: PgnGame? = null
    private var position: Position = Position.STARTING
    private var ply = 0
    private var startedAtMillis = 0L
    private var feedbackJob: Job? = null
    private var isStarted = false

    /** Sesija je prekinuta pre kraja — ne sme da se prijavi kao završena. */
    private var wasQuit = false

    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)

        viewModelScope.launch {
            // Prvo podešavanje se sačeka: bez toga bi prvi potez mogao da prođe
            // pre nego što se sazna da se vežba bez ekrana, pa ne bi bio izgovoren.
            _isEyesFree.value = settingsRepository.settings.first().eyesFree

            val loaded = runCatching { catalog.games() }
            val failure = loaded.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure != null) Log.e(TAG, "Partije nisu učitane", failure)

            val games = loaded.getOrDefault(emptyList())
            if (games.isEmpty()) {
                val reason = failure?.let { "\n\n${it.userReason()}" }.orEmpty()
                _uiState.update {
                    it.copy(isLoading = false, infoMessage = "Nema partija u sadržaju.$reason")
                }
                return@launch
            }

            // Partija mora imati dovoljno poteza za sva pitanja; ako nijedna
            // nema, uzima se najduža koja postoji.
            val needed = setup.questionCount * setup.plyGap
            val chosen = games.filter { it.plyCount >= needed }.randomOrNull()
                ?: games.maxByOrNull { it.plyCount }
                ?: return@launch

            game = chosen
            position = Position.STARTING
            ply = 0
            startedAtMillis = System.currentTimeMillis()

            _uiState.update {
                it.copy(
                    white = chosen.white,
                    black = chosen.black,
                    event = chosen.event,
                    questionCount = setup.questionCount,
                    isLoading = false,
                    infoMessage = null
                )
            }

            // Bez ekrana se ne vidi ni da je partija učitana ni šta se sad
            // očekuje — a prvi potez traži dodir, pa bi se ćutke stajalo.
            if (_isEyesFree.value) {
                speaker.say("Partija je spremna. Dodirni za prvi potez.")
            }
        }
    }

    /** Sledeći potez partije. Kad se napuni razmak, stiže pitanje. */
    fun onNextMove() {
        val current = game ?: return
        if (_uiState.value.phase != FollowPhase.FOLLOWING) return

        if (ply >= current.plyCount) {
            finish()
            return
        }

        val move = current.moves[ply]
        val label = moveLabel(ply, current.sanMoves[ply])
        position = position.applyMove(move)
        ply++

        _uiState.update { it.copy(lastMoveLabel = label) }

        // Bez ekrana je izgovoren potez jedini put do partije. Ide poljima a ne
        // skraćenim zapisom: „Nf3" se ne izgovara, „g1, f3" se izgovara svuda.
        if (_isEyesFree.value) speaker.say(move)

        if (ply % setup.plyGap == 0) askQuestion()
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (state.phase != FollowPhase.QUESTION) return
        val question = state.question ?: return

        val correct = square == question.square
        _uiState.update {
            it.copy(
                phase = FollowPhase.FEEDBACK,
                answerSquare = square,
                wasCorrect = correct,
                solved = it.solved + if (correct) 1 else 0,
                mistakes = it.mistakes + if (correct) 0 else 1
            )
        }

        if (_isEyesFree.value) {
            // Ispisana ispravka se bez ekrana ne vidi; bez nje se pogrešna
            // slika pozicije nosi dalje kroz celu partiju.
            if (correct) {
                speaker.say("Tačno.")
            } else {
                speaker.say("Nije. ${question.piece.spokenName()} je na")
                speaker.say(question.square, interrupt = false)
            }
        }

        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(FEEDBACK_MILLIS)
            if (_uiState.value.questionNumber >= setup.questionCount) {
                finish()
            } else {
                _uiState.update {
                    it.copy(phase = FollowPhase.FOLLOWING, question = null, answerSquare = null)
                }
            }
        }
    }

    private fun askQuestion() {
        val question = questionFor(position) ?: return
        _uiState.update {
            it.copy(
                phase = FollowPhase.QUESTION,
                question = question,
                answerSquare = null,
                questionNumber = it.questionNumber + 1
            )
        }

        // Čeka svoj red, da ne preseče izgovor poteza koji ga je izazvao.
        if (_isEyesFree.value) speaker.say(question.prompt, interrupt = false)
    }

    /** „21. bxc5" za belog, „21... Bg7" za crnog. */
    private fun moveLabel(ply: Int, san: String): String {
        val number = ply / 2 + 1
        return if (ply % 2 == 0) "$number. $san" else "$number... $san"
    }

    private fun finish() {
        feedbackJob?.cancel()
        voiceInput.stop()

        val state = _uiState.value
        if (_isEyesFree.value) {
            // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
            speaker.say(
                "Kraj sesije. Tačno ${state.solved} od ${state.questionNumber}.",
                interrupt = false
            )
        }

        _uiState.update { it.copy(isFinished = true, phase = FollowPhase.FOLLOWING) }
    }

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.FOLLOW_GAME,
            difficulty = difficulty,
            attempted = state.questionNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished && !wasQuit
        )
    }

    override fun onCleared() {
        super.onCleared()
        feedbackJob?.cancel()
        voiceInput.stop()
        speaker.stop()
    }
}
