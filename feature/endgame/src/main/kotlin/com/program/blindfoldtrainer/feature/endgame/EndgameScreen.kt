package com.program.blindfoldtrainer.feature.endgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import java.util.concurrent.TimeUnit

@Composable
fun EndgameScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    viewModel: EndgameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty) }
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    uiState.infoMessage?.let { message ->
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsPanel(uiState)

        ChessBoard(
            board = uiState.position.board,
            tints = buildTints(uiState),
            visibility = uiState.visibility,
            onSquareClick = { viewModel.onSquareClicked(it) }
        )

        StatusBanner(uiState)

        Spacer(Modifier.weight(1f))

        Controls(
            uiState = uiState,
            onHidePieces = viewModel::onHidePieces,
            onGiveUp = viewModel::onGiveUp,
            onNext = viewModel::onNextPuzzle
        )
    }
}

private fun buildTints(uiState: EndgameUiState): Map<Square, SquareTint> = buildMap {
    uiState.lastMove?.let { move ->
        put(move.from, SquareTint.HIGHLIGHT)
        put(move.to, SquareTint.HIGHLIGHT)
    }
    uiState.selectedSquare?.let { put(it, SquareTint.HINT) }
    uiState.errorSquare?.let { put(it, SquareTint.ERROR) }
}

@Composable
private fun StatsPanel(uiState: EndgameUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Stat("Pozicija", "${uiState.puzzleNumber}/${uiState.puzzleCount}")
            Stat("Cilj", uiState.evaluationLabel.ifBlank { "Mat" })
            Stat("Promašaji", "${uiState.mistakes}")
            Stat("Vreme", formatDuration(uiState.elapsedMillis))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBanner(uiState: EndgameUiState) {
    val color = when (uiState.outcome) {
        EndgameOutcome.MATED -> MaterialTheme.colorScheme.primary
        EndgameOutcome.IN_PROGRESS -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        text = uiState.statusMessage,
        fontSize = 17.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Controls(
    uiState: EndgameUiState,
    onHidePieces: () -> Unit,
    onGiveUp: () -> Unit,
    onNext: () -> Unit
) {
    val buttonModifier = Modifier.fillMaxWidth().height(56.dp)

    when {
        uiState.outcome == EndgameOutcome.GAVE_UP ->
            FilledTonalButton(onClick = onNext, modifier = buttonModifier) {
                Text("SLEDEĆA POZICIJA", fontWeight = FontWeight.Bold)
            }

        uiState.outcome != EndgameOutcome.IN_PROGRESS ->
            // Ishod je rešen; sledeća pozicija stiže sama posle kratke pauze.
            Box(buttonModifier)

        uiState.isMemorizing ->
            Button(onClick = onHidePieces, modifier = buttonModifier) {
                Text("ZAPAMTIO SAM — SAKRIJ", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

        else ->
            OutlinedButton(
                onClick = onGiveUp,
                modifier = buttonModifier,
                enabled = !uiState.isEngineThinking
            ) {
                Text("ODUSTANI / POKAŽI")
            }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
