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
import com.program.blindfoldtrainer.core.progress.Recommendation
import com.program.blindfoldtrainer.core.progress.SessionReward
import java.util.concurrent.TimeUnit

/**
 * Sažetak sesije. Isti je za sve module jer svi prijavljuju [SessionResult] —
 * nema po jedan završni dijalog po modulu kao u staroj aplikaciji.
 */
@Composable
fun SessionSummaryDialog(
    result: SessionResult,
    reward: SessionReward?,
    /**
     * Šta se ovom sesijom pomerilo — nivo veštine i orijentir. `null` dok se
     * napredak ne upiše, ili kad sesija ne nosi zadatak koji se zna.
     */
    shift: SessionShift?,
    /** Šta put predlaže posle ove sesije, ako išta. */
    next: Recommendation?,
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

                // Šta je ova sesija pomerila. Stiže u trenutku kad je zasluženo
                // i bez otvaranja ijednog ekrana — zato je ovo najvažnije mesto
                // na kom se profil vidi, važnije od samog ekrana napretka.
                if (result.bySkill.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.summary_skills),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    result.bySkill.forEach { (skill, tally) ->
                        SummaryRow(
                            label = stringResource(skill.labelRes()),
                            value = "${tally.solved}/${tally.attempted}"
                        )
                    }
                }

                // **Dokle si stigao**, ne samo kako je prošlo: nivo veštine posle
                // ove sesije (i od čega, ako se promenio), pa orijentir ove
                // prečke naspram onoga što si upravo uradio. Ovo je mesto gde se
                // profil vidi bez otvaranja ijednog ekrana.
                shift?.let { ShiftLines(it) }

                // I šta je sledeće — da se sa sažetka zna kuda, a ne tek sa
                // kartice. Provera ne predlaže: ona meri, put bira posle.
                if (next != null && !result.isCheckup) {
                    Text(
                        text = stringResource(
                            R.string.summary_next,
                            stringResource(taskLabelRes(next.taskId)),
                            stringResource(next.support.labelRes())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Poeni stižu tek pošto se sesija upiše, pa ih nema u prvom
                // kadru — red se zato pojavljuje kad nagrada stigne.
                reward?.let {
                    SummaryRow(
                        label = stringResource(R.string.summary_xp),
                        value = "+${it.xp}",
                        emphasised = true
                    )
                    if (it.isRankUp) {
                        Text(
                            text = stringResource(
                                R.string.summary_rank_up,
                                stringResource(it.rankAfter.labelRes())
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    it.newAchievements.forEach { achievement ->
                        Text(
                            text = stringResource(
                                R.string.summary_achievement,
                                stringResource(achievement.labelRes())
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
private fun ShiftLines(shift: SessionShift) {
    val skill = stringResource(shift.skill.labelRes())
    Text(
        text = if (shift.levelChanged) {
            stringResource(
                R.string.summary_level_changed,
                skill,
                shift.after.headline(),
                shift.before.headline()
            )
        } else {
            stringResource(R.string.summary_level, skill, shift.after.headline())
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (shift.levelChanged) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )

    val benchmark = shift.benchmark
    val perAttempt = shift.millisPerAttempt
    if (benchmark != null && perAttempt != null) {
        Text(
            text = stringResource(
                if (shift.metBenchmark) {
                    R.string.summary_benchmark_met
                } else {
                    R.string.summary_benchmark_missed
                },
                seconds(benchmark.millisPerAttempt),
                (benchmark.minAccuracy * 100).toInt(),
                seconds(perAttempt),
                (shift.accuracy * 100).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (shift.metBenchmark) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Sekunde sa jednom decimalom i tačkom: „2.5". */
private fun seconds(millis: Long): String = "%.1f".format(java.util.Locale.ROOT, millis / 1000f)

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
