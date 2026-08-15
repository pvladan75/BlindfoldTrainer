package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.SessionResult
import java.util.concurrent.TimeUnit

/**
 * Sažetak sesije. Isti je za sve module jer svi prijavljuju [SessionResult] —
 * nema po jedan završni dijalog po modulu kao u staroj aplikaciji.
 */
@Composable
fun SessionSummaryDialog(
    result: SessionResult,
    onRepeat: () -> Unit,
    onBackToMenu: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onBackToMenu,
        title = {
            Text(
                text = stringResource(
                    if (result.isPerfect) R.string.summary_perfect else R.string.summary_title
                ),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryRow(
                    label = stringResource(R.string.summary_solved),
                    value = "${result.solved}/${result.attempted}"
                )
                SummaryRow(
                    label = stringResource(R.string.summary_mistakes),
                    value = result.mistakes.toString()
                )
                SummaryRow(
                    label = stringResource(R.string.summary_accuracy),
                    value = "${(result.accuracy * 100).toInt()}%"
                )
                HorizontalDivider()
                SummaryRow(
                    label = stringResource(R.string.summary_time),
                    value = formatDuration(result.elapsedMillis),
                    emphasised = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onRepeat) { Text(stringResource(R.string.summary_repeat)) }
        },
        dismissButton = {
            TextButton(onClick = onBackToMenu) { Text(stringResource(R.string.summary_menu)) }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, emphasised: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = if (emphasised) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
