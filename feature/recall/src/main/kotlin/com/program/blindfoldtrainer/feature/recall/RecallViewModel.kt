package com.program.blindfoldtrainer.feature.recall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.ReconstructionGrade
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.chess.gradeReconstruction
import com.program.blindfoldtrainer.core.chess.randomSparsePosition
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Faza kroz koju prolazi svaki zadatak. */
enum class RecallPhase {
    /** Pozicija se vidi i uči napamet, sat teče. */
    MEMORIZE,

    /** Tabla je prazna, figure se vraćaju iz palete. */
    PLACING,

    /** Poređenje sa zadatom pozicijom. */
    REVIEW
}

data class RecallUiState(
    /** Pozicija koju treba zapamtiti. */
    val target: Board = Board.EMPTY,
    /** Šta je korisnik dosad postavio. */
    val placed: Map<Square, Piece> = emptyMap(),
    /** Figure koje još čekaju u paleti. */
    val palette: List<Piece> = emptyList(),
    val selectedIndex: Int? = null,
    val phase: RecallPhase = RecallPhase.MEMORIZE,
    val remainingMillis: Long = 0,
    val memorizeMillis: Long = 0,
    val grade: ReconstructionGrade? = null,
    val taskNumber: Int = 0,
    val taskCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val isFinished: Boolean = false
) {
    val selectedPiece: Piece? get() = selectedIndex?.let { palette.getOrNull(it) }

    val progress: Float
        get() = if (taskCount == 0) 0f else taskNumber.toFloat() / taskCount

    /** Traka za pamćenje: puna na početku, prazna kad vreme istekne. */
    val memorizeFraction: Float
        get() = if (memorizeMillis <= 0) 0f else (remainingMillis.toFloat() / memorizeMillis)

    /** Tabla koja se prikazuje — zadata pozicija ili ono što je korisnik složio. */
    val visibleBoard: Board
        get() = when (phase) {
            RecallPhase.MEMORIZE, RecallPhase.REVIEW -> target
            RecallPhase.PLACING -> Board.of(placed)
        }
}

/**
 * Podešavanja po težini. Raste broj figura, a pada vreme gledanja — teško je
 * prekratko da se pozicija „pročita" polje po polje.
 */
private data class Setup(val taskCount: Int, val pieceCount: Int, val memorizeMillis: Long)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(taskCount = 5, pieceCount = 3, memorizeMillis = 6_000)
    Difficulty.MEDIUM -> Setup(taskCount = 6, pieceCount = 4, memorizeMillis = 5_000)
    Difficulty.HARD -> Setup(taskCount = 6, pieceCount = 5, memorizeMillis = 4_000)
}

private const val REVIEW_PAUSE_MILLIS = 2_600L
private const val TICK_MILLIS = 100L

@HiltViewModel
class RecallViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RecallUiState())
    val uiState: StateFlow<RecallUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis = 0L
    private var timerJob: Job? = null
    private var reviewJob: Job? = null
    private var isStarted = false

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        startedAtMillis = System.currentTimeMillis()
        _uiState.value = RecallUiState(
            taskCount = setup.taskCount,
            memorizeMillis = setup.memorizeMillis
        )
        nextTask()
    }

    /** Korisnik je zapamtio pre isteka vremena i ne želi da čeka. */
    fun onReadyToPlace() {
        if (_uiState.value.phase != RecallPhase.MEMORIZE) return
        startPlacing()
    }

    fun onPaletteClicked(index: Int) {
        val state = _uiState.value
        if (state.phase != RecallPhase.PLACING) return
        if (index !in state.palette.indices) return

        // Ponovni dodir na izabranu figuru je poništava — inače nema načina da
        // se odustane od izbora osim postavljanja.
        _uiState.update { it.copy(selectedIndex = if (it.selectedIndex == index) null else index) }
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (state.phase != RecallPhase.PLACING) return

        val existing = state.placed[square]
        if (existing != null) {
            // Dodir na zauzeto polje vraća figuru u paletu — ispravka pogrešnog
            // polja ne sme da traži poništavanje cele rekonstrukcije.
            _uiState.update {
                it.copy(
                    placed = it.placed - square,
                    palette = it.palette + existing,
                    selectedIndex = null
                )
            }
            return
        }

        val index = state.selectedIndex ?: return
        val piece = state.palette.getOrNull(index) ?: return

        val palette = state.palette.toMutableList().also { it.removeAt(index) }
        _uiState.update {
            it.copy(
                placed = it.placed + (square to piece),
                palette = palette,
                selectedIndex = null
            )
        }

        if (palette.isEmpty()) finishTask()
    }

    /** Predaja — ono što je postavljeno se ocenjuje kakvo jeste. */
    fun onGiveUp() {
        if (_uiState.value.phase != RecallPhase.PLACING) return
        finishTask()
    }

    private fun startPlacing() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                phase = RecallPhase.PLACING,
                remainingMillis = 0,
                // Paleta se meša da redosled ne oda kojim su redom figure
                // postavljene na tablu.
                palette = it.target.occupied().map { (_, piece) -> piece }.shuffled(),
                placed = emptyMap(),
                selectedIndex = null
            )
        }
    }

    private fun finishTask() {
        val state = _uiState.value
        val grade = gradeReconstruction(state.target, state.placed)

        _uiState.update {
            it.copy(
                phase = RecallPhase.REVIEW,
                grade = grade,
                selectedIndex = null,
                solved = it.solved + if (grade.isPerfect) 1 else 0,
                // Svako pogrešno i svako propušteno polje je po jedna greška.
                mistakes = it.mistakes + grade.wrong.size + grade.missed.size
            )
        }

        reviewJob?.cancel()
        reviewJob = viewModelScope.launch {
            delay(REVIEW_PAUSE_MILLIS)
            if (_uiState.value.taskNumber >= setup.taskCount) {
                _uiState.update { it.copy(isFinished = true) }
            } else {
                nextTask()
            }
        }
    }

    private fun nextTask() {
        val target = randomSparsePosition(setup.pieceCount)

        _uiState.update {
            it.copy(
                target = target,
                placed = emptyMap(),
                palette = emptyList(),
                selectedIndex = null,
                phase = RecallPhase.MEMORIZE,
                remainingMillis = setup.memorizeMillis,
                grade = null,
                taskNumber = it.taskNumber + 1
            )
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = setup.memorizeMillis
            while (remaining > 0) {
                delay(TICK_MILLIS)
                remaining -= TICK_MILLIS
                _uiState.update { it.copy(remainingMillis = remaining.coerceAtLeast(0)) }
            }
            startPlacing()
        }
    }

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.RECALL,
            difficulty = difficulty,
            // Ako je korisnik prekinuo, broji se samo dokle je stigao.
            attempted = state.taskNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished,
            // Zadatak zna samo punu podršku — slaže se iz palete, a paleta
            // traži oko.
            support = Support.FULL,
            taskId = RECALL_RECONSTRUCT.id,
            bySkill = mapOf(
                RECALL_RECONSTRUCT.measures to SkillTally(
                    attempted = state.taskNumber,
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
        timerJob?.cancel()
        reviewJob?.cancel()
    }
}

/**
 * Pozicija se vidi nekoliko sekundi, pa se vraća po sećanju.
 *
 * Meri **držanje**, i to je jedini zadatak koji ga meri čisto: nema poteza koji
 * bi sliku menjali, pa ne meri ni ažuriranje ni oporavak — samo koliko se veza
 * figura–polje drži odjednom.
 *
 * Zna samo punu podršku. Rešava se vraćanjem figura iz palete na tablu, a to je
 * i meta koja se dodiruje i izbor koji se gleda; zone tu ne pomažu.
 */
internal val RECALL_RECONSTRUCT = TaskSpec(
    id = "reconstruct",
    skills = listOf(Skill.POSITION_HOLD),
    supports = listOf(Support.FULL),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 60_000, minAccuracy = 0.8f)
    )
)
