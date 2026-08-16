package com.program.blindfoldtrainer.feature.endgame

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.spoken
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.engine.ChessEngine
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.moduleapi.userReason
import com.program.blindfoldtrainer.feature.endgame.data.EndgameCatalog
import com.program.blindfoldtrainer.feature.endgame.data.EndgamePuzzle
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

/** Kako se završila jedna pozicija. */
enum class EndgameOutcome {
    /** Još se igra. */
    IN_PROGRESS,

    /** Mat — pozicija je privedena kraju. */
    MATED,

    /** Matiran je sam igrač. U dobijenoj poziciji redak, ali moguć ishod. */
    LOST,

    /** Pat: pozicija je bila dobijena, a prokockana je u remi. */
    STALEMATE,

    /** Pravilo 50 poteza — mat nije stigao na vreme. */
    FIFTY_MOVES,

    /** Korisnik je odustao i otkrio figure. */
    GAVE_UP
}

data class EndgameUiState(
    val position: Position = Position.STARTING,
    val visibility: PieceVisibility = PieceVisibility.None,
    /**
     * Korisnik još gleda početnu poziciju. Zasebno od [visibility] jer se ona
     * kratko menja i pri svakom potezu — na njoj se ne sme zasnivati UI.
     */
    val isMemorizing: Boolean = true,
    val selectedSquare: Square? = null,
    val lastMove: Move? = null,
    val errorSquare: Square? = null,
    val puzzleNumber: Int = 0,
    val puzzleCount: Int = 0,
    val mistakes: Int = 0,
    val outcome: EndgameOutcome = EndgameOutcome.IN_PROGRESS,
    val statusMessage: String = "",
    val evaluationLabel: String = "",
    val isEngineThinking: Boolean = false,
    val elapsedMillis: Long = 0,
    val isLoading: Boolean = true,
    val infoMessage: String? = null,
    val isFinished: Boolean = false
) {
    val isPlayerTurn: Boolean
        get() = outcome == EndgameOutcome.IN_PROGRESS && !isEngineThinking && !isMemorizing
}

private data class Setup(val puzzleCount: Int, val engineDepth: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(puzzleCount = 3, engineDepth = 10)
    Difficulty.MEDIUM -> Setup(puzzleCount = 3, engineDepth = 12)
    Difficulty.HARD -> Setup(puzzleCount = 3, engineDepth = 14)
}

private const val MOVE_FLASH_MILLIS = 700L
private const val OUTCOME_PAUSE_MILLIS = 2_000L
private const val TAG = "EndgameViewModel"

@HiltViewModel
class EndgameViewModel @Inject constructor(
    private val catalog: EndgameCatalog,
    private val engine: ChessEngine,
    private val speaker: Speaker,
    private val voiceInput: VoiceInput
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    /**
     * Potez se izgovara u dva koraka — polazno pa odredišno polje — jer prolazi
     * kroz isti [onSquareClicked] kao i dodir.
     */
    fun onVoiceInput() {
        voiceInput.listenForSquare { square -> onSquareClicked(square) }
    }

    private val _uiState = MutableStateFlow(EndgameUiState())
    val uiState: StateFlow<EndgameUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var puzzles: List<EndgamePuzzle> = emptyList()
    private var playerColor: Color = Color.WHITE
    private var solvedCount = 0
    private var startedAtMillis = 0L
    private var timerJob: Job? = null
    private var engineJob: Job? = null
    private var isStarted = false

    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        speaker.setRate(0.85f)

        viewModelScope.launch {
            // Motor se podiže dok korisnik još gleda prvu poziciju, da se
            // učitavanje NNUE mreže ne oseti kao zastoj usred partije.
            launch { engine.start() }

            val loaded = runCatching { catalog.puzzles(difficulty) }
            val failure = loaded.exceptionOrNull()
            // runCatching hvata i otkazivanje, a ono nije greška u učitavanju.
            if (failure is CancellationException) throw failure
            if (failure != null) {
                Log.e(TAG, "Zagonetke za $difficulty nisu učitane", failure)
            }

            val available = loaded.getOrDefault(emptyList())
            if (available.isEmpty()) {
                // Razlog ide i na ekran, ne samo u log: bez uređaja na kablu je
                // poruka jedini trag zašto modul nema nijednu poziciju.
                val reason = failure?.let { "\n\n${it.userReason()}" }.orEmpty()
                _uiState.update {
                    it.copy(isLoading = false, infoMessage = "Nema pozicija za ovu težinu.$reason")
                }
                return@launch
            }

            puzzles = available.shuffled().take(setup.puzzleCount)
            startedAtMillis = System.currentTimeMillis()
            startTimer()
            loadPuzzle(0)
        }
    }

    private fun loadPuzzle(index: Int) {
        val puzzle = puzzles[index]
        val position = Position.fromFen(puzzle.fen)

        if (position == null) {
            if (index + 1 >= puzzles.size) finishSession() else loadPuzzle(index + 1)
            return
        }

        playerColor = position.sideToMove

        _uiState.update {
            it.copy(
                position = position,
                // Prvo se pozicija vidi — treba je zapamtiti pre nego što se ugasi.
                visibility = PieceVisibility.All,
                isMemorizing = true,
                selectedSquare = null,
                lastMove = null,
                errorSquare = null,
                puzzleNumber = index + 1,
                puzzleCount = puzzles.size,
                outcome = EndgameOutcome.IN_PROGRESS,
                statusMessage = "Zapamti poziciju, pa igraj",
                evaluationLabel = puzzle.evaluation,
                isLoading = false,
                infoMessage = null
            )
        }
    }

    /** Korisnik je zapamtio poziciju — tabla se gasi i partija počinje. */
    fun onHidePieces() {
        _uiState.update {
            it.copy(
                visibility = PieceVisibility.None,
                isMemorizing = false,
                statusMessage = "Ti si na potezu"
            )
        }
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (!state.isPlayerTurn) return

        val selected = state.selectedSquare
        if (selected == null) {
            // Bira se figura. Naslepo se lako promaši prazno polje —
            // tada se prosto ništa ne dešava.
            val piece = state.position.board[square]
            if (piece != null && piece.color == playerColor) {
                _uiState.update { it.copy(selectedSquare = square, errorSquare = null) }
            }
            return
        }

        if (square == selected) {
            _uiState.update { it.copy(selectedSquare = null) }
            return
        }

        val move = findLegalMove(state.position, selected, square)
        if (move == null) {
            onIllegalMove(square)
            return
        }

        applyPlayerMove(move)
    }

    /**
     * Traži legalan potez sa polja na polje. Promocija se podrazumeva u damu —
     * u ovim pozicijama nema situacije gde bi nešto drugo bilo bolje.
     */
    private fun findLegalMove(position: Position, from: Square, to: Square): Move? {
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        return candidates.firstOrNull { it.promotion == PieceType.QUEEN }
            ?: candidates.firstOrNull()
    }

    private fun onIllegalMove(square: Square) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    errorSquare = square,
                    selectedSquare = null,
                    mistakes = it.mistakes + 1,
                    statusMessage = "Taj potez nije moguć"
                )
            }
            delay(600)
            _uiState.update {
                it.copy(errorSquare = null, statusMessage = "Ti si na potezu")
            }
        }
    }

    private fun applyPlayerMove(move: Move) {
        val next = _uiState.value.position.applyMove(move)
        _uiState.update {
            it.copy(
                position = next,
                selectedSquare = null,
                lastMove = move,
                errorSquare = null,
                visibility = PieceVisibility.only(move.to)
            )
        }

        viewModelScope.launch {
            delay(MOVE_FLASH_MILLIS)
            _uiState.update { it.copy(visibility = PieceVisibility.None) }

            if (checkOutcome(next)) return@launch
            playEngineReply(next)
        }
    }

    private fun playEngineReply(position: Position) {
        engineJob?.cancel()
        engineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isEngineThinking = true, statusMessage = "Protivnik razmišlja…")
            }

            val reply = engine.bestMove(position, setup.engineDepth)
                // Ako motor zataji, biramo bilo koji legalan potez da partija
                // ne stane — bolje slabija odbrana nego zamrznuta tabla.
                ?: position.legalMoves().randomOrNull()

            if (reply == null) {
                _uiState.update { it.copy(isEngineThinking = false) }
                checkOutcome(position)
                return@launch
            }

            val after = position.applyMove(reply)
            speaker.say(reply.spoken())

            _uiState.update {
                it.copy(
                    position = after,
                    lastMove = reply,
                    isEngineThinking = false,
                    visibility = PieceVisibility.only(reply.to),
                    statusMessage = "$reply"
                )
            }
            delay(MOVE_FLASH_MILLIS)
            _uiState.update { it.copy(visibility = PieceVisibility.None) }

            if (!checkOutcome(after)) {
                _uiState.update { it.copy(statusMessage = "Ti si na potezu") }
            }
        }
    }

    /** Vraća `true` ako je pozicija završena. */
    private fun checkOutcome(position: Position): Boolean {
        val outcome = when {
            // Matiran je onaj ko je na potezu — otud provera čiji je red.
            position.isCheckmate ->
                if (position.sideToMove != playerColor) EndgameOutcome.MATED
                else EndgameOutcome.LOST
            position.isStalemate -> EndgameOutcome.STALEMATE
            position.isDrawByFiftyMoveRule -> EndgameOutcome.FIFTY_MOVES
            else -> return false
        }

        if (outcome == EndgameOutcome.MATED) solvedCount++

        _uiState.update {
            it.copy(
                outcome = outcome,
                visibility = PieceVisibility.All,
                isEngineThinking = false,
                statusMessage = messageFor(outcome)
            )
        }

        viewModelScope.launch {
            delay(OUTCOME_PAUSE_MILLIS)
            onNextPuzzle()
        }
        return true
    }

    private fun messageFor(outcome: EndgameOutcome) = when (outcome) {
        EndgameOutcome.MATED -> "Mat! Pozicija je privedena kraju."
        EndgameOutcome.LOST -> "Matiran si — u dobijenoj poziciji."
        EndgameOutcome.STALEMATE -> "Pat — dobijena pozicija je prokockana u remi."
        EndgameOutcome.FIFTY_MOVES -> "Pedeset poteza bez napretka."
        EndgameOutcome.GAVE_UP -> "Figure su otkrivene."
        EndgameOutcome.IN_PROGRESS -> ""
    }

    /** Odustajanje — pozicija se otkrije i ne broji se kao rešena. */
    fun onGiveUp() {
        engineJob?.cancel()
        speaker.stop()
        _uiState.update {
            it.copy(
                outcome = EndgameOutcome.GAVE_UP,
                visibility = PieceVisibility.All,
                isEngineThinking = false,
                statusMessage = messageFor(EndgameOutcome.GAVE_UP)
            )
        }
    }

    fun onNextPuzzle() {
        val next = _uiState.value.puzzleNumber
        if (next >= puzzles.size) finishSession() else loadPuzzle(next)
    }

    private fun finishSession() {
        timerJob?.cancel()
        engineJob?.cancel()
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
            moduleId = ModuleId.ENDGAME,
            difficulty = difficulty,
            attempted = state.puzzleNumber,
            solved = solvedCount,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        engineJob?.cancel()
        engine.stopSearch()
        speaker.stop()
        voiceInput.stop()
    }
}
