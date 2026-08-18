package com.program.blindfoldtrainer.feature.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
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
import com.program.blindfoldtrainer.core.audio.MicrophoneZone
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.ZoneTone
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support

/**
 * Daj šah.
 *
 * Tri prečke, i ovo je prvi zadatak koji koristi **srednju**:
 *
 * - uz punu podršku se tabla vidi sve vreme — zadatak je čitanje linija;
 * - uz srednju se vidi dok ne kažeš da si zapamtio, pa crne figure nestaju;
 * - bez podrške se pozicija samo čuje.
 */
@Composable
fun CheckScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    support: Support? = null,
    taskId: String? = null,
    viewModel: CheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEyesFree by viewModel.isEyesFree.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    // Prečka se čita ovde, a ne unutar `when` niže: uslovan `collectAsState` je
    // uslovan poziv u kompoziciji, i radi samo dok se grane ne promene redom.
    val resolvedSupport by viewModel.support.collectAsState()

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
                        label = stringResource(R.string.check_give_up),
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val puzzle = uiState.puzzle
        val memorizing = uiState.phase == CheckPhase.MEMORIZE

        LinearProgressIndicator(
            progress = { uiState.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )

        Text(
            text = stringResource(
                if (memorizing) R.string.check_memorize else R.string.check_goal
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Modul nosi dva zadatka sa različitim pravilom, a cilj im je isti — pa
        // se iz naslova ne vidi u kom si. Pravilo zato stoji na ekranu: put ume
        // da pošalje na lakši oblik, i to ne sme da izgleda kao da modul
        // popušta.
        puzzle?.let {
            Text(
                text = stringResource(
                    if (it.avoidAttacked) {
                        R.string.check_rule_safe_path
                    } else {
                        R.string.check_rule_no_capture
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = stringResource(R.string.check_moves, uiState.moves, puzzle?.optimalMoves ?: 0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Posle potvrde ostaje vidljiv **samo skakač**: gde si znaš, šta te
        // okružuje moraš da držiš u glavi. To je i cela razlika između čitanja
        // linija i kontrole polja.
        val visibility = when {
            memorizing -> PieceVisibility.All
            resolvedSupport == Support.PARTIAL ->
                PieceVisibility.Only(setOfNotNull(uiState.current))

            else -> PieceVisibility.All
        }

        // Skakač nije deo pozicije: u njoj su samo crne figure, a beli skakač je
        // stanje sesije. Za prikaz se dodaje — obojeno polje ne kaže *šta* na
        // njemu stoji, a na srednjoj prečki bi posle nestanka figura ostala
        // prazna tabla. U samu poziciju ne sme, jer bi zaklonio linije i
        // promenio šta crni drži.
        val shown = puzzle?.board?.let { position ->
            uiState.current?.let { position.withPiece(it, WHITE_KNIGHT) } ?: position
        } ?: Board.EMPTY

        ChessBoard(
            board = shown,
            tints = buildTints(uiState),
            visibility = visibility,
            onSquareClick = viewModel::onSquareClicked,
            modifier = Modifier.fillMaxWidth()
        )

        if (memorizing) {
            FilledTonalButton(onClick = viewModel::onMemorized) {
                Text(stringResource(R.string.check_memorized))
            }
            return@Column
        }

        val message = when {
            uiState.isSolved -> stringResource(R.string.check_done)
            uiState.refusal == Refusal.OCCUPIED -> stringResource(R.string.check_captured)
            uiState.refusal == Refusal.ATTACKED -> stringResource(R.string.check_attacked)
            uiState.refusal == Refusal.NOT_KNIGHT_MOVE -> stringResource(R.string.check_not_knight)
            uiState.refusal == Refusal.ALREADY_THERE ->
                stringResource(R.string.check_already_there)

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
            text = stringResource(R.string.check_static),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Boje na tabli: gde je skakač i kuda je prošao.
 *
 * Ni napadnuta polja ni polja sa kojih se daje šah se **ne boje** — to je baš
 * ono što treba znati, a ne pročitati sa table.
 */
private fun buildTints(uiState: CheckUiState): Map<Square, SquareTint> = buildMap {
    uiState.walked.forEach { put(it, SquareTint.HINT) }
    uiState.current?.let { put(it, SquareTint.HIGHLIGHT) }
}

/** Jedina bela figura u zadatku; stoji ovde da se ne pravi u svakom kadru. */
private val WHITE_KNIGHT = Piece(PieceType.KNIGHT, Color.WHITE)
