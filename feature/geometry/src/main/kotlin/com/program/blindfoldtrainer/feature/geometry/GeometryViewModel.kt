package com.program.blindfoldtrainer.feature.geometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
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

enum class Answer { LIGHT, DARK }

enum class Feedback { CORRECT, WRONG, TIMEOUT }

data class GeometryUiState(
    val square: Square? = null,
    val questionNumber: Int = 0,
    val questionCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val feedback: Feedback? = null,
    /** `null` kad težina nema vremensko ograničenje. */
    val remainingMillis: Long? = null,
    val questionLimitMillis: Long? = null,
    val isFinished: Boolean = false
) {
    val progress: Float
        get() = if (questionCount == 0) 0f else questionNumber.toFloat() / questionCount
}

/**
 * Podešavanja po težini. Lako nema sat i služi da se nauči obrazac; teško je
 * dovoljno brzo da se ne stigne brojati polja u glavi.
 */
private data class Setup(val questionCount: Int, val perQuestionMillis: Long?)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(questionCount = 10, perQuestionMillis = null)
    Difficulty.MEDIUM -> Setup(questionCount = 15, perQuestionMillis = 6_000)
    Difficulty.HARD -> Setup(questionCount = 20, perQuestionMillis = 3_500)
}

private const val FEEDBACK_PAUSE_MILLIS = 600L
private const val TICK_MILLIS = 100L

/**
 * Koliko se sat produžava kad se pitanje i izgovara.
 *
 * Bez ekrana polje ne stigne odjednom nego se izgovori, a na teškom je rok
 * 3,5 s — pola bi otišlo na slušanje. Dodatak plaća čitanje, ne razmišljanje.
 */
private const val EYES_FREE_GRACE_MILLIS = 1_500L
private const val EYES_FREE_FEEDBACK_PAUSE_MILLIS = 1_600L

@HiltViewModel
class GeometryViewModel @Inject constructor(
    private val speaker: Speaker,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeometryUiState())
    val uiState: StateFlow<GeometryUiState> = _uiState.asStateFlow()

    private val _isEyesFree = MutableStateFlow(Settings.DEFAULT.eyesFree)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */
    val isEyesFree: StateFlow<Boolean> = _isEyesFree.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis: Long = 0
    private var questionJob: Job? = null
    private var isStarted = false

    /** Sesija je prekinuta pre kraja — ne sme da se prijavi kao završena. */
    private var wasQuit = false

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)

        viewModelScope.launch {
            // Prvo podešavanje se sačeka: bez toga bi prvo pitanje prošlo pre
            // nego što se sazna da se vežba bez ekrana, pa ne bi bilo izgovoreno.
            _isEyesFree.value = settingsRepository.settings.first().eyesFree

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = GeometryUiState(
                questionCount = setup.questionCount,
                questionLimitMillis = questionLimit()
            )
            nextQuestion()
        }
    }

    /** Ponavlja poslednje izgovoreno — nisi dočuo, a ne da ne znaš odgovor. */
    fun onRepeat() = speaker.repeat()

    /** Prvi dodir na zonu za prekid — traži potvrdu, jer je nepovratno. */
    fun onQuitArmed() = speaker.say("Dodirni ponovo da prekineš.")

    /** Prekid sesije bez ekrana — broji se dokle se stiglo, ali ne kao završeno. */
    fun onQuit() {
        if (_uiState.value.isFinished) return
        wasQuit = true
        questionJob?.cancel()
        finish()
    }

    /** Rok po pitanju; bez ekrana se produžava za izgovor. */
    private fun questionLimit(): Long? = setup.perQuestionMillis?.let { limit ->
        if (_isEyesFree.value) limit + EYES_FREE_GRACE_MILLIS else limit
    }

    fun onAnswer(answer: Answer) {
        val state = _uiState.value
        val square = state.square ?: return
        if (state.feedback != null || state.isFinished) return

        val expected = if (square.isLight) Answer.LIGHT else Answer.DARK
        if (answer == expected) {
            resolve(Feedback.CORRECT)
        } else {
            resolve(Feedback.WRONG)
        }
    }

    private fun resolve(feedback: Feedback) {
        questionJob?.cancel()
        val square = _uiState.value.square
        _uiState.update {
            it.copy(
                feedback = feedback,
                solved = it.solved + if (feedback == Feedback.CORRECT) 1 else 0,
                mistakes = it.mistakes + if (feedback == Feedback.CORRECT) 0 else 1,
                remainingMillis = null
            )
        }

        // Bez ekrana se ispisana ispravka ne vidi, pa mora da se čuje — inače se
        // pogrešan obrazac samo ponavlja.
        if (_isEyesFree.value) speaker.say(spokenFeedback(feedback, square))

        viewModelScope.launch {
            delay(feedbackPause())
            if (_uiState.value.questionNumber >= setup.questionCount) {
                finish()
            } else {
                nextQuestion()
            }
        }
    }

    private fun spokenFeedback(feedback: Feedback, square: Square?): String {
        val color = if (square?.isLight == true) "svetlo" else "tamno"
        return when (feedback) {
            Feedback.CORRECT -> "Tačno."
            Feedback.WRONG -> "Nije, polje je $color."
            Feedback.TIMEOUT -> "Isteklo je vreme, polje je $color."
        }
    }

    /**
     * Bez ekrana je pauza duža: ispravka se izgovara, a sledeće pitanje sme da
     * krene tek kad se dočuje — inače bi ga preseklo ili gurnulo u red pa bi sat
     * kretao pre nego što se pitanje uopšte čuje.
     */
    private fun feedbackPause(): Long =
        if (_isEyesFree.value) EYES_FREE_FEEDBACK_PAUSE_MILLIS else FEEDBACK_PAUSE_MILLIS

    private fun finish() {
        questionJob?.cancel()
        val state = _uiState.value
        if (_isEyesFree.value) {
            // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
            speaker.say(
                "Kraj sesije. Tačno ${state.solved} od ${state.questionNumber}.",
                interrupt = false
            )
        }
        _uiState.update { it.copy(isFinished = true, feedback = null) }
    }

    private fun nextQuestion() {
        val square = Square(Random.nextInt(64))
        val limit = questionLimit()

        _uiState.update {
            it.copy(
                square = square,
                questionNumber = it.questionNumber + 1,
                feedback = null,
                remainingMillis = limit
            )
        }

        // Bez ekrana je izgovoreno polje celo pitanje.
        if (_isEyesFree.value) speaker.say(square, interrupt = false)

        if (limit == null) return
        questionJob?.cancel()
        questionJob = viewModelScope.launch {
            var remaining = limit
            while (remaining > 0) {
                delay(TICK_MILLIS)
                remaining -= TICK_MILLIS
                _uiState.update { it.copy(remainingMillis = remaining.coerceAtLeast(0)) }
            }
            resolve(Feedback.TIMEOUT)
        }
    }

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.GEOMETRY,
            difficulty = difficulty,
            // Ako je korisnik prekinuo, broji se samo dokle je stigao —
            // ne cela sesija koju nije odradio.
            attempted = state.questionNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished && !wasQuit
        )
    }

    override fun onCleared() {
        super.onCleared()
        questionJob?.cancel()
        speaker.stop()
    }
}
