package com.program.blindfoldtrainer.feature.geometry

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import com.program.blindfoldtrainer.core.audio.Buzz
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MAIN_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.designsystem.theme.BoardDark
import com.program.blindfoldtrainer.core.designsystem.theme.BoardLight
import com.program.blindfoldtrainer.core.designsystem.theme.SquareError
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.designsystem.theme.SquareSuccess
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support

@Composable
fun GeometryScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    support: Support? = null,
    viewModel: GeometryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty, support) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    if (isEyesFree) {
        // Odgovor je jedan od dva, pa su odgovori i glavne zone — mikrofona nema
        // jer se „svetlo" i „tamno" ne moraju izgovarati da bi se pogodili.
        EyesFreeControls(
            rows = listOf(
                EyesFreeRow(
                    weight = MAIN_ZONE_WEIGHT,
                    zones = listOf(
                        EyesFreeZone(
                            label = "SVETLO",
                            tone = ZoneTone.PRIMARY,
                            fontSize = 26.sp,
                            onClick = { viewModel.onAnswer(Answer.LIGHT) }
                        ),
                        EyesFreeZone(
                            label = "TAMNO",
                            tone = ZoneTone.TERTIARY,
                            buzz = Buzz.MEDIUM,
                            fontSize = 26.sp,
                            onClick = { viewModel.onAnswer(Answer.DARK) }
                        )
                    )
                ),
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zone = EyesFreeZone(
                        label = "PONOVI",
                        tone = ZoneTone.SECONDARY,
                        onClick = viewModel::onRepeat
                    )
                ),
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zone = EyesFreeZone(
                        label = "PREKINI  (dva dodira)",
                        fontSize = 16.sp,
                        onClick = viewModel::onQuit,
                        onArmed = viewModel::onQuitArmed
                    )
                )
            ),
            modifier = Modifier.padding(8.dp)
        )
        return
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

        // Vežba, ne test: posle odgovora se **pokaže** gde to polje stoji, i to
        // i kad je odgovor tačan — veza koordinate i mesta se gradi i tada.
        // Mesto je zauzeto i dok table nema, da odgovor ne skakuće po ekranu.
        Box(
            modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            uiState.revealedSquare?.let { revealed ->
                ChessBoard(
                    board = Board.EMPTY,
                    tints = mapOf(revealed to SquareTint.HIGHLIGHT),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
