package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.audio.ModelState
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.Achievement
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.Rank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    modules: List<TrainingModule>,
    progress: ProgressSnapshot,
    voiceModel: ModelState,
    onDownloadVoiceModel: () -> Unit,
    onCancelVoiceModel: () -> Unit,
    onDeleteVoiceModel: () -> Unit,
    onStart: (TrainingModule, Difficulty) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        if (modules.isEmpty()) {
            // Ne bi smelo da se desi — registar je prazan samo ako nijedan
            // feature modul nije na spisku zavisnosti :app-a.
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.no_modules),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "progress") { RankCard(progress = progress) }

            items(modules, key = { it.id.key }) { module ->
                ModuleCard(module = module, onStart = { onStart(module, it) })
            }

            item(key = "voice") {
                VoiceModelCard(
                    state = voiceModel,
                    onDownload = onDownloadVoiceModel,
                    onCancel = onCancelVoiceModel,
                    onDelete = onDeleteVoiceModel
                )
            }
        }
    }
}

/**
 * Rang i poeni. Stoji na vrhu menija, kao prva stavka liste a ne kao zaseban
 * red iznad nje — inače bi ostao zalepljen dok se spisak modula pomera.
 */
@Composable
private fun RankCard(progress: ProgressSnapshot) {
    val rankProgress = progress.rankProgress

    Card(
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
                        R.string.menu_achievements,
                        progress.achievements.size,
                        Achievement.entries.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Jezički model za glasovni unos.
 *
 * Stoji na dnu menija, ispod modula, jer nije vežba nego oprema. Preuzimanje
 * pokreće korisnik — 39 MB nema ko da nametne onome ko vežba dodirom.
 */
@Composable
private fun VoiceModelCard(
    state: ModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.voice_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (state) {
                    ModelState.Absent -> stringResource(R.string.voice_absent)
                    is ModelState.Downloading -> stringResource(R.string.voice_downloading)
                    ModelState.Unpacking -> stringResource(R.string.voice_unpacking)
                    ModelState.Ready -> stringResource(R.string.voice_ready)
                    is ModelState.Failed -> stringResource(R.string.voice_failed, state.reason)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is ModelState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (state is ModelState.Downloading || state is ModelState.Unpacking) {
                Spacer(Modifier.height(10.dp))
                val fraction = (state as? ModelState.Downloading)?.fraction
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when (state) {
                ModelState.Absent, is ModelState.Failed ->
                    FilledTonalButton(onClick = onDownload) {
                        Text(stringResource(R.string.voice_download))
                    }

                is ModelState.Downloading ->
                    FilledTonalButton(onClick = onCancel) {
                        Text(stringResource(R.string.voice_cancel))
                    }

                // Raspakivanje traje kratko i nema šta da se prekine na pola.
                ModelState.Unpacking -> Unit

                ModelState.Ready ->
                    FilledTonalButton(onClick = onDelete) {
                        Text(stringResource(R.string.voice_delete))
                    }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: TrainingModule, onStart: (Difficulty) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(module.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(module.titleRes),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(module.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                module.difficulties.forEach { difficulty ->
                    FilledTonalButton(
                        onClick = { onStart(difficulty) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(difficulty.labelRes()),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/**
 * Nazivi rangova stoje ovde, a ne u `:core:progress` — taj modul je čist Kotlin
 * i ne zna ni za jezik ni za resurse.
 */
internal fun Rank.labelRes(): Int = when (this) {
    Rank.BEGINNER -> R.string.rank_beginner
    Rank.STUDENT -> R.string.rank_student
    Rank.AMATEUR -> R.string.rank_amateur
    Rank.EXPERIENCED -> R.string.rank_experienced
    Rank.MASTER -> R.string.rank_master
    Rank.GRANDMASTER -> R.string.rank_grandmaster
}

internal fun Achievement.labelRes(): Int = when (this) {
    Achievement.FIRST_SESSION -> R.string.achievement_first_session
    Achievement.FIRST_PERFECT -> R.string.achievement_first_perfect
    Achievement.TEN_PERFECT -> R.string.achievement_ten_perfect
    Achievement.PERFECT_STREAK_FIVE -> R.string.achievement_perfect_streak_five
    Achievement.PERFECT_ON_HARD -> R.string.achievement_perfect_on_hard
    Achievement.SOLVED_HUNDRED -> R.string.achievement_solved_hundred
    Achievement.SOLVED_FIVE_HUNDRED -> R.string.achievement_solved_five_hundred
    Achievement.THREE_MODULES -> R.string.achievement_three_modules
    Achievement.HOUR_OF_TRAINING -> R.string.achievement_hour_of_training
    Achievement.RANK_MASTER -> R.string.achievement_rank_master
}
