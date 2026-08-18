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
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot

/**
 * Profil: šta je jako, šta slabo, a šta se još ne zna.
 *
 * Prikazuje se **po veštinama, ne po modulima** — modul je alat, veština je ono
 * što se razvija, pa je i spisak takav.
 *
 * Tri odluke koje ovaj ekran drže poštenim:
 *
 * - **„Nije mereno" stoji umesto nule.** Nula bi rekla „loš si u tome", a istina
 *   je „o tome još ništa ne znamo".
 * - **Ističe se jedna slabost**, ona najslabija. Ekran na kom šest od osam
 *   veština stoji crveno je tačan i beskoristan — otvori se jednom.
 * - **Uz svaku veštinu stoji rečenica šta znači.** Bez toga je profil spisak
 *   stranih reči.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(progress: ProgressSnapshot, onBack: () -> Unit) {
    val weakest = progress.weakestSkill

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
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
            if (progress.bySkill.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.progress_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Redosled je iz modela, ne po uspehu: spisak koji se prekraja pri
            // svakoj sesiji se ne pamti, a profil se čita više puta.
            items(Skill.entries, key = { it.key }) { skill ->
                SkillCard(
                    skill = skill,
                    tally = progress.bySkill[skill],
                    isWeakest = skill == weakest
                )
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.progress_old_sessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    tally: com.program.blindfoldtrainer.core.model.SkillTally?,
    isWeakest: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWeakest) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(skill.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (tally == null) {
                        stringResource(R.string.progress_not_measured)
                    } else {
                        stringResource(
                            R.string.progress_skill_score,
                            tally.solved,
                            tally.attempted
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(
                    if (tally == null) R.string.progress_not_measured_hint else skill.hintRes()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Traka postoji samo tamo gde ima šta da se prikaže. Prazna traka bi
            // izgledala kao nula, a nula i „nije mereno" nisu ista stvar.
            if (tally != null && tally.attempted > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { tally.solved.toFloat() / tally.attempted },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }

            if (isWeakest) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.progress_weakest),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
