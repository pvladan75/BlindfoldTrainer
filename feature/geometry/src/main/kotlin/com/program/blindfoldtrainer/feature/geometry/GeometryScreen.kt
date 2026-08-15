package com.program.blindfoldtrainer.feature.geometry

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.core.designsystem.theme.BoardDark
import com.program.blindfoldtrainer.core.designsystem.theme.BoardLight
import com.program.blindfoldtrainer.core.designsystem.theme.SquareError
import com.program.blindfoldtrainer.core.designsystem.theme.SquareSuccess
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult

@Composable
fun GeometryScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    viewModel: GeometryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressHeader(uiState)

        Spacer(Modifier.height(32.dp))

        QuestionPrompt(uiState)

        Spacer(Modifier.height(28.dp))

        AnswerButtons(
            enabled = uiState.feedback == null && !uiState.isFinished,
            onAnswer = viewModel::onAnswer
        )

        // Prazan prostor ide ispod dugmadi, da odgovori stoje odmah uz pitanje
        // umesto da budu zalepljeni za dno ekrana.
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ProgressHeader(uiState: GeometryUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${uiState.questionNumber}/${uiState.questionCount}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Tačno: ${uiState.solved}   Greške: ${uiState.mistakes}",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(8.dp))

        // Kad težina ima sat, traka pokazuje preostalo vreme za pitanje;
        // kad nema, pokazuje napredak kroz sesiju.
        val limit = uiState.questionLimitMillis
        val remaining = uiState.remainingMillis
        val fraction = if (limit != null && remaining != null) {
            remaining.toFloat() / limit
        } else {
            uiState.progress
        }

        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun QuestionPrompt(uiState: GeometryUiState) {
    val square = uiState.square ?: return

    val promptColor by animateColorAsState(
        targetValue = when (uiState.feedback) {
            Feedback.CORRECT -> SquareSuccess
            Feedback.WRONG, Feedback.TIMEOUT -> SquareError
            null -> MaterialTheme.colorScheme.onBackground
        },
        label = "prompt"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Koje je boje polje?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = square.toString(),
            fontSize = 88.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = promptColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Posle greške se odmah kaže tačan odgovor — bez toga se pogrešan
        // obrazac samo ponavlja.
        val correction = when (uiState.feedback) {
            Feedback.WRONG, Feedback.TIMEOUT ->
                if (square.isLight) "svetlo" else "tamno"
            else -> null
        }
        Text(
            text = correction ?: " ",
            style = MaterialTheme.typography.titleMedium,
            color = SquareError
        )
    }
}

@Composable
private fun AnswerButtons(enabled: Boolean, onAnswer: (Answer) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { onAnswer(Answer.LIGHT) },
            enabled = enabled,
            modifier = Modifier.weight(1f).height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BoardLight,
                contentColor = Color.Black
            )
        ) {
            Text("SVETLO", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        Button(
            onClick = { onAnswer(Answer.DARK) },
            enabled = enabled,
            modifier = Modifier.weight(1f).height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BoardDark,
                contentColor = Color.White
            )
        ) {
            Text("TAMNO", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
