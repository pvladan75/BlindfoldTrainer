package com.program.blindfoldtrainer.feature.minefield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.listenForSquare
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.chess.KnightPath
import com.program.blindfoldtrainer.core.chess.Minefield
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.chess.randomMinefield
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zašto potez nije prošao. Razlog se kaže, jer se iz njega uči. */
enum class Refusal { OCCUPIED, ATTACKED, NOT_KNIGHT_MOVE }

data class MinefieldUiState(
    val puzzle: Minefield? = null,
    val current: Square? = null,
    /** Polja kroz koja se prošlo, uključujući polazno. */
    val walked: List<Square> = emptyList(),
    val taskNumber: Int = 0,
    val taskCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val refusal: Refusal? = null,
    val isSolved: Boolean = false,
    val isFinished: Boolean = false
) {
    val moves: Int get() = (walked.size - 1).coerceAtLeast(0)

    val progress: Float
        get() = if (taskCount == 0) 0f else taskNumber.toFloat() / taskCount
}

/**
 * Skakač kroz polja koja protivnik drži.
 *
 * Prvi modul koji pita **šta protivnik kontroliše**, a ne gde su figure. U
 * pravoj partiji naslepo se figure ne gube zato što se zaboravi gde stoje, nego
 * zato što se zaboravi šta drže.
 *
 * Zaseban modul, a ne težina u „Putanja skakača", jer se menja uputstvo: tamo je
 * „stigni u najmanje poteza", ovde „stigni živ". Menja se i veština — skakačeva
 * geometrija naspram protivnikove kontrole.
 */
@HiltViewModel
class MinefieldViewModel @Inject constructor(
    private val speaker: Speaker,
    private val voiceInput: VoiceInput,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MinefieldUiState())
    val uiState: StateFlow<MinefieldUiState> = _uiState.asStateFlow()

    private val _support = MutableStateFlow(Support.FULL)
    val support: StateFlow<Support> = _support.asStateFlow()

    /** Zadržano za ekran: najniža prečka znači da se tabla ne crta. */
    val isEyesFree: StateFlow<Boolean> = _support
        .map { it == Support.NONE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    private var difficulty: Difficulty = Difficulty.EASY
    private var setup: Setup = setupFor(Difficulty.EASY)
    private var task: TaskSpec = MINEFIELD_SAFE_PATH
    private var startedAtMillis = 0L
    private var isStarted = false
    private var wasQuit = false
    private var nextJob: Job? = null

    fun startOnce(
        difficulty: Difficulty,
        requestedSupport: Support? = null,
        taskId: String? = null
    ) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        task = MINEFIELD_TASKS.find { it.id == taskId } ?: MINEFIELD_SAFE_PATH

        viewModelScope.launch {
            val eyesFree = settingsRepository.settings.first().eyesFree
            val wanted = requestedSupport ?: if (eyesFree) task.hardest else Support.FULL
            _support.value = task.nearestSupport(wanted)

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = MinefieldUiState(taskCount = setup.taskCount)
            nextPuzzle()
        }
    }

    fun onRepeat() = speaker.repeat()

    fun onVoiceInput() = voiceInput.listenForSquare { square -> onSquareClicked(square) }

    fun onVoiceStop() = voiceInput.stop()

    fun onGiveUpArmed() = speaker.say { confirmGiveUp }

    fun onGiveUp() {
        if (_uiState.value.isFinished) return
        wasQuit = true
        nextJob?.cancel()
        voiceInput.stop()
        _uiState.update { it.copy(isFinished = true) }
    }

    /**
     * Jedan skok.
     *
     * Odbijen potez **kaže zašto** je odbijen: „tu stoji figura" i „to polje je
     * napadnuto" su dve različite greške, a iz druge se uči ono zbog čega modul
     * i postoji.
     */
    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        val current = state.current ?: return
        if (state.isSolved || state.isFinished) return

        if (!KnightPath.isKnightMove(current, square)) {
            refuse(Refusal.NOT_KNIGHT_MOVE)
            return
        }

        if (puzzle.board[square] != null) {
            refuse(Refusal.OCCUPIED)
            return
        }

        if (!puzzle.isSafe(square)) {
            refuse(Refusal.ATTACKED)
            return
        }

        val walked = state.walked + square
        val reached = square == puzzle.target

        _uiState.update {
            it.copy(
                current = square,
                walked = walked,
                refusal = null,
                isSolved = reached,
                solved = it.solved + if (reached) 1 else 0
            )
        }

        if (_support.value == Support.NONE) speaker.say(square)

        if (reached) {
            speaker.say(interrupt = false) { correctInMoves(walked.size - 1) }
            scheduleNext()
        }
    }

    private fun refuse(refusal: Refusal) {
        _uiState.update { it.copy(refusal = refusal, mistakes = it.mistakes + 1) }

        // Razlog se izgovara uvek, ne samo bez ekrana: on je ovde sama pouka, a
        // ne obaveštenje da je dodir primljen.
        speaker.say {
            when (refusal) {
                Refusal.OCCUPIED -> pieceInTheWay
                Refusal.ATTACKED -> squareIsAttacked
                Refusal.NOT_KNIGHT_MOVE -> notKnightMove
            }
        }
    }

    private fun scheduleNext() {
        nextJob?.cancel()
        nextJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SOLVED_PAUSE_MILLIS)
            if (_uiState.value.taskNumber >= setup.taskCount) {
                _uiState.update { it.copy(isFinished = true) }
            } else {
                nextPuzzle()
            }
        }
    }

    private fun nextPuzzle() {
        // Nerešiv raspored se odbacuje pri pravljenju; ako se ipak ne nađe
        // nijedan, olakšava se umesto da modul stane bez zadatka.
        val puzzle = randomMinefield(
            pieceCount = setup.pieceCount,
            avoidAttacked = task.id == MINEFIELD_SAFE_PATH.id,
            minMoves = setup.minMoves
        ) ?: randomMinefield(
            pieceCount = setup.pieceCount / 2,
            avoidAttacked = task.id == MINEFIELD_SAFE_PATH.id,
            minMoves = 2
        ) ?: return

        _uiState.update {
            it.copy(
                puzzle = puzzle,
                current = puzzle.start,
                walked = listOf(puzzle.start),
                taskNumber = it.taskNumber + 1,
                refusal = null,
                isSolved = false
            )
        }

        announce(puzzle)
    }

    private fun announce(puzzle: Minefield) {
        speaker.say { knightIsOn }
        speaker.say(puzzle.start, interrupt = false)
        speaker.say(interrupt = false) { goal }
        speaker.say(puzzle.target, interrupt = false)

        // Bez table se pozicija mora i čuti, inače se ne zna šta se izbegava.
        if (_support.value == Support.NONE) speaker.say(puzzle.board, interrupt = false)
    }

    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.MINEFIELD,
            difficulty = difficulty,
            attempted = state.taskNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished && !wasQuit,
            support = _support.value,
            taskId = task.id,
            bySkill = mapOf(
                task.measures to SkillTally(
                    attempted = state.taskNumber,
                    solved = state.solved,
                    millis = System.currentTimeMillis() - startedAtMillis
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceInput.stop()
        speaker.stop()
    }
}

private data class Setup(val taskCount: Int, val pieceCount: Int, val minMoves: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(taskCount = 5, pieceCount = 4, minMoves = 2)
    Difficulty.MEDIUM -> Setup(taskCount = 6, pieceCount = 7, minMoves = 3)
    Difficulty.HARD -> Setup(taskCount = 6, pieceCount = 10, minMoves = 3)
}

/**
 * Stigni do cilja **ne stajući na polje koje crni drži**.
 *
 * Meri **kontrolu polja**: put se ne bira po tome kuda skakač može, nego po tome
 * šta protivnik pokriva. Uz to ide računanje, jer se put mora sagledati unapred.
 */
internal val MINEFIELD_SAFE_PATH = TaskSpec(
    id = "safe_path",
    skills = listOf(Skill.SQUARE_CONTROL, Skill.CALCULATION, Skill.POSITION_HOLD),
    supports = listOf(Support.FULL, Support.NONE),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 45_000, minAccuracy = 0.8f),
        Support.NONE to Benchmark(millisPerAttempt = 90_000, minAccuracy = 0.7f)
    )
)

/**
 * Lakši oblik: **ne uzmi nijednu figuru**, ali napadnuta polja su dozvoljena.
 *
 * Meri geometriju skakača kroz prorešetanu tablu; kontrola polja tu još ne
 * ulazi, pa je ovo prirodan ulaz u teži zadatak.
 */
internal val MINEFIELD_NO_CAPTURE = TaskSpec(
    id = "no_capture",
    skills = listOf(Skill.PIECE_GEOMETRY, Skill.POSITION_HOLD),
    supports = listOf(Support.FULL, Support.NONE),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 30_000, minAccuracy = 0.85f),
        Support.NONE to Benchmark(millisPerAttempt = 60_000, minAccuracy = 0.75f)
    )
)

internal val MINEFIELD_TASKS = listOf(MINEFIELD_NO_CAPTURE, MINEFIELD_SAFE_PATH)

private const val SOLVED_PAUSE_MILLIS = 1_500L
