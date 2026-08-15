package com.program.blindfoldtrainer.feature.followgame

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.chess.PgnGame
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.feature.followgame.data.GameCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val catalog: GameCatalog
) : ViewModel() {

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

    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)

        viewModelScope.launch {
            val loaded = runCatching { catalog.games() }
            val failure = loaded.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure != null) Log.e(TAG, "Partije nisu učitane", failure)

            val games = loaded.getOrDefault(emptyList())
            if (games.isEmpty()) {
                val reason = failure
                    ?.let { "\n\n${it::class.java.simpleName}: ${it.message}" }
                    .orEmpty()
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
    }

    /** „21. bxc5" za belog, „21... Bg7" za crnog. */
    private fun moveLabel(ply: Int, san: String): String {
        val number = ply / 2 + 1
        return if (ply % 2 == 0) "$number. $san" else "$number... $san"
    }

    private fun finish() {
        feedbackJob?.cancel()
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
            completed = state.isFinished
        )
    }

    override fun onCleared() {
        super.onCleared()
        feedbackJob?.cancel()
    }
}
