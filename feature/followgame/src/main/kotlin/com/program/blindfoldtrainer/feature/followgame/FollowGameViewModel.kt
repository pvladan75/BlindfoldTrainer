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
import com.program.blindfoldtrainer.core.moduleapi.quantity
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.moduleapi.userReason
import com.program.blindfoldtrainer.feature.followgame.data.GameCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /**
     * Dokle se izdržalo pre prve greške — broj tačnih odgovora pre nje.
     *
     * `null` dok greške nema. Ovo je jedini modul u kom se greška gomila kroz
     * desetine poteza, pa je i jedini u kom ovaj broj nešto znači.
     */
    val heldUntil: Int? = null,
    /** Polja koja su već pogođena u pitanju sa više odgovora. */
    val found: Set<Square> = emptySet(),
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
internal data class Setup(val questionCount: Int, val plyGap: Int)

/**
 * Šta težina znači: **na koliko poteza stiže pitanje**.
 *
 * Razmak se u kodu meri polupotezima, a čoveku se kaže u potezima — u partiji se
 * broje potezi, ne polupotezi.
 */
internal fun difficultyDetailOf(difficulty: Difficulty): String =
    "pitanje na ${quantity(setupFor(difficulty).plyGap / 2, "potez", "poteza")}"

internal fun setupFor(difficulty: Difficulty) = when (difficulty) {
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

    /**
     * Koliko slike aplikacija drži umesto tebe.
     *
     * Zamenilo je prekidač „bez ekrana", koji je bio skok sa prve prečke na
     * poslednju. Prečku bira **porudžbina puta** kad je ima, inače podešavanje.
     */
    private val _support = MutableStateFlow(Support.FULL)

    val support: StateFlow<Support> = _support.asStateFlow()

    /** Zadržano za ekran: najniža prečka znači da se tabla ne crta. */
    val isEyesFree: StateFlow<Boolean> = _support
        .map { it == Support.NONE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */

    /** Ponavlja poslednje izgovoreno — potez ili pitanje, šta je poslednje bilo. */
    fun onRepeat() = speaker.repeat()

    /** Prvi dodir na zonu za prekid — traži potvrdu, jer je nepovratno. */
    fun onQuitArmed() = speaker.say { confirmStop }

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

    /**
     * Koji zadatak ova sesija radi.
     *
     * **Jedna sesija — jedan zadatak.** Rezultat nosi jedan `taskId`, pa bi
     * mešanje pitanja u istoj sesiji značilo da se ne zna šta je mereno. Bez
     * porudžbine se radi zatečeni zadatak; put i provera traže izričito.
     */
    private var task: TaskSpec = FOLLOW_WHERE_IS_PIECE

    fun startOnce(
        difficulty: Difficulty,
        requestedSupport: Support? = null,
        taskId: String? = null,
        rounds: Int? = null
    ) {
        task = FOLLOW_TASKS.find { it.id == taskId } ?: FOLLOW_WHERE_IS_PIECE
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty).shortenedTo(rounds)

        viewModelScope.launch {
            // Prvo podešavanje se sačeka: bez toga bi prvi potez mogao da prođe
            // pre nego što se sazna da se vežba bez ekrana, pa ne bi bio izgovoren.
            val eyesFree = settingsRepository.settings.first().eyesFree
            // Porudžbina puta ima prednost; bez nje odlučuje podešavanje.
            // Rezim vise nije prekidac nego polazna precka.
            // prečka — najniža koju zadatak ume.
            val wanted = requestedSupport
                ?: if (eyesFree) task.hardest else Support.FULL
            _support.value = task.nearestSupport(wanted)

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
            if (_support.value == Support.NONE) {
                speaker.say { gameReady }
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
        if (_support.value == Support.NONE) speaker.say(move)

        if (ply % setup.plyGap == 0) askQuestion()
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (state.phase != FollowPhase.QUESTION) return
        val question = state.question ?: return

        val correct = square in question.expected

        // Pitanje sa više odgovora se ne zaključuje na prvom pogotku: skupljaju
        // se polja dok se ne nađu sva. Isto polje dvaput ne znači ništa.
        if (correct && square !in state.found) {
            val found = state.found + square
            if (found.size < question.expected.size) {
                _uiState.update { it.copy(found = found) }
                if (_support.value == Support.NONE) speaker.say(square)
                return
            }
        }

        _uiState.update {
            it.copy(
                phase = FollowPhase.FEEDBACK,
                answerSquare = square,
                wasCorrect = correct,
                solved = it.solved + if (correct) 1 else 0,
                mistakes = it.mistakes + if (correct) 0 else 1,
                // Pamti se **prva** greška, ne poslednja: posle nje je slika već
                // pokvarena, pa ostali odgovori ne mere isto.
                heldUntil = it.heldUntil ?: if (correct) null else it.solved,
                found = emptySet()
            )
        }

        if (_support.value == Support.NONE) {
            // Ispisana ispravka se bez ekrana ne vidi; bez nje se pogrešna
            // slika pozicije nosi dalje kroz celu partiju.
            if (correct) {
                // `this.` jer lokalno `correct` (Boolean) zaklanja rečenicu
                // istog imena iz prijemnika.
                speaker.say { this.correct }
            } else {
                // Ispravka je deo pitanja, jer se razlikuje po vrsti: kod mesta
                // se kaže gde figura jeste, kod napada ko sve gađa.
                speaker.say(question.correction)
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
        val question = if (task.id == FOLLOW_ATTACKERS.id) {
            attackersQuestionFor(position)
        } else {
            questionFor(position)
        } ?: return
        _uiState.update {
            it.copy(
                phase = FollowPhase.QUESTION,
                question = question,
                answerSquare = null,
                questionNumber = it.questionNumber + 1
            )
        }

        // Čeka svoj red, da ne preseče izgovor poteza koji ga je izazvao.
        // Pitanje se izgovara onako kako i piše — tekst zna sámo pitanje, jer se
        // dve vrste razlikuju i po tome šta traže i po tome koliko odgovora ima.
        if (_support.value == Support.NONE) speaker.say(question.prompt, interrupt = false)
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
        if (_support.value == Support.NONE) {
            // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
            speaker.say(interrupt = false) { sessionEndCorrect(state.solved, state.questionNumber) }
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
            completed = state.isFinished && !wasQuit,
            heldUntil = state.heldUntil,
            // Prečka na kojoj je sesija stvarno odrađena — ona koju je modul
            // dobio porudžbinom ili izveo iz podešavanja, ne pretpostavka.
            support = _support.value,
            taskId = task.id,
            bySkill = mapOf(
                task.measures to SkillTally(
                    attempted = state.questionNumber,
                    solved = state.solved,
                    // Vreme je deo mere: tačno a sporo znači da veština
                    // još nije automatska, pa se na njoj ne može graditi dalje.
                    millis = System.currentTimeMillis() - startedAtMillis
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        feedbackJob?.cancel()
        voiceInput.stop()
        speaker.stop()
    }
}

/**
 * Potezi majstorske partije stižu jedan po jedan; povremeno se pita gde koja
 * figura stoji.
 *
 * Meri **ažuriranje** — i to je jedini zadatak u kom se greška gomila kroz
 * desetine poteza, kao u pravoj partiji. Prevod zapisa ide uz to, jer potezi
 * stižu kao reči a ne kao slika.
 *
 * Ovde će stati i pitanja koja još ne postoje — „ko napada ovu figuru" meri
 * kontrolu polja i biće **zaseban zadatak**, ne druga težina ovog.
 */
/**
 * „Ko napada ovu figuru?"
 *
 * Meri **kontrolu polja** — prvu praznu vrstu u tabeli veština. U pravoj partiji
 * naslepo se figure ne gube zato što se zaboravi gde stoje, nego zato što se
 * zaboravi **šta drže**.
 *
 * Isti modul, isti ulaz i ista podrška kao i pitanje o mestu figure — a mere
 * različite stvari. To je i bio razlog da veština pripada zadatku, a ne modulu.
 */
internal val FOLLOW_ATTACKERS = TaskSpec(
    id = "attackers",
    skills = listOf(Skill.SQUARE_CONTROL, Skill.POSITION_HOLD),
    supports = listOf(Support.FULL, Support.NONE),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 25_000, minAccuracy = 0.8f),
        Support.NONE to Benchmark(millisPerAttempt = 40_000, minAccuracy = 0.7f)
    )
)

internal val FOLLOW_WHERE_IS_PIECE = TaskSpec(
    id = "where_is_piece",
    skills = listOf(Skill.POSITION_UPDATE, Skill.NOTATION),
    supports = listOf(Support.FULL, Support.NONE),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 20_000, minAccuracy = 0.85f),
        Support.NONE to Benchmark(millisPerAttempt = 30_000, minAccuracy = 0.8f)
    )
)

/** Oba zadatka ovog modula. Sesija radi jedan, a modul prijavljuje oba. */
internal val FOLLOW_TASKS = listOf(FOLLOW_WHERE_IS_PIECE, FOLLOW_ATTACKERS)

/**
 * Kraća sesija na zahtev provere. `null` — koliko težina kaže.
 *
 * Skraćuje se **samo broj krugova**, ne i njihova težina: provera koja bi uz to
 * olakšala i sadržaj merila bi nešto drugo nego vežba.
 */
private fun Setup.shortenedTo(rounds: Int?): Setup =
    if (rounds == null || rounds <= 0) this else copy(questionCount = rounds)
