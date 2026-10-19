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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot

/**
 * Kartica **Ja** — treća od tri: ko vežba, koliko je uložio, kako je podešeno.
 *
 * Sve ovo je stajalo na početnom ekranu: rang u kartici, a profil, uputstvo i
 * podešavanja kao četiri ikone u traci. Ekran na kom se bira vežba time je
 * nosio i ono što se menja jednom mesečno. Sad je to ovde, a Vežbaj je ostao
 * samo za „šta sad".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    progress: ProgressSnapshot,
    profileName: String?,
    onOpenProfiles: () -> Unit,
    onOpenPersistence: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_you), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "profile") {
                LinkRow(
                    icon = Icons.Default.Person,
                    title = profileName ?: stringResource(R.string.app_name),
                    hint = stringResource(R.string.you_profile_hint),
                    onClick = onOpenProfiles
                )
            }

            item(key = "rank") {
                RankCard(progress = progress, onOpenPersistence = onOpenPersistence)
            }

            item(key = "settings") {
                LinkRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_title),
                    hint = stringResource(R.string.you_settings_hint),
                    onClick = onOpenSettings
                )
            }

            item(key = "guide") {
                LinkRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(R.string.guide_title),
                    hint = stringResource(R.string.you_guide_hint),
                    onClick = onOpenGuide
                )
            }

            item(key = "voice") {
                LinkRow(
                    icon = Icons.Default.MicNone,
                    title = stringResource(R.string.voice_title),
                    hint = stringResource(R.string.voice_notice),
                    onClick = onOpenSettings
                )
            }
        }
    }
}

/**
 * **Istrajnost** — rang i poeni, ali imenovani kao ono što jesu.
 *
 * Poeni mere koliko si radio, ne koliko znaš; zato uz rang stoje **sesije i
 * vreme**, po čemu se odmah vidi da je reč o uloženom, i kartica vodi na svoj
 * ekran, a ne na Napredak. Nivo po veštini je na kartici Napredak i zove se
 * drugačije — dva broja koja izgledaju kao ista stvar su najskuplja greška u
 * ovom projektu.
 */
@Composable
private fun RankCard(progress: ProgressSnapshot, onOpenPersistence: () -> Unit) {
    val rankProgress = progress.rankProgress

    Card(
        onClick = onOpenPersistence,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(rankProgress.current.labelRes()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.menu_xp, progress.totalXp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { rankProgress.fraction },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            val next = rankProgress.next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (next == null) {
                        stringResource(R.string.menu_rank_top)
                    } else {
                        stringResource(
                            R.string.menu_rank_next,
                            rankProgress.xpNeededForNext - rankProgress.xpIntoRank,
                            stringResource(next.labelRes())
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.menu_rank_effort,
                        progress.sessions,
                        practiceTimeLabel(progress.timeMillis)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Vreme vežbanja, u satima i minutima; ispod sata se sati ne pominju. */
@Composable
private fun practiceTimeLabel(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) {
        stringResource(R.string.persistence_minutes, minutes)
    } else {
        stringResource(R.string.persistence_hours, minutes / 60, minutes % 60)
    }
}
