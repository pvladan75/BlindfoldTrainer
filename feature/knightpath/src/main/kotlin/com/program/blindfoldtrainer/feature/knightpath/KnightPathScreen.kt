package com.program.blindfoldtrainer.feature.knightpath

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.designsystem.theme.SquareError
import com.program.blindfoldtrainer.core.designsystem.theme.SquareSuccess
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.SessionResult

@Composable
fun KnightPathScreen(
    difficulty: Difficulty,
    /** Porudžbina puta; bez nje modul bira prečku po podešavanju. */
    support: Support? = null,
    onFinish: (SessionResult) -> Unit,
    /** Koliko krugova; bez porudžbine koliko težina kaže. */
    rounds: Int? = null,
    viewModel: KnightPathViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty, support, rounds) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    if (isEyesFree) {
        // Tabla je i inače prazna — bez ekrana od nje ostaje samo tastatura za
        // polja, a polja se izgovaraju.
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
                            onClick = viewModel::onRepeat
                        ),
                        EyesFreeZone(
                            label = "STANJE",
                            tone = ZoneTone.TERTIARY,
                            buzz = Buzz.MEDIUM,
                            onClick = viewModel::onReadState
                        )
                    )
                ),
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zone = EyesFreeZone(
                        label = "ODUSTANI  (dva dodira)",
                        fontSize = 16.sp,
                        onClick = viewModel::onGiveUp,
                        onArmed = viewModel::onGiveUpArmed
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
        ProgressHeader(uiState)

        TaskPrompt(uiState)

        // Tabla je prazna i služi samo kao tastatura za polja — putanja se prati
        // po zapisu iznad, ne po figurama.
        ChessBoard(
            board = Board.EMPTY,
            tints = buildTints(uiState),
            visibility = PieceVisibility.None,
            modifier = Modifier.weight(1f, fill = false),
            onSquareClick = { viewModel.onSquareClicked(it) }
        )

        PathTrail(uiState)

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = viewModel::onGiveUp,
                enabled = uiState.isAcceptingInput,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("ODUSTANI / POKAŽI PUTANJU")
            }

            // Polje sme i da se izgovori; ide kroz isti put kao i dodir.
            VoiceInputButton(
                state = voiceState,
                onStartListening = viewModel::onVoiceInput,
                onStopListening = viewModel::onVoiceStop,
                enabled = uiState.isAcceptingInput
            )
        }
    }
}

/**
 * Tabla ostaje čista dok se rešava — obojena polja bi rešavala zadatak umesto
 * korisnika. Boje se pale tek kad je zadatak gotov, i posle promašaja pokazuju
 * putanju koju je trebalo naći.
 */
private fun buildTints(uiState: KnightPathUiState): Map<Square, SquareTint> = buildMap {
    when (uiState.feedback) {
        Feedback.SOLVED -> uiState.path.forEach { put(it, SquareTint.SUCCESS) }
        Feedback.FAILED -> uiState.solution.forEach { put(it, SquareTint.HINT) }
        null -> Unit
    }
    uiState.errorSquare?.let { put(it, SquareTint.ERROR) }
}

@Composable
private fun ProgressHeader(uiState: KnightPathUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${uiState.taskNumber}/${uiState.taskCount}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Rešeno: ${uiState.solved}   Greške: ${uiState.mistakes}",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { uiState.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun TaskPrompt(uiState: KnightPathUiState) {
    val start = uiState.start ?: return
    val target = uiState.target ?: return

    val promptColor by animateColorAsState(
        targetValue = when (uiState.feedback) {
            Feedback.SOLVED -> SquareSuccess
            Feedback.FAILED -> SquareError
            null -> MaterialTheme.colorScheme.onBackground
        },
        label = "prompt"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Skakač: $start → $target",
            fontSize = 34.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = promptColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = when (uiState.feedback) {
                Feedback.SOLVED -> "Tačno, u ${uiState.optimalMoves} poteza."
                Feedback.FAILED -> "Nije uspelo. Najkraće ide ovako:"
                null -> "Preostalo poteza: ${uiState.movesLeft} od ${uiState.optimalMoves}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

/** Odigrana polja kao zapis — jedini trag koji korisnik ima dok rešava. */
@Composable
private fun PathTrail(uiState: KnightPathUiState) {
    val squares = when (uiState.feedback) {
        Feedback.FAILED -> uiState.solution
        else -> uiState.path
    }

    Text(
        text = squares.joinToString("  →  ").ifBlank { " " },
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
