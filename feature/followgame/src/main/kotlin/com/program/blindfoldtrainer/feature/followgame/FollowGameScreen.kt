package com.program.blindfoldtrainer.feature.followgame

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MAIN_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MicrophoneZone
import com.program.blindfoldtrainer.core.audio.VoiceInputButton
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.SessionResult

@Composable
fun FollowGameScreen(
    difficulty: Difficulty,
    /** Porudžbina puta; bez nje modul bira prečku po podešavanju. */
    support: Support? = null,
    /** Koji zadatak se radi; bez porudžbine se radi zatečeni. */
    taskId: String? = null,
    onFinish: (SessionResult) -> Unit,
    viewModel: FollowGameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty, support, taskId) }

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
        EyesFreeControls(
            // Gornja zona je uvek ono što se traži sad: dok partija teče to je
            // sledeći potez, a čim stigne pitanje — mikrofon. Meta je ista, pa
            // se prst ne premešta.
            microphone = MicrophoneZone(
                isListening = voiceState == VoiceState.Listening,
                voiceState = voiceState,
                onToggle = {
                    if (voiceState == VoiceState.Listening) viewModel.onVoiceStop()
                    else viewModel.onVoiceInput()
                }
            ).takeIf { uiState.phase == FollowPhase.QUESTION },
            rows = buildList {
                if (uiState.phase != FollowPhase.QUESTION) {
                    add(
                        EyesFreeRow(
                            weight = MAIN_ZONE_WEIGHT,
                            zone = EyesFreeZone(
                                label = "SLEDEĆI POTEZ",
                                tone = ZoneTone.PRIMARY,
                                fontSize = 26.sp,
                                onClick = viewModel::onNextMove
                            )
                        )
                    )
                }
                add(
                    EyesFreeRow(
                        weight = HELPER_ZONE_WEIGHT,
                        zone = EyesFreeZone(
                            label = "PONOVI",
                            tone = ZoneTone.SECONDARY,
                            onClick = viewModel::onRepeat
                        )
                    )
                )
                add(
                    EyesFreeRow(
                        weight = HELPER_ZONE_WEIGHT,
                        zone = EyesFreeZone(
                            label = "PREKINI  (dva dodira)",
                            fontSize = 16.sp,
                            onClick = viewModel::onQuit,
                            onArmed = viewModel::onQuitArmed
                        )
                    )
                )
            },
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProgressHeader(uiState)

        GameCaption(uiState)

        MovePanel(uiState)

        // Tabla je prazna — partija se drži u glavi, a dodiruje se samo kad
        // stigne pitanje.
        ChessBoard(
            board = Board.EMPTY,
            tints = buildTints(uiState),
            visibility = PieceVisibility.None,
            onSquareClick = { viewModel.onSquareClicked(it) }
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = viewModel::onNextMove,
                enabled = uiState.phase == FollowPhase.FOLLOWING && !uiState.isFinished,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("SLEDEĆI POTEZ", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            // Odgovor sme i da se izgovori — mikrofon ima smisla samo dok pitanje stoji.
            VoiceInputButton(
                state = voiceState,
                onStartListening = viewModel::onVoiceInput,
                onStopListening = viewModel::onVoiceStop,
                enabled = uiState.phase == FollowPhase.QUESTION
            )
        }
    }
}

/** Boje se pale samo uz odgovor: zeleno tačno polje, crveno promašeno. */
private fun buildTints(uiState: FollowGameUiState): Map<Square, SquareTint> = buildMap {
    if (uiState.phase != FollowPhase.FEEDBACK) return@buildMap

    uiState.question?.expected?.forEach { put(it, SquareTint.SUCCESS) }
    uiState.answerSquare?.takeIf { !uiState.wasCorrect }?.let { put(it, SquareTint.ERROR) }
}

@Composable
private fun ProgressHeader(uiState: FollowGameUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pitanje ${uiState.questionNumber}/${uiState.questionCount}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Tačno: ${uiState.solved}   Greške: ${uiState.mistakes}",
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
private fun GameCaption(uiState: FollowGameUiState) {
    Text(
        text = "${uiState.white} — ${uiState.black}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Poslednji potez, ili pitanje kad partija stane. Oba stoje na istom mestu da
 * pogled ne skače između dva reda teksta.
 */
@Composable
private fun MovePanel(uiState: FollowGameUiState) {
    val question = uiState.question

    val text = when (uiState.phase) {
        FollowPhase.FOLLOWING -> uiState.lastMoveLabel.ifBlank { "Pritisni za prvi potez" }
        FollowPhase.QUESTION -> {
            // Kod pitanja sa više odgovora se vidi i dokle se stiglo: bez toga
            // se ne zna da li je dodir uopšte primljen.
            val found = uiState.found.size
            val wanted = question?.expected?.size ?: 0
            if (wanted > 1 && found > 0) {
                "${question?.prompt.orEmpty()}  ($found/$wanted)"
            } else {
                question?.prompt.orEmpty()
            }
        }

        FollowPhase.FEEDBACK -> if (uiState.wasCorrect) {
            "Tačno"
        } else {
            question?.correction.orEmpty()
        }
    }

    val color = when {
        uiState.phase != FollowPhase.FEEDBACK -> MaterialTheme.colorScheme.onBackground
        uiState.wasCorrect -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        fontSize = if (uiState.phase == FollowPhase.FOLLOWING) 32.sp else 20.sp,
        fontFamily = if (uiState.phase == FollowPhase.FOLLOWING) FontFamily.Monospace else FontFamily.Default,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(76.dp)
    )
}
