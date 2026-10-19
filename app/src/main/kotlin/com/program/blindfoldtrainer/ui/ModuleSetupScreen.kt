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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot

/**
 * Stranica modula: šta je, šta gradi, šta se bira — i **jedno** dugme.
 *
 * Nasledila je rasklopljenu karticu iz menija. Tamo su tri dugmeta za težinu
 * bila ujedno i „kreni", pa je težina izgledala kao najvažniji izbor, a bila
 * je najmanje važan: oslonac je prava lestvica za rad naslepo. Sad su sva tri
 * izbora jednaki redovi, a dugme je jedno i zove se onim što radi.
 *
 * Sve je **unapred izabrano** ([startingChoice]): ko ne dira ništa, kreće tamo
 * gde je prošli put stao. Birači stoje za onoga ko hoće drugačije.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleSetupScreen(
    module: TrainingModule,
    progress: ProgressSnapshot,
    /** Da li je u Podešavanjima uključen režim bez ekrana. */
    eyesFree: Boolean,
    onStart: (ModuleArgs) -> Unit,
    onBack: () -> Unit
) {
    val starting = remember(module.id, progress, eyesFree) {
        module.startingChoice(progress, eyesFree)
    }

    // `rememberSaveable`, da izbor preživi ponovno pravljenje aktivnosti.
    // Oslonac se pamti kao **ključ**, jer se `Support` ne ume sam sačuvati.
    var chosenTask by rememberSaveable(module.id) { mutableStateOf(starting.taskId) }
    var chosenRungKey by rememberSaveable(module.id) { mutableStateOf(starting.support?.key) }
    var chosenDifficulty by rememberSaveable(module.id) { mutableStateOf(starting.difficulty.name) }

    val task = module.tasks.find { it.id == chosenTask }

    // **Prečke pripadaju zadatku, ne modulu.** Nude se samo one koje izabrani
    // zadatak ume — inače bi se izbor ćutke sveo na najbližu koju ima.
    val rungs = remember(module.id, chosenTask) {
        task?.supports?.sortedBy { it.ordinal }
            ?: module.tasks.flatMap { it.supports }.distinct().sortedBy { it.ordinal }
    }

    // Oslonac se **nudi** samo kad je lestvica. Gde su to samo dva načina rada
    // — gledaš ili ne gledaš — odlučuje podešavanje, jednom za sve module.
    val isLadder = task?.supportIsLadder != false
    val pickableRung = rungs.size > 1 && isLadder
    val pickableTask = module.tasks.size > 1

    // Prečka izabrana za prošli zadatak ne mora da postoji u novom.
    val shownRung = chosenRungKey?.let { key -> Support.entries.find { it.key == key } }
        ?.takeIf { it in rungs }
        ?: if (eyesFree) rungs.lastOrNull() else rungs.firstOrNull()

    val difficulty = Difficulty.entries.find { it.name == chosenDifficulty } ?: starting.difficulty

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(module.titleRes), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Dugme stoji **na dnu, uvek vidljivo**: stranica se čita odozgo, a
            // odluka se donosi na kraju — pa se ne traži prstom kroz spisak.
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = {
                        // **Šalje se ono što na stranici piše.** Gde se oslonac ne
                        // bira, ide `null`: tamo odlučuje podešavanje.
                        onStart(
                            ModuleArgs(
                                difficulty = difficulty,
                                taskId = chosenTask,
                                support = shownRung.takeIf { pickableRung }
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_start),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(module.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(module.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Šta se ovim modulom razvija — da se vidi da vežbe nisu same sebi
            // svrha. Modul prijavljuje zadatke, stranica sabira njihove veštine.
            if (module.skills.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val names = module.skills.map { stringResource(it.labelRes()) }
                Text(
                    text = stringResource(R.string.menu_module_skills, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (eyesFree && !module.supportsEyesFree) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.menu_module_no_eyes_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (pickableTask) {
                ChoiceLabel(R.string.menu_task_label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    module.tasks.forEach { candidate ->
                        FilterChip(
                            selected = candidate.id == chosenTask,
                            onClick = {
                                chosenTask = candidate.id
                                // Novi zadatak nosi svoje prečke; stari izbor bi
                                // se u njemu svodio na nešto drugo.
                                chosenRungKey = null
                            },
                            label = { Text(stringResource(taskLabelRes(candidate.id))) }
                        )
                    }
                }
            }

            if (pickableRung) {
                ChoiceLabel(R.string.menu_support_label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rungs.forEach { rung ->
                        FilterChip(
                            selected = rung == shownRung,
                            onClick = { chosenRungKey = rung.key },
                            label = { Text(stringResource(rung.labelRes())) }
                        )
                    }
                }
            }

            // **Šta oslonac ovde znači.** Imena su zajednička, ali posao nije:
            // stoji i kad se oslonac ne bira — tada je to jedino mesto na kom se
            // vidi kako će izgledati.
            shownRung?.let { rung ->
                module.supportDetail(rung, chosenTask)?.let { detail ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (module.difficulties.size > 1) {
                ChoiceLabel(R.string.setup_difficulty_label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    module.difficulties.forEach { candidate ->
                        // Na čipu piše **šta težina znači**, a ne kako se zove:
                        // redosled se ionako vidi iz toga što stoje s leva na desno.
                        FilterChip(
                            selected = candidate == difficulty,
                            onClick = { chosenDifficulty = candidate.name },
                            label = {
                                Text(
                                    module.difficultyDetail(candidate, chosenTask)
                                        ?: stringResource(candidate.labelRes())
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoiceLabel(textRes: Int) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
}
