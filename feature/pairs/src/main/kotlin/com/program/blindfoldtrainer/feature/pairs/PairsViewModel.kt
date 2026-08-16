package com.program.blindfoldtrainer.feature.pairs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.spoken
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.moduleapi.userReason
import com.program.blindfoldtrainer.feature.pairs.data.PairsPuzzle
import com.program.blindfoldtrainer.feature.pairs.data.PuzzleCatalog
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

/** Faza kroz koju prolazi svaka zagonetka. */
enum class PairsPhase {
    /** Pozicija se vidi, korisnik je uči napamet. */
    MEMORIZE,

    /** Potez je izgovoren; čeka se da korisnik pokaže polje. */
    AWAITING_INPUT,

    /** Zagonetka je rešena, kratka čestitka pre sledeće. */
    SOLVED,

    /** Korisnik je odustao — figure su otkrivene. */
    REVEALED
}

data class PairsUiState(
    val board: Board = Board.EMPTY,
    val visibility: PieceVisibility = PieceVisibility.All,
    val phase: PairsPhase = PairsPhase.MEMORIZE,
    val moveHighlight: Move? = null,
    val feedbackSquare: Square? = null,
    val feedbackIsCorrect: Boolean = false,
    val puzzleNumber: Int = 0,
    val puzzleCount: Int = 0,
    val stepNumber: Int = 0,
    val stepCount: Int = 0,
    val mistakes: Int = 0,
    val elapsedMillis: Long = 0,
    val lastSpokenMove: String = "",
    val isLoading: Boolean = true,
    val infoMessage: String? = null,
    val isFinished: Boolean = false
)

/** Sastav sesije po težini. */
private data class Setup(val pieceCount: Int, val stepsPerPuzzle: Int, val puzzleCount: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(pieceCount = 3, stepsPerPuzzle = 8, puzzleCount = 5)
    Difficulty.MEDIUM -> Setup(pieceCount = 4, stepsPerPuzzle = 12, puzzleCount = 5)
    Difficulty.HARD -> Setup(pieceCount = 5, stepsPerPuzzle = 16, puzzleCount = 5)
}

private const val PIECE_FLASH_MILLIS = 550L
private const val FEEDBACK_MILLIS = 400L
private const val SOLVED_PAUSE_MILLIS = 1_400L
private const val TAG = "PairsViewModel"

@HiltViewModel
class PairsViewModel @Inject constructor(
    private val catalog: PuzzleCatalog,
    private val speaker: Speaker
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairsUiState())
    val uiState: StateFlow<PairsUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var puzzles: List<PairsPuzzle> = emptyList()
    private var currentPuzzle: PairsPuzzle? = null
    private var solvedPuzzles = 0
    private var startedAtMillis = 0L
    private var timerJob: Job? = null
    private var playJob: Job? = null
    private var isStarted = false
    private var currentPuzzleFailed = false

    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        speaker.setRate(0.85f)

        viewModelScope.launch {
            val loaded = runCatching {
                buildList {
                    repeat(setup.puzzleCount) {
                        catalog.randomPuzzle(setup.pieceCount, setup.stepsPerPuzzle)?.let(::add)
                    }
                }.distinctBy { it.id }
            }
            val failure = loaded.exceptionOrNull()
            // runCatching hvata i otkazivanje, a ono nije greška u učitavanju.
            if (failure is CancellationException) throw failure
            if (failure != null) {
                Log.e(TAG, "Zagonetke za $difficulty nisu učitane", failure)
            }

            val available = loaded.getOrDefault(emptyList())
            if (available.isEmpty()) {
                // Razlog ide i na ekran, ne samo u log: bez uređaja na kablu je
                // poruka jedini trag zašto modul nema nijednu zagonetku.
                val reason = failure?.let { "\n\n${it.userReason()}" }.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Nema zagonetki za ovu težinu.$reason"
                    )
                }
                return@launch
            }

            puzzles = available
            startedAtMillis = System.currentTimeMillis()
            startTimer()
            loadPuzzle(0)
        }
    }

    /** Korisnik je pogledao poziciju i spreman je da krene. */
    fun onBeginPuzzle() {
        if (_uiState.value.phase != PairsPhase.MEMORIZE) return
        playNextMove()
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (state.phase != PairsPhase.AWAITING_INPUT || state.feedbackSquare != null) return

        val puzzle = currentPuzzle ?: return
        val expected = Square.fromAlgebraic(puzzle.solution[state.stepNumber - 1].interactingSquare)

        if (square == expected) {
            onCorrectSquare(square)
        } else {
            onWrongSquare(square)
        }
    }

    private fun onCorrectSquare(square: Square) {
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackSquare = square, feedbackIsCorrect = true) }
            delay(FEEDBACK_MILLIS)
            _uiState.update { it.copy(feedbackSquare = null, moveHighlight = null) }

            if (_uiState.value.stepNumber >= setup.stepsPerPuzzle) {
                finishPuzzle()
            } else {
                playNextMove()
            }
        }
    }

    private fun onWrongSquare(square: Square) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    feedbackSquare = square,
                    feedbackIsCorrect = false,
                    mistakes = it.mistakes + 1
                )
            }
            delay(FEEDBACK_MILLIS)
            _uiState.update { it.copy(feedbackSquare = null) }
        }
    }

    /**
     * Ponavlja poslednji izgovoreni potez. Naslepo se lako propusti šta je
     * rečeno, a bez toga korisnik ostaje bez ijednog načina da se vrati u tok.
     */
    fun onRepeatMove() {
        val puzzle = currentPuzzle ?: return
        val state = _uiState.value
        if (state.phase != PairsPhase.AWAITING_INPUT) return

        val step = puzzle.solution.getOrNull(state.stepNumber - 1) ?: return
        val from = Square.fromAlgebraic(step.moveNotation.substringBefore('-')) ?: return
        val to = Square.fromAlgebraic(step.moveNotation.substringAfter('-')) ?: return
        speaker.say(Move(from, to).spoken())
    }

    /** Odustajanje — figure se otkriju, ali zagonetka se ne broji kao rešena. */
    fun onRevealPieces() {
        if (!currentPuzzleFailed) currentPuzzleFailed = true
        playJob?.cancel()
        speaker.stop()
        _uiState.update {
            it.copy(phase = PairsPhase.REVEALED, visibility = PieceVisibility.All)
        }
    }

    fun onNextPuzzle() {
        val next = _uiState.value.puzzleNumber
        if (next >= puzzles.size) finishSession() else loadPuzzle(next)
    }

    private fun loadPuzzle(index: Int) {
        currentPuzzleFailed = false
        val puzzle = puzzles[index]
        currentPuzzle = puzzle

        val board = Board.fromPlacementFen(puzzle.initialFen)
        if (board == null) {
            // Neispravan zapis u sadržaju — preskačemo umesto da rušimo sesiju.
            if (index + 1 >= puzzles.size) finishSession() else loadPuzzle(index + 1)
            return
        }

        _uiState.update {
            it.copy(
                board = board,
                visibility = PieceVisibility.All,
                phase = PairsPhase.MEMORIZE,
                moveHighlight = null,
                feedbackSquare = null,
                puzzleNumber = index + 1,
                puzzleCount = puzzles.size,
                stepNumber = 0,
                stepCount = setup.stepsPerPuzzle,
                isLoading = false,
                infoMessage = null
            )
        }
    }

    /**
     * Izgovara sledeći potez i pušta ga na tabli. Naslepo se figura nakratko
     * vidi na polaznom pa na odredišnom polju — to je jedini trag koji korisnik
     * dobija i po njemu održava sliku pozicije u glavi.
     */
    private fun playNextMove() {
        val puzzle = currentPuzzle ?: return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val stepIndex = _uiState.value.stepNumber
            val step = puzzle.solution.getOrNull(stepIndex) ?: return@launch

            val from = Square.fromAlgebraic(step.moveNotation.substringBefore('-'))
            val to = Square.fromAlgebraic(step.moveNotation.substringAfter('-'))
            if (from == null || to == null) return@launch

            val piece = _uiState.value.board[from] ?: return@launch
            val move = Move(from, to)
            val boardAfter = _uiState.value.board.withPieces(from to null, to to piece)

            speaker.say(move.spoken())

            _uiState.update {
                it.copy(
                    phase = PairsPhase.AWAITING_INPUT,
                    stepNumber = stepIndex + 1,
                    moveHighlight = move,
                    lastSpokenMove = "${from} → ${to}",
                    visibility = PieceVisibility.only(from)
                )
            }
            delay(PIECE_FLASH_MILLIS)

            _uiState.update {
                it.copy(board = boardAfter, visibility = PieceVisibility.only(to))
            }
            delay(PIECE_FLASH_MILLIS)

            _uiState.update { it.copy(visibility = PieceVisibility.None) }
        }
    }

    private fun finishPuzzle() {
        if (!currentPuzzleFailed) solvedPuzzles++
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = PairsPhase.SOLVED, visibility = PieceVisibility.All)
            }
            delay(SOLVED_PAUSE_MILLIS)
            onNextPuzzle()
        }
    }

    private fun finishSession() {
        timerJob?.cancel()
        speaker.stop()
        _uiState.update { it.copy(isFinished = true) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { it.copy(elapsedMillis = it.elapsedMillis + 1_000) }
            }
        }
    }

    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.PAIRS,
            difficulty = difficulty,
            attempted = state.puzzleNumber,
            solved = solvedPuzzles,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        playJob?.cancel()
        speaker.stop()
    }
}
