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
import com.program.blindfoldtrainer.core.audio.Buzz
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MicrophoneZone
import com.program.blindfoldtrainer.core.audio.VoiceInputButton
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.ZoneTone
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
    val voiceState by viewModel.voiceState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()

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

    if (isEyesFree) {
        // Ekran je samo površina za dodir: tabla se ne crta, jer se i ne gleda.
        EyesFreeControls(
            microphone = MicrophoneZone(
                isListening = voiceState == VoiceState.Listening,
                voiceState = voiceState,
                onToggle = {
                    if (voiceState == VoiceState.Listening) viewModel.onVoiceStop()
                    else viewModel.onVoiceInput()
                }
            ),
            rows = listOf(
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zones = listOf(
                        EyesFreeZone(
                            label = "PONOVI",
                            tone = ZoneTone.SECONDARY,
                            onClick = viewModel::onRepeatLast
                        ),
                        EyesFreeZone(
                            label = "POZICIJA",
                            tone = ZoneTone.TERTIARY,
                            buzz = Buzz.MEDIUM,
                            onClick = viewModel::onReadPosition
                        )
                    )
                ),
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zone = EyesFreeZone(
                        label = "ODUSTANI  ·  DUGO: PONIŠTI",
                        fontSize = 16.sp,
                        onClick = viewModel::onGiveUp,
                        onArmed = viewModel::onGiveUpArmed,
                        onLongClick = viewModel::onUndo
                    )
                )
            ),
            modifier = Modifier.padding(8.dp)
        )
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

        ListenControls(
            onRepeatLast = viewModel::onRepeatLast,
            onReadPosition = viewModel::onReadPosition,
            onUndo = viewModel::onUndo
        )

        Controls(
            uiState = uiState,
            voiceState = voiceState,
            onHidePieces = viewModel::onHidePieces,
            onGiveUp = viewModel::onGiveUp,
            onNext = viewModel::onNextPuzzle,
            onVoiceInput = viewModel::onVoiceInput,
            onVoiceStop = viewModel::onVoiceStop
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

/**
 * Slušanje: ponovi poslednje, ili pročitaj celu poziciju.
 *
 * Dva različita posla. „Ponovi" je za ono što nisi dočuo i ne broji se; „Čitaj
 * poziciju" je za kad ti se slika u glavi raspala, i to se broji — ne kao kazna
 * nego kao merilo napretka.
 */
@Composable
private fun ListenControls(
    onRepeatLast: () -> Unit,
    onReadPosition: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onRepeatLast,
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Text("PONOVI")
        }

        OutlinedButton(
            onClick = onReadPosition,
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Text("POZICIJA")
        }

        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Text("PONIŠTI")
        }
    }
}

@Composable
private fun Controls(
    uiState: EndgameUiState,
    voiceState: VoiceState,
    onHidePieces: () -> Unit,
    onGiveUp: () -> Unit,
    onNext: () -> Unit,
    onVoiceInput: () -> Unit,
    onVoiceStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // `weight` postoji samo unutar reda, pa modifikator mora ovde.
        val buttonModifier = Modifier.weight(1f).height(56.dp)

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

        // Potez se izgovara u dva koraka: prvo polazno pa odredišno polje.
        VoiceInputButton(
            state = voiceState,
            onStartListening = onVoiceInput,
            onStopListening = onVoiceStop,
            enabled = uiState.isPlayerTurn
        )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
