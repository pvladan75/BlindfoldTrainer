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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SkillStage
import com.program.blindfoldtrainer.core.progress.levelOf

/**
 * **Presek** — gde stojiš po svim veštinama, izmereno na isti način.
 *
 * Napredak pokazuje ono što je vežba **usput** proizvela: koliko si radio, kojim
 * zadacima, na kom osloncu. Presek je nešto drugo — **namerno i ujednačeno
 * merenje u jednoj tački u vremenu**, koje se sme uporediti sa istim takvim
 * merenjem mesec dana kasnije. Vežba daje napredak, provera daje nivo.
 *
 * Zato se ovde ne prikazuju krive ni broj sesija. Prikazuje se samo ono što je
 * uporedivo: **nivo veštine** i **kad je poslednji put potvrđen**.
 *
 * Merenja se ne rade odjednom u jednom dahu nego se **skupljaju**: osam merenja
 * ne stane u tri minuta, a jedno stane u jedan. Presek zato uvek pokazuje ono što
 * ima, uz jasno rečeno šta je staro i šta nedostaje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(
    progress: ProgressSnapshot,
    tasks: Map<String, TaskSpec>,
    /** Provere koje se u ovoj verziji zaista mogu ponuditi. */
    checkups: List<Checkup>,
    onStartCheckup: (Checkup) -> Unit,
    onBack: () -> Unit
) {
    val allTasks = tasks.values.toList()
    val bySkill = checkups.associateBy { it.skill }

    // Redosled je **iz stabla, ne po uspehu**: presek se čita odozdo naviše, jer
    // se tako i gradi. Spisak koji se prekraja pri svakom merenju se ne pamti.
    val rows = Skill.entries.map { skill ->
        SnapshotRow(
            skill = skill,
            level = progress.levelOf(skill, allTasks),
            checkup = progress.lastCheckup(skill),
            offered = bySkill[skill]
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.snapshot_title)) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.snapshot_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Prvo ono što **nije mereno**, i to kao poziv a ne kao prekor: presek
            // sa rupom je i dalje presek, ali rupa mora da se vidi pre brojeva.
            val pending = rows.filter { it.needsCheckup }
            if (pending.isNotEmpty()) {
                item(key = "pending") {
                    PendingCard(
                        rows = pending,
                        onStartCheckup = onStartCheckup
                    )
                }
            }

            items(rows, key = { it.skill.key }) { row ->
                SnapshotCard(row = row, onStartCheckup = onStartCheckup)
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.snapshot_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Jedan red preseka: veština, njen nivo, i kad je poslednji put potvrđen. */
private data class SnapshotRow(
    val skill: Skill,
    val level: com.program.blindfoldtrainer.core.progress.SkillLevel,
    val checkup: com.program.blindfoldtrainer.core.progress.SkillEntry?,
    /** Provera koja za ovu veštinu postoji, ako postoji. */
    val offered: Checkup?
) {
    /**
     * Nedostaje merenje.
     *
     * Veštinu koju **nijedan zadatak ne meri** ovde ne računamo kao nedostatak
     * merenja — tu ne fali provera nego vežba, i to se kaže drugim rečima.
     */
    val needsCheckup: Boolean
        get() = offered != null && checkup == null
}

/**
 * Šta još nije izmereno, sa dugmetom po veštini.
 *
 * Stoji **iznad** brojeva namerno: presek u kom polovina veština stoji na „nije
 * mereno" nije loš presek, ali jeste nepotpun — i to je prva stvar koju treba
 * znati pre nego što se ostatak počne čitati.
 */
@Composable
private fun PendingCard(rows: List<SnapshotRow>, onStartCheckup: (Checkup) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.snapshot_pending_title, rows.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.snapshot_pending_hint),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            rows.forEach { row ->
                val checkup = row.offered ?: return@forEach
                FilledTonalButton(
                    onClick = { onStartCheckup(checkup) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.snapshot_measure,
                            stringResource(row.skill.labelRes())
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Jedna veština u preseku.
 *
 * Namerno **bez krivih i bez broja sesija** — to su podaci o vežbi, a presek se
 * bavi merenjem. Sve što ovde stoji sme da se uporedi sa istim redom od pre
 * mesec dana.
 */
@Composable
private fun SnapshotCard(row: SnapshotRow, onStartCheckup: (Checkup) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(row.skill.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = row.level.headline(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.level.stage == SkillStage.NOT_MEASURED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = when {
                    // Ovde ne fali provera nego **vežba** — to su dve različite
                    // rupe i ne smeju da se kažu istom rečenicom.
                    row.offered == null -> stringResource(R.string.snapshot_no_task)
                    row.checkup == null -> stringResource(R.string.snapshot_never)
                    else -> stringResource(
                        R.string.checkup_confirmed,
                        row.checkup.tally.solved,
                        row.checkup.tally.attempted,
                        daysAgoLabel(row.checkup.atMillis)
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            row.offered?.let { checkup ->
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { onStartCheckup(checkup) }) {
                    Text(
                        stringResource(
                            if (row.checkup == null) {
                                R.string.snapshot_measure_short
                            } else {
                                R.string.snapshot_remeasure
                            }
                        )
                    )
                }
            }
        }
    }
}
