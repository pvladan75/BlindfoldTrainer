package com.program.blindfoldtrainer.feature.geometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.SpeechVoice
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val isFinished: Boolean = false,

    /**
     * Polje koje se posle odgovora **pokaže na tabli**, uz punu podršku.
     *
     * Ovo je razlika između testa i vežbe: test kaže da li si pogodio, vežba
     * pokaže istinu. Uz [Support.NONE] table nema pa se ista istina izgovara.
     */
    val revealedSquare: Square? = null
) {
    val progress: Float
        get() = if (questionCount == 0) 0f else questionNumber.toFloat() / questionCount
}

/**
 * Jedina vrsta zadatka koju ovaj modul ume: **koje je boje polje**.
 *
 * Meri koordinatnu automatiku — ne „umeš li da nađeš e4 na tabli", nego „znaš li
 * šta je e4 zatvorenih očiju". Zato dve prečke, i namerno bez one između:
 *
 * - [Support.FULL] — posle odgovora se pokaže tabla sa poljem. To gradi vezu
 *   koordinate i mesta, i to je pravi ulaz za početnika.
 * - [Support.NONE] — table nema, istina se izgovori. Ovo je veština kakva
 *   zaista treba, jer preživi zatvorene oči.
 */
internal val SQUARE_COLOR = TaskSpec(
    id = "square_color",
    skills = listOf(Skill.COORDINATES),
    supports = listOf(Support.FULL, Support.NONE),
    // Deset polja za desetak sekundi je ono čemu se teži; uz punu podršku se
    // računa i pauza dok se tabla pokaže, bez table i izgovor istine.
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 3_000, minAccuracy = 0.9f),
        Support.NONE to Benchmark(millisPerAttempt = 4_500, minAccuracy = 0.9f)
    )
)

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

    private val _support = MutableStateFlow(Support.FULL)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */
    /**
     * Koliko slike aplikacija drži umesto tebe.
     *
     * Zamenilo je prekidač „bez ekrana": on je bio skok sa prve prečke na
     * poslednju, pa je modul ili imao tablu ili je nije imao. Ovde su prečke
     * dve — [Support.FULL] pokaže istinu na tabli, [Support.NONE] je izgovori —
     * a koje postoje kaže sam zadatak.
     */
    val support: StateFlow<Support> = _support.asStateFlow()

    /** Zadržano za ekran: najniža prečka znači da se tabla ne crta. */
    val isEyesFree: StateFlow<Boolean> = _support.map { it == Support.NONE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis: Long = 0
    private var questionJob: Job? = null
    private var isStarted = false

    /** Sesija je prekinuta pre kraja — ne sme da se prijavi kao završena. */
    private var wasQuit = false

    /**
     * Bezbedno je zvati više puta — pokreće sesiju samo prvi put.
     *
     * [requestedSupport] stiže iz porudžbine puta. Kad ga nema — slobodno
     * vežbanje iz menija — prečka se izvodi iz podešavanja: ko vežba zatvorenih
     * očiju kreće od najniže koju zadatak ume.
     */
    fun startOnce(
        difficulty: Difficulty,
        requestedSupport: Support? = null,
        rounds: Int? = null
    ) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty).shortenedTo(rounds)

        viewModelScope.launch {
            // Prvo podešavanje se sačeka: bez toga bi prvo pitanje prošlo pre
            // nego što se sazna koliko podrške ima, pa istina ne bi bila ni
            // pokazana ni izgovorena.
            val settings = settingsRepository.settings.first()
            val wanted = requestedSupport
                ?: if (settings.eyesFree) SQUARE_COLOR.hardest else Support.FULL
            _support.value = SQUARE_COLOR.nearestSupport(wanted)

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
    fun onQuitArmed() = speaker.say { confirmStop }

    /** Prekid sesije bez ekrana — broji se dokle se stiglo, ali ne kao završeno. */
    fun onQuit() {
        if (_uiState.value.isFinished) return
        wasQuit = true
        questionJob?.cancel()
        finish()
    }

    /** Rok po pitanju; bez ekrana se produžava za izgovor. */
    private fun questionLimit(): Long? = setup.perQuestionMillis?.let { limit ->
        if (_support.value == Support.NONE) limit + EYES_FREE_GRACE_MILLIS else limit
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
                remainingMillis = null,
                // Uz punu podršku se istina **pokaže**, i to posle svakog
                // odgovora a ne samo posle greške: veza „e4 je tamno" se gradi
                // i kad se pogodi.
                revealedSquare = if (_support.value == Support.FULL) square else null
            )
        }

        // Uz najnižu prečku table nema, pa ista istina mora da se čuje — inače
        // se pogrešan obrazac samo ponavlja.
        if (_support.value == Support.NONE) speaker.say { spokenFeedback(feedback, square) }

        viewModelScope.launch {
            delay(feedbackPause())
            if (_uiState.value.questionNumber >= setup.questionCount) {
                finish()
            } else {
                nextQuestion()
            }
        }
    }

    private fun SpeechVoice.spokenFeedback(feedback: Feedback, square: Square?): String {
        val color = if (square?.isLight == true) lightSquare else darkSquare
        return when (feedback) {
            Feedback.CORRECT -> correct
            Feedback.WRONG -> wrongSquareIs(color)
            Feedback.TIMEOUT -> timeoutSquareIs(color)
        }
    }

    /**
     * Bez ekrana je pauza duža: ispravka se izgovara, a sledeće pitanje sme da
     * krene tek kad se dočuje — inače bi ga preseklo ili gurnulo u red pa bi sat
     * kretao pre nego što se pitanje uopšte čuje.
     */
    private fun feedbackPause(): Long =
        if (_support.value == Support.NONE) EYES_FREE_FEEDBACK_PAUSE_MILLIS else FEEDBACK_PAUSE_MILLIS

    private fun finish() {
        questionJob?.cancel()
        val state = _uiState.value
        if (_support.value == Support.NONE) {
            // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
            speaker.say(interrupt = false) { sessionEndCorrect(state.solved, state.questionNumber) }
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
                revealedSquare = null,
                remainingMillis = limit
            )
        }

        // Bez ekrana je izgovoreno polje celo pitanje.
        if (_support.value == Support.NONE) speaker.say(square, interrupt = false)

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
            completed = state.isFinished && !wasQuit,
            support = _support.value,
            // Ceo modul meri jednu veštinu, pa je razlaganje kratko — ali ide
            // istim kanalom kao i kod modula koji mešaju više vrsta pitanja.
            taskId = SQUARE_COLOR.id,
            bySkill = mapOf(
                SQUARE_COLOR.measures to SkillTally(
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
        questionJob?.cancel()
        speaker.stop()
    }
}

/**
 * Kraća sesija na zahtev provere. `null` — koliko težina kaže.
 *
 * Skraćuje se **samo broj krugova**, ne i njihova težina: provera koja bi uz to
 * olakšala i sadržaj merila bi nešto drugo nego vežba.
 */
private fun Setup.shortenedTo(rounds: Int?): Setup =
    if (rounds == null || rounds <= 0) this else copy(questionCount = rounds)
