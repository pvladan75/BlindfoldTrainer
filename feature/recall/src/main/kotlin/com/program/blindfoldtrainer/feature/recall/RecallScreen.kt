package com.program.blindfoldtrainer.feature.recall

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.ChessBoard
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.designsystem.board.SquareTint
import com.program.blindfoldtrainer.core.designsystem.board.drawableRes
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.SessionResult

@Composable
fun RecallScreen(
    difficulty: Difficulty,
    /** Porudžbina puta; bez nje modul bira prečku po podešavanju. */
    support: Support? = null,
    onFinish: (SessionResult) -> Unit,
    viewModel: RecallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty, support) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinish(viewModel.buildResult())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProgressHeader(uiState)

        PhasePrompt(uiState)

        ChessBoard(
            board = uiState.visibleBoard,
            tints = buildTints(uiState),
            visibility = PieceVisibility.All,
            onSquareClick = { viewModel.onSquareClicked(it) }
        )

        Palette(uiState = uiState, onPieceClick = viewModel::onPaletteClicked)

        Spacer(Modifier.weight(1f))

        PhaseButton(
            uiState = uiState,
            onReady = viewModel::onReadyToPlace,
            onGiveUp = viewModel::onGiveUp
        )
    }
}

/** Boje se pale samo u pregledu — dok se slaže, tabla ne sme ništa da oda. */
private fun buildTints(uiState: RecallUiState): Map<Square, SquareTint> = buildMap {
    val grade = uiState.grade ?: return@buildMap
    if (uiState.phase != RecallPhase.REVIEW) return@buildMap

    grade.correct.forEach { put(it, SquareTint.SUCCESS) }
    grade.missed.forEach { put(it, SquareTint.HINT) }
    grade.wrong.forEach { put(it, SquareTint.ERROR) }
}

@Composable
private fun ProgressHeader(uiState: RecallUiState) {
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

        // Dok se pamti, traka pokazuje preostalo vreme; posle toga napredak
        // kroz sesiju — traka koja stoji na nuli ne govori ništa.
        val fraction = if (uiState.phase == RecallPhase.MEMORIZE) {
            uiState.memorizeFraction
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
private fun PhasePrompt(uiState: RecallUiState) {
    val grade = uiState.grade

    val text = when (uiState.phase) {
        RecallPhase.MEMORIZE -> "Zapamti poziciju"
        RecallPhase.PLACING -> "Vrati figure na svoja polja"
        RecallPhase.REVIEW -> when {
            grade == null -> ""
            grade.isPerfect -> "Sve tačno!"
            else -> "Tačno ${grade.correct.size} od ${grade.correct.size + grade.missed.size}"
        }
    }

    val color = when {
        uiState.phase != RecallPhase.REVIEW -> MaterialTheme.colorScheme.onBackground
        grade?.isPerfect == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Figure koje čekaju da budu vraćene. Prazan red se zadržava i kad je paleta
 * prazna, da tabla ne poskakuje između faza.
 */
@Composable
private fun Palette(uiState: RecallUiState, onPieceClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.phase != RecallPhase.PLACING) return@Row

        uiState.palette.forEachIndexed { index, piece ->
            val isSelected = uiState.selectedIndex == index

            Surface(
                onClick = { onPieceClick(index) },
                modifier = Modifier.size(52.dp).padding(2.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(piece.drawableRes()),
                        contentDescription = piece.toString(),
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseButton(
    uiState: RecallUiState,
    onReady: () -> Unit,
    onGiveUp: () -> Unit
) {
    val modifier = Modifier.fillMaxWidth().height(52.dp)

    when (uiState.phase) {
        RecallPhase.MEMORIZE ->
            Button(onClick = onReady, modifier = modifier) {
                Text("ZAPAMTIO SAM", fontWeight = FontWeight.Bold)
            }

        RecallPhase.PLACING ->
            OutlinedButton(onClick = onGiveUp, modifier = modifier) {
                Text("PROVERI / ODUSTANI")
            }

        // Sledeći zadatak stiže sam; dugme bi samo mamilo na dodir.
        RecallPhase.REVIEW -> Box(modifier)
    }
}
