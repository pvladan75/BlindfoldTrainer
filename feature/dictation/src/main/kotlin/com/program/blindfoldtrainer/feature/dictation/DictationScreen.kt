package com.program.blindfoldtrainer.feature.dictation

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
import com.program.blindfoldtrainer.core.model.SessionResult

@Composable
fun DictationScreen(
    difficulty: Difficulty,
    onFinish: (SessionResult) -> Unit,
    viewModel: DictationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(difficulty) { viewModel.startOnce(difficulty) }

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

        // Dok se sluša, table nema na ekranu. Prazna tabla pred očima vodi na
        // prepisivanje — čuješ figuru, spustiš je — a slika u glavi se nikad ne
        // sastavi. Zato se pojavljuje tek kad korisnik kaže da zna gde je šta.
        if (uiState.phase != DictationPhase.LISTENING) {
            ChessBoard(
                board = uiState.visibleBoard,
                tints = buildTints(uiState),
                visibility = PieceVisibility.All,
                onSquareClick = { viewModel.onSquareClicked(it) }
            )

            Palette(uiState = uiState, onPieceClick = viewModel::onPaletteClicked)
        }

        Spacer(Modifier.weight(1f))

        Controls(
            uiState = uiState,
            onReplay = viewModel::onReplay,
            onReady = viewModel::onReady,
            onCheck = viewModel::onCheck
        )
    }
}

/** Boje se pale samo u pregledu — dok se slaže, tabla ne sme ništa da oda. */
private fun buildTints(uiState: DictationUiState): Map<Square, SquareTint> = buildMap {
    val grade = uiState.grade ?: return@buildMap
    if (uiState.phase != DictationPhase.REVIEW) return@buildMap

    grade.correct.forEach { put(it, SquareTint.SUCCESS) }
    grade.missed.forEach { put(it, SquareTint.HINT) }
    grade.wrong.forEach { put(it, SquareTint.ERROR) }
}

@Composable
private fun ProgressHeader(uiState: DictationUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${uiState.taskNumber}/${uiState.taskCount}",
                style = MaterialTheme.typography.labelMedium
            )
            // Broj čitanja stoji uz ostalo namerno: to je merilo napretka u ovom
            // modulu, a merilo koje se ne vidi ne meri ništa.
            Text(
                text = "Rešeno: ${uiState.solved}   Greške: ${uiState.mistakes}   " +
                    "Čitanja: ${uiState.replays}",
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
private fun PhasePrompt(uiState: DictationUiState) {
    val grade = uiState.grade

    val text = when (uiState.phase) {
        DictationPhase.LISTENING -> "Slušaj poziciju"
        DictationPhase.PLACING -> "Postavi ono što si čuo"
        DictationPhase.REVIEW -> when {
            grade == null -> ""
            grade.isPerfect -> "Sve tačno!"
            else -> "Tačno ${grade.correct.size} od ${grade.correct.size + grade.missed.size}"
        }
    }

    val color = when {
        uiState.phase != DictationPhase.REVIEW -> MaterialTheme.colorScheme.onBackground
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
 * Figure koje čekaju da budu postavljene. Prazan red se zadržava i kad je paleta
 * prazna, da tabla ne poskakuje između faza.
 */
@Composable
private fun Palette(uiState: DictationUiState, onPieceClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.phase != DictationPhase.PLACING) return@Row

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

/**
 * „Čitaj ponovo" je glavno dugme, ne pomoćno: pozicija se ovde **samo** čuje, pa
 * je ponovno čitanje jedini put do zadatka — ne pomoć nego alat.
 */
@Composable
private fun Controls(
    uiState: DictationUiState,
    onReplay: () -> Unit,
    onReady: () -> Unit,
    onCheck: () -> Unit
) {
    when (uiState.phase) {
        DictationPhase.LISTENING -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // `weight` postoji samo unutar reda, pa modifikator mora ovde.
            val buttonModifier = Modifier.weight(1f).height(52.dp)

            OutlinedButton(onClick = onReplay, modifier = buttonModifier) {
                Text("ČITAJ PONOVO")
            }

            // Prekida čitanje i otvara tablu — namerno je ovo glavno dugme,
            // jer je odluka „znam gde je šta" ceo prelaz u drugu polovinu vežbe.
            Button(onClick = onReady, modifier = buttonModifier) {
                Text("ZNAM GDE JE ŠTA", fontWeight = FontWeight.Bold)
            }
        }

        DictationPhase.PLACING -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val buttonModifier = Modifier.weight(1f).height(52.dp)

            Button(onClick = onReplay, modifier = buttonModifier) {
                Text("ČITAJ PONOVO", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(onClick = onCheck, modifier = buttonModifier) {
                Text("PROVERI")
            }
        }

        // Sledeći zadatak stiže sam; dugme bi samo mamilo na dodir.
        DictationPhase.REVIEW -> Box(Modifier.fillMaxWidth().height(52.dp))
    }
}
