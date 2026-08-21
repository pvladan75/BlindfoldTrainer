package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.progress.Achievement
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot

/**
 * **Istrajnost** — koliko si uložio, ne dokle si stigao.
 *
 * Poeni i rang su dugo stajali na početnom ekranu i **čitali se kao nivo**, jer
 * je „Amater" reč koja zvuči kao ocena znanja. Nisu: mere koliko si radio. Nivo
 * je otišao u Napredak i tamo se zove svojim imenom — prečka koju veština drži.
 *
 * Ova razlika nije kozmetička. Ona je razlog zbog kog se takmičenje, kad dođe
 * server, sme vezati **samo za istrajnost**: ko nizove dana i minute „lažira",
 * samo je vežbao. Lestvica po veštinama bi napala sam instrument, jer bi merilo
 * postalo nagrada — a merilo koje nagrađuje prestaje da meri.
 *
 * Ovde stoji i spisak dostignuća, koji dotle nije imao svoj ekran nego samo
 * brojač u meniju.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistenceScreen(progress: ProgressSnapshot, onBack: () -> Unit) {
    val rankProgress = progress.rankProgress

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.persistence_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.persistence_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item(key = "rank") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(rankProgress.current.labelRes()),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { rankProgress.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = rankProgress.next?.let {
                                stringResource(
                                    R.string.menu_rank_next,
                                    rankProgress.xpNeededForNext - rankProgress.xpIntoRank,
                                    stringResource(it.labelRes())
                                )
                            } ?: stringResource(R.string.menu_rank_top),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item(key = "totals") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Total(R.string.persistence_sessions, "${progress.sessions}")
                        Total(R.string.persistence_time, hoursLabel(progress.timeMillis))
                        Total(R.string.persistence_solved, "${progress.solved}")
                        Total(R.string.persistence_perfect, "${progress.perfectSessions}")
                        Total(
                            R.string.persistence_streak,
                            "${progress.perfectStreak} (${progress.bestPerfectStreak})"
                        )
                    }
                }
            }

            item(key = "achievements_title") {
                Text(
                    text = stringResource(
                        R.string.persistence_achievements,
                        progress.achievements.size,
                        Achievement.entries.size
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Neosvojena se **pokazuju**, ne kriju: spisak na kom se vidi šta još
            // stoji je putokaz, a spisak koji raste iz ničega je iznenađenje.
            items(Achievement.entries, key = { it.name }) { achievement ->
                val earned = achievement in progress.achievements
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (earned) "✓" else "·",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (earned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = stringResource(achievement.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (earned) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Total(labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Vreme u satima i minutima.
 *
 * Sekunde se ne pokazuju: ukupno vreme vežbanja je podatak o istrajnosti, a tamo
 * pola minuta ne znači ništa. Ispod sata se sati i ne pominju.
 */
@Composable
private fun hoursLabel(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) {
        stringResource(R.string.persistence_minutes, minutes)
    } else {
        stringResource(R.string.persistence_hours, minutes / 60, minutes % 60)
    }
}
