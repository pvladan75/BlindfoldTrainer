package com.program.blindfoldtrainer.feature.geometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class GeometryViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(GeometryUiState())
    val uiState: StateFlow<GeometryUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis: Long = 0
    private var questionJob: Job? = null
    private var isStarted = false

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)
        startedAtMillis = System.currentTimeMillis()
        _uiState.value = GeometryUiState(
            questionCount = setup.questionCount,
            questionLimitMillis = setup.perQuestionMillis
        )
        nextQuestion()
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
        _uiState.update {
            it.copy(
                feedback = feedback,
                solved = it.solved + if (feedback == Feedback.CORRECT) 1 else 0,
                mistakes = it.mistakes + if (feedback == Feedback.CORRECT) 0 else 1,
                remainingMillis = null
            )
        }
        viewModelScope.launch {
            delay(FEEDBACK_PAUSE_MILLIS)
            if (_uiState.value.questionNumber >= setup.questionCount) {
                _uiState.update { it.copy(isFinished = true, feedback = null) }
            } else {
                nextQuestion()
            }
        }
    }

    private fun nextQuestion() {
        val square = Square(Random.nextInt(64))
        _uiState.update {
            it.copy(
                square = square,
                questionNumber = it.questionNumber + 1,
                feedback = null,
                remainingMillis = setup.perQuestionMillis
            )
        }

        val limit = setup.perQuestionMillis ?: return
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
            completed = state.isFinished
        )
    }

    override fun onCleared() {
        super.onCleared()
        questionJob?.cancel()
    }
}
