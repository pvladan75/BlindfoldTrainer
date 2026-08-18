package com.program.blindfoldtrainer.feature.knightpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.listenForSquare
import com.program.blindfoldtrainer.core.chess.KnightPath
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
class KnightPathViewModel @Inject constructor(
    private val speaker: Speaker,
    private val voiceInput: VoiceInput,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnightPathUiState())
    val uiState: StateFlow<KnightPathUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    private val _isEyesFree = MutableStateFlow(Settings.DEFAULT.eyesFree)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */
    val isEyesFree: StateFlow<Boolean> = _isEyesFree.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis = 0L
    private var errorJob: Job? = null
    private var resolveJob: Job? = null
    private var isStarted = false

    /**
     * Sluša do prvog prepoznatog polja i prosleđuje ga kao da je dodirnuto.
     * Glas i dodir zato prolaze kroz istu proveru — nema drugog puta do poteza.
     */
    fun onVoiceInput() {
        voiceInput.listenForSquare { square -> onSquareClicked(square) }
    }

    /** Prekid slušanja na dodir — bez toga se upaljen mikrofon ne može ugasiti. */
    fun onVoiceStop() = voiceInput.stop()

    /** Ponavlja poslednje izgovoreno — nisi dočuo, a ne da si izgubio putanju. */
    fun onRepeat() = speaker.repeat()

    /**
     * Čita dokle se stiglo: gde skakač stoji, kuda ide i koliko poteza ostaje.
     * Bez ekrana je to jedini način da se zapis putanje ponovo sastavi.
     */
    fun onReadState() {
        val state = _uiState.value
        val current = state.current ?: return
        val target = state.target ?: return
        speaker.say { knightIsOn }
        speaker.say(current, interrupt = false)
        speaker.say(interrupt = false) { goal }
        speaker.say(target, interrupt = false)
        speaker.say(interrupt = false) { movesLeft(state.movesLeft) }
    }

    /** Prvi dodir na zonu za odustajanje — traži potvrdu, jer je nepovratno. */
    fun onGiveUpArmed() = speaker.say { confirmGiveUp }

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)

        viewModelScope.launch {
            // Prvo podešavanje se sačeka: bez toga bi prvi zadatak stigao pre
            // nego što se sazna da se vežba bez ekrana, pa ne bi bio izgovoren.
            _isEyesFree.value = settingsRepository.settings.first().eyesFree

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = KnightPathUiState(taskCount = setup.taskCount)
            nextTask()
        }
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

        // Primljeno polje se izgovara: kad potez stiže glasom, to je jedina
        // potvrda da je prepoznato ono što je i rečeno.
        if (_isEyesFree.value) speaker.say(square)

        when {
            square == target -> resolve(Feedback.SOLVED)
            path.size - 1 >= state.optimalMoves -> resolve(Feedback.FAILED)
        }
    }

    /** Odustajanje od zadatka — rešenje se pokaže, zadatak se ne broji kao rešen. */
    fun onGiveUp() {
        if (!_uiState.value.isAcceptingInput) return
        // Mikrofon ne sme da ostane upaljen kad se od korisnika više ništa ne traži.
        voiceInput.stop()
        resolve(Feedback.FAILED)
    }

    private fun flashError(square: Square) {
        errorJob?.cancel()
        _uiState.update { it.copy(errorSquare = square, mistakes = it.mistakes + 1) }
        if (_isEyesFree.value) speaker.say { notKnightMove }
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

        if (_isEyesFree.value) sayOutcome(feedback)

        resolveJob = viewModelScope.launch {
            delay(if (feedback == Feedback.SOLVED) SOLVED_PAUSE_MILLIS else FAILED_PAUSE_MILLIS)
            if (_uiState.value.taskNumber >= setup.taskCount) {
                finish()
            } else {
                nextTask()
            }
        }
    }

    /**
     * Ishod zadatka naglas. Posle promašaja se izgovori i najkraća putanja —
     * bez ekrana je to jedini način da se vidi kuda je trebalo ići.
     */
    private fun sayOutcome(feedback: Feedback) {
        val state = _uiState.value
        if (feedback == Feedback.SOLVED) {
            speaker.say { correctInMoves(state.optimalMoves) }
            return
        }

        speaker.say { shortestGoesLikeThis }
        state.solution.forEach { speaker.say(it, interrupt = false) }
    }

    private fun finish() {
        voiceInput.stop()
        val state = _uiState.value
        if (_isEyesFree.value) {
            // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
            speaker.say(interrupt = false) { sessionEndSolved(state.solved, state.taskNumber) }
        }
        _uiState.update { it.copy(isFinished = true, feedback = null) }
    }

    private fun nextTask() {
        voiceInput.stop()
        val start = Square(Random.nextInt(64))
        val target = targetFor(start)
        val optimalMoves = KnightPath.distance(start, target)

        _uiState.update {
            it.copy(
                start = start,
                target = target,
                optimalMoves = optimalMoves,
                path = listOf(start),
                errorSquare = null,
                solution = emptyList(),
                taskNumber = it.taskNumber + 1,
                feedback = null
            )
        }

        if (!_isEyesFree.value) return

        // Zadatak čeka svoj red, da ne preseče izgovor prethodnog ishoda.
        speaker.say(interrupt = false) { knightFrom }
        speaker.say(start, interrupt = false)
        speaker.say(interrupt = false) { toSquare }
        speaker.say(target, interrupt = false)
        speaker.say(interrupt = false) { inMoves(optimalMoves) }
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
            completed = state.isFinished,
            // Prečka na kojoj je sesija stvarno odrađena. Zasad su zauzeti samo
            // krajevi lestvice — modul još ne prima porudžbinu, nego čita
            // podešavanje, ali profil od sada zna koliko uspeh vredi.
            support = if (_isEyesFree.value) Support.NONE else Support.FULL,
            bySkill = mapOf(
                KNIGHT_SHORTEST_PATH.measures to SkillTally(
                    attempted = state.taskNumber,
                    solved = state.solved
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        errorJob?.cancel()
        resolveJob?.cancel()
        speaker.stop()
        voiceInput.stop()
    }
}

/**
 * Skakač od polazišta do odredišta, najkraćim putem, bez table.
 *
 * Meri **geometriju figure** — skakačev skok je jedini koji se ne vidi po liniji
 * nego se mora znati. Računanje ide uz to, jer se put bira među mogućnostima.
 */
internal val KNIGHT_SHORTEST_PATH = TaskSpec(
    id = "shortest_path",
    skills = listOf(Skill.PIECE_GEOMETRY, Skill.CALCULATION),
    supports = listOf(Support.FULL, Support.NONE)
)
