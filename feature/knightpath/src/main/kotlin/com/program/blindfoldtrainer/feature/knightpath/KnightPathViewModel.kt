package com.program.blindfoldtrainer.feature.knightpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.chess.KnightPath
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class Feedback { SOLVED, FAILED }

data class KnightPathUiState(
    val start: Square? = null,
    val target: Square? = null,
    /** Najmanji broj poteza — ujedno i broj koji korisnik sme da potroši. */
    val optimalMoves: Int = 0,
    /** Odigrana polja, počev od polaznog. */
    val path: List<Square> = emptyList(),
    /** Polje koje je upravo odbijeno kao nemoguć potez. */
    val errorSquare: Square? = null,
    /** Rešenje — prikazuje se tek kad se zadatak promaši. */
    val solution: List<Square> = emptyList(),
    val taskNumber: Int = 0,
    val taskCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val feedback: Feedback? = null,
    val isFinished: Boolean = false
) {
    /** Polje na kojem skakač trenutno stoji. */
    val current: Square? get() = path.lastOrNull()

    val movesUsed: Int get() = (path.size - 1).coerceAtLeast(0)

    val movesLeft: Int get() = (optimalMoves - movesUsed).coerceAtLeast(0)

    val progress: Float
        get() = if (taskCount == 0) 0f else taskNumber.toFloat() / taskCount

    val isAcceptingInput: Boolean get() = feedback == null && !isFinished && start != null
}

/**
 * Podešavanja po težini. Rastojanje je ono što zadatak čini teškim — dva poteza
 * se vide odmah, četiri se moraju izvesti.
 */
private data class Setup(val taskCount: Int, val distance: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(taskCount = 8, distance = 2)
    Difficulty.MEDIUM -> Setup(taskCount = 10, distance = 3)
    Difficulty.HARD -> Setup(taskCount = 10, distance = 4)
}

private const val SOLVED_PAUSE_MILLIS = 900L
private const val FAILED_PAUSE_MILLIS = 2_600L
private const val ERROR_FLASH_MILLIS = 450L

@HiltViewModel
class KnightPathViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(KnightPathUiState())
    val uiState: StateFlow<KnightPathUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis = 0L
    private var errorJob: Job? = null
    private var resolveJob: Job? = null
    private var isStarted = false

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        startedAtMillis = System.currentTimeMillis()
        _uiState.value = KnightPathUiState(taskCount = setup.taskCount)
        nextTask()
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (!state.isAcceptingInput) return

        val current = state.current ?: return
        val target = state.target ?: return

        if (!KnightPath.isKnightMove(current, square)) {
            // Naslepo se lako promaši polje. Potez se ne prihvata, ali se ni
            // zadatak ne prekida — greška se broji i ide se dalje.
            flashError(square)
            return
        }

        val path = state.path + square
        _uiState.update { it.copy(path = path, errorSquare = null) }

        when {
            square == target -> resolve(Feedback.SOLVED)
            path.size - 1 >= state.optimalMoves -> resolve(Feedback.FAILED)
        }
    }

    /** Odustajanje od zadatka — rešenje se pokaže, zadatak se ne broji kao rešen. */
    fun onGiveUp() {
        if (!_uiState.value.isAcceptingInput) return
        resolve(Feedback.FAILED)
    }

    private fun flashError(square: Square) {
        errorJob?.cancel()
        _uiState.update { it.copy(errorSquare = square, mistakes = it.mistakes + 1) }
        errorJob = viewModelScope.launch {
            delay(ERROR_FLASH_MILLIS)
            _uiState.update { it.copy(errorSquare = null) }
        }
    }

    private fun resolve(feedback: Feedback) {
        errorJob?.cancel()
        resolveJob?.cancel()

        val state = _uiState.value
        val start = state.start
        val target = state.target

        _uiState.update {
            it.copy(
                feedback = feedback,
                errorSquare = null,
                solved = it.solved + if (feedback == Feedback.SOLVED) 1 else 0,
                mistakes = it.mistakes + if (feedback == Feedback.SOLVED) 0 else 1,
                // Posle promašaja se pokazuje jedna najkraća putanja; bez toga
                // se isti zadatak sledeći put promaši na isti način.
                solution = if (feedback == Feedback.SOLVED || start == null || target == null) {
                    emptyList()
                } else {
                    KnightPath.shortestPath(start, target)
                }
            )
        }

        resolveJob = viewModelScope.launch {
            delay(if (feedback == Feedback.SOLVED) SOLVED_PAUSE_MILLIS else FAILED_PAUSE_MILLIS)
            if (_uiState.value.taskNumber >= setup.taskCount) {
                _uiState.update { it.copy(isFinished = true, feedback = null) }
            } else {
                nextTask()
            }
        }
    }

    private fun nextTask() {
        val start = Square(Random.nextInt(64))
        val target = targetFor(start)

        _uiState.update {
            it.copy(
                start = start,
                target = target,
                optimalMoves = KnightPath.distance(start, target),
                path = listOf(start),
                errorSquare = null,
                solution = emptyList(),
                taskNumber = it.taskNumber + 1,
                feedback = null
            )
        }
    }

    /**
     * Odredište na traženom rastojanju. Na praznoj tabli takvo polje postoji sa
     * svakog polazišta za rastojanja koja ovde koristimo, ali se ne oslanjamo na
     * to — ako ga nema, uzima se najveće manje rastojanje.
     */
    private fun targetFor(start: Square): Square =
        (setup.distance downTo 1)
            .firstNotNullOfOrNull { KnightPath.squaresAtDistance(start, it).randomOrNull() }
            ?: KnightPath.movesFrom(start).first()

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.KNIGHT_PATH,
            difficulty = difficulty,
            // Ako je korisnik prekinuo, broji se samo dokle je stigao.
            attempted = state.taskNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished
        )
    }

    override fun onCleared() {
        super.onCleared()
        errorJob?.cancel()
        resolveJob?.cancel()
    }
}
