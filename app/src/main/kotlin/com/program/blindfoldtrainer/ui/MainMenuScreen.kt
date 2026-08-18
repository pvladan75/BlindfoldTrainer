package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.progress.Recommendation
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.Achievement
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.Rank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    modules: List<TrainingModule>,
    progress: ProgressSnapshot,
    /** Da li je u Podešavanjima uključen režim bez ekrana. */
    eyesFree: Boolean,
    onStart: (TrainingModule, ModuleArgs) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    /** Uputstvo — čemu sve ovo i kako se čita ono što piše po ekranima. */
    onOpenGuide: () -> Unit,
    /** Provera koja se nudi, ili `null` ako je nema. */
    checkup: Checkup?,
    onStartCheckup: (Checkup) -> Unit,
    /** Ime profila koji vežba; stoji u traci da se ne vežba pod tuđim imenom. */
    profileName: String?,
    onOpenProfiles: () -> Unit,
    /** Sledeći korak koji put predlaže, ili `null` dok se nema šta predložiti. */
    recommendation: Recommendation?,
    onStartRecommended: (Recommendation) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Ime profila umesto imena aplikacije: ono što se menja
                    // vredi više od onoga što uvek piše isto.
                    Text(
                        text = profileName ?: stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.guide_open)
                        )
                    }
                    IconButton(onClick = onOpenProfiles) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.profiles_open)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_open)
                        )
                    }
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
            item(key = "progress") {
                RankCard(progress = progress, onOpenProgress = onOpenProgress)
            }

            // Predlozi, ne obaveze: stoje iznad spiska, a spisak ostaje netaknut.
            recommendation?.let {
                item(key = "path") {
                    PathCard(recommendation = it, onStart = { onStartRecommended(it) })
                }
            }

            checkup?.let {
                item(key = "checkup") {
                    CheckupCard(checkup = it, onStart = { onStartCheckup(it) })
                }
            }

            items(modules, key = { it.id.key }) { module ->
                ModuleCard(
                    module = module,
                    eyesFree = eyesFree,
                    onStart = { args -> onStart(module, args) }
                )
            }

            item(key = "voice") { VoiceNotice(onOpenSettings = onOpenSettings) }
        }
    }
}

/**
 * Predlog koji put daje za sledeći korak.
 *
 * **Presudan je drugi red — razlog.** Preporuka bez razloga je proročanstvo, a
 * proročanstvu se ne veruje kad promaši; sa razlogom je argument i korisnik sme
 * da se ne složi. Zato ispod naslova stoji i **koliko pomoći** korisnik dobija,
 * jer je prečka pola predloga.
 *
 * Ništa se ne zaključava i odbijanje nema posledice: spisak modula stoji odmah
 * ispod, netaknut.
 */
@Composable
private fun PathCard(recommendation: Recommendation, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.path_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.path_offer,
                    stringResource(taskLabelRes(recommendation.taskId)),
                    stringResource(recommendation.support.labelRes())
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(recommendation.reason.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onStart) {
                Text(stringResource(R.string.path_start))
            }
        }
    }
}

/**
 * Poziv na proveru.
 *
 * Provera daje **nivo** — jedino merenje koje je svima jednako, pa se sme
 * porediti sa prošlim. Vežbe daju napredak unutar svog zadatka, ali ne i mesto
 * na lestvici.
 *
 * Da ne nosi poene piše **pre** dodira, ne posle: merilo koje nagrađuje prestaje
 * da meri.
 */
@Composable
private fun CheckupCard(checkup: Checkup, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.checkup_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.checkup_offer,
                    stringResource(checkup.skill.labelRes())
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.checkup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onStart) {
                Text(stringResource(R.string.checkup_start))
            }
        }
    }
}

/**
 * Rang i poeni. Stoji na vrhu menija, kao prva stavka liste a ne kao zaseban
 * red iznad nje — inače bi ostao zalepljen dok se spisak modula pomera.
 */
@Composable
private fun RankCard(progress: ProgressSnapshot, onOpenProgress: () -> Unit) {
    val rankProgress = progress.rankProgress

    Card(
        // Cela kartica je ulaz u profil: rang i poeni kažu koliko si radio, a
        // profil šta se od toga razvilo — i to drugo je ono zbog čega se vežba.
        onClick = onOpenProgress,
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
 * Obaveštenje o glasovnom unosu — bez ijednog dugmeta.
 *
 * Ranije je ovde stajalo preuzimanje paketa, a jezik se birao u Podešavanjima:
 * korisnik je preuzimao paket ne videvši za koji je jezik. Sada je sav izbor na
 * jednom mestu, a meni samo pokazuje put.
 */
@Composable
private fun VoiceNotice(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenSettings() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MicNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.voice_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.voice_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: TrainingModule,
    eyesFree: Boolean,
    onStart: (ModuleArgs) -> Unit
) {
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
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )

                // Modul sam prijavljuje da ume glasom; meni to samo prikaže.
                if (Capability.VOICE_INPUT in module.needs) {
                    Icon(
                        imageVector = Icons.Default.MicNone,
                        contentDescription = stringResource(R.string.menu_module_voice),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(module.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Šta se ovim modulom razvija — da se vidi da vežbe nisu same sebi
            // svrha. Modul prijavljuje zadatke, meni sabira njihove veštine.
            if (module.skills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                // `map` je inline pa sme da zove stringResource; `joinToString`
                // nije, pa se imena prvo pokupe a tek onda spoje.
                val names = module.skills.map { stringResource(it.labelRes()) }
                Text(
                    text = stringResource(R.string.menu_module_skills, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Da modul nema režim bez ekrana treba znati **pre** ulaska, a ne
            // tek kad se unutra otvori tabla u koju se ne gleda.
            if (eyesFree && !module.supportsEyesFree) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.menu_module_no_eyes_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Zadatak i prečka su se do sada birali samo **posredno** — globalnim
            // režimom bez ekrana ili predlogom puta. To je ostavljalo dve rupe:
            // srednja prečka se nije mogla dohvatiti bez dve uspešne sesije na
            // punoj podršci, a drugi zadatak modula uopšte nije imao ulaz iz
            // menija — put ga po svom prvom pravilu izbegava odmah posle vežbe.
            //
            // Zatečeno stanje ostaje **neizabrano**: dok se ne dodirne, modul
            // dobija `null` i odlučuje sam, tačno kao pre. Chip koji svetli
            // pokazuje šta bi se tada dogodilo, da izbor ne izgleda prazan.
            val rungs = remember(module.id) { module.rungs() }
            var chosenRung by remember(module.id) { mutableStateOf<Support?>(null) }
            var chosenTask by remember(module.id) { mutableStateOf<String?>(null) }

            if (module.tasks.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.menu_task_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val showing = chosenTask ?: module.defaultTaskId
                    module.tasks.forEach { task ->
                        FilterChip(
                            selected = task.id == showing,
                            onClick = { chosenTask = task.id },
                            label = { Text(stringResource(taskLabelRes(task.id))) }
                        )
                    }
                }
            }

            if (rungs.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.menu_support_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val showing = chosenRung ?: if (eyesFree) rungs.last() else rungs.first()
                    rungs.forEach { rung ->
                        FilterChip(
                            selected = rung == showing,
                            onClick = { chosenRung = rung },
                            label = { Text(stringResource(rung.labelRes())) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                module.difficulties.forEach { difficulty ->
                    FilledTonalButton(
                        onClick = {
                            onStart(
                                ModuleArgs(
                                    difficulty = difficulty,
                                    taskId = chosenTask,
                                    support = chosenRung
                                )
                            )
                        },
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

/**
 * Prečke koje ovaj modul ume — **unija zadataka**, isto pravilo po kom se sabiraju
 * i veštine. Zadatak koji tu prečku ne ume dobija najbližu koju ume, pa izbor
 * nikad ne odvede u prazno.
 */
private fun TrainingModule.rungs(): List<Support> =
    tasks.flatMap { it.supports }.distinct().sortedBy { it.ordinal }

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
