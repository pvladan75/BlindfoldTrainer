package com.program.blindfoldtrainer.feature.minefield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.core.audio.Buzz
import com.program.blindfoldtrainer.core.audio.EyesFreeControls
import com.program.blindfoldtrainer.core.audio.EyesFreeRow
import com.program.blindfoldtrainer.core.audio.EyesFreeZone
import com.program.blindfoldtrainer.core.audio.HELPER_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MAIN_ZONE_WEIGHT
import com.program.blindfoldtrainer.core.audio.MicrophoneZone
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support

/**
 * Skakač kroz minsko polje.
 *
 * Uz punu podršku se vidi tabla sa crnim figurama — zadatak je onda čitanje
 * linija. Bez table se pozicija čuje jednom, pa se dalje drži u glavi; tek tamo
 * kontrola polja postaje ono što u partiji zaista jeste.
 */
@Composable
fun MinefieldScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    support: Support? = null,
    taskId: String? = null,
    viewModel: MinefieldViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty, support, taskId) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    if (isEyesFree) {
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
                    zone = EyesFreeZone(
                        label = "PONOVI",
                        tone = ZoneTone.SECONDARY,
                        onClick = viewModel::onRepeat
                    )
                ),
                EyesFreeRow(
                    weight = HELPER_ZONE_WEIGHT,
                    zone = EyesFreeZone(
                        label = stringResource(R.string.minefield_give_up),
                        fontSize = 16.sp,
                        buzz = Buzz.LONG,
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
        val puzzle = uiState.puzzle

        LinearProgressIndicator(
            progress = { uiState.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )

        Text(
            text = stringResource(R.string.minefield_target, puzzle?.target?.toString().orEmpty()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(
                R.string.minefield_moves,
                uiState.moves,
                puzzle?.optimalMoves ?: 0
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ChessBoard(
            board = puzzle?.board ?: Board.EMPTY,
            tints = buildTints(uiState),
            onSquareClick = viewModel::onSquareClicked,
            modifier = Modifier.fillMaxWidth()
        )

        // Odbijen potez kaže **zašto**: „tu stoji figura" i „to polje je
        // napadnuto" su dve različite greške, a iz druge se uči.
        val message = when {
            uiState.isSolved -> stringResource(R.string.minefield_solved)
            uiState.refusal == Refusal.OCCUPIED -> stringResource(R.string.minefield_captured)
            uiState.refusal == Refusal.ATTACKED -> stringResource(R.string.minefield_attacked)
            uiState.refusal == Refusal.NOT_KNIGHT_MOVE ->
                stringResource(R.string.minefield_not_knight)

            else -> " "
        }

        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = if (uiState.isSolved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.minefield_static),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Boje na tabli: gde je skakač sada, kuda je prošao i gde se ide.
 *
 * Napadnuta polja se **ne boje** — to je baš ono što treba da se zna napamet, a
 * ne da se pročita sa table.
 */
private fun buildTints(uiState: MinefieldUiState): Map<Square, SquareTint> = buildMap {
    val puzzle = uiState.puzzle ?: return@buildMap

    uiState.walked.forEach { put(it, SquareTint.HINT) }
    put(puzzle.target, SquareTint.SUCCESS)
    uiState.current?.let { put(it, SquareTint.HIGHLIGHT) }
}
