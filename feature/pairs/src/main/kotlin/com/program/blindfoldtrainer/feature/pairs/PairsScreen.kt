package com.program.blindfoldtrainer.feature.pairs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import com.program.blindfoldtrainer.core.audio.VoiceInputButton
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import java.util.concurrent.TimeUnit

@Composable
fun PairsScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    viewModel: PairsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

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
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsPanel(uiState)

            ChessBoard(
                board = uiState.board,
                tints = buildTints(uiState),
                visibility = uiState.visibility,
                onSquareClick = { viewModel.onSquareClicked(it) }
            )

            MoveBanner(uiState)

            Spacer(Modifier.weight(1f))

            Controls(
                uiState = uiState,
                voiceState = voiceState,
                onBegin = viewModel::onBeginPuzzle,
                onRepeat = viewModel::onRepeatMove,
                onReveal = viewModel::onRevealPieces,
                onNext = viewModel::onNextPuzzle,
                onVoiceInput = viewModel::onVoiceInput,
                onVoiceStop = viewModel::onVoiceStop
            )
        }

        if (uiState.phase == PairsPhase.SOLVED) {
            Card(
                modifier = Modifier.align(Alignment.Center),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Zagonetka ${uiState.puzzleNumber} rešena!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(28.dp)
                )
            }
        }
    }
}

private fun buildTints(uiState: PairsUiState): Map<Square, SquareTint> = buildMap {
    uiState.moveHighlight?.let { move ->
        put(move.from, SquareTint.HIGHLIGHT)
        put(move.to, SquareTint.HIGHLIGHT)
    }
    // Povratna informacija se crta preko isticanja poteza.
    uiState.feedbackSquare?.let { square ->
        put(square, if (uiState.feedbackIsCorrect) SquareTint.SUCCESS else SquareTint.ERROR)
    }
}

@Composable
private fun StatsPanel(uiState: PairsUiState) {
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
            Stat("Zadatak", "${uiState.puzzleNumber}/${uiState.puzzleCount}")
            Stat("Potez", "${uiState.stepNumber}/${uiState.stepCount}")
            Stat("Greške", "${uiState.mistakes}")
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

/** Poslednji izgovoreni potez, ispisan za slučaj da se zvuk propusti. */
@Composable
private fun MoveBanner(uiState: PairsUiState) {
    val text = when (uiState.phase) {
        PairsPhase.MEMORIZE -> "Zapamti poziciju"
        PairsPhase.AWAITING_INPUT -> uiState.lastSpokenMove
        PairsPhase.REVEALED -> "Figure su otkrivene"
        PairsPhase.SOLVED -> ""
    }
    Text(
        text = text,
        fontSize = 22.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun Controls(
    uiState: PairsUiState,
    voiceState: VoiceState,
    onBegin: () -> Unit,
    onRepeat: () -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onVoiceInput: () -> Unit,
    onVoiceStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mainModifier = Modifier.weight(1f).height(56.dp)

        when (uiState.phase) {
            PairsPhase.MEMORIZE ->
                Button(onClick = onBegin, modifier = mainModifier) {
                    Text("KREĆEMO", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

            PairsPhase.AWAITING_INPUT ->
                OutlinedButton(onClick = onReveal, modifier = mainModifier) {
                    Text("ODUSTANI / POKAŽI")
                }

            PairsPhase.REVEALED ->
                FilledTonalButton(onClick = onNext, modifier = mainModifier) {
                    Text("SLEDEĆA POZICIJA", fontWeight = FontWeight.Bold)
                }

            PairsPhase.SOLVED -> Box(mainModifier)
        }

        FilledIconButton(
            onClick = onRepeat,
            enabled = uiState.phase == PairsPhase.AWAITING_INPUT,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Ponovi potez")
        }

        // Polje sme i da se izgovori — u modulu u kom potezi stižu glasom, to je
        // najprirodniji način da se odgovori.
        VoiceInputButton(
            state = voiceState,
            onStartListening = onVoiceInput,
            onStopListening = onVoiceStop,
            enabled = uiState.phase == PairsPhase.AWAITING_INPUT
        )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
