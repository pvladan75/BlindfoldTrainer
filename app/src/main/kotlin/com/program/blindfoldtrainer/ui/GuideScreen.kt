package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.model.requires
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule

/**
 * Uputstvo: čemu sve ovo i kako se čita ono što piše po ekranima.
 *
 * **Proza stoji u `strings.xml`, struktura se čita iz registra.** Spisak veština,
 * grane preduslova, ko šta meri, koje prečke postoje i za šta ima provere —
 * ništa od toga se ovde ne prepisuje, nego se izvodi iz istih podataka po kojima
 * aplikacija i radi.
 *
 * Razlog je onaj isti zbog kog moduli idu kroz registar a ne kroz `when` blok:
 * uputstvo koje prepisuje činjenice zastari čim se doda deveti modul, i to niko
 * ne primeti — za razliku od koda, tekst se ne prevodi pa se ne buni.
 *
 * Ono što se **ne** izvodi je jedino ono o čemu je neko doneo odluku: zašto
 * preduslovi idu tim redom, čemu provera, zašto orijentir ima i vreme i tačnost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    modules: List<TrainingModule>,
    /**
     * Provere koje se u ovoj verziji zaista mogu ponuditi — ne spisak namera.
     * Uputstvo tvrdi korisniku šta je merljivo, pa mora da gleda isto ono što se
     * i nudi na kartici.
     */
    checkups: List<Checkup>,
    onBack: () -> Unit
) {
    // Ko šta meri — po tome se zna čime se veština vežba. Zadatak nosi više
    // veština, ali u profil ide po **prvoj**, pa se i ovde broji ta.
    val byMeasuredSkill: Map<Skill, List<Pair<TrainingModule, TaskSpec>>> =
        modules.flatMap { module -> module.tasks.map { module to it } }
            .groupBy { (_, task) -> task.measures }

    // Prečke koje u aplikaciji zaista postoje. `Support` ih poznaje četiri, ali
    // opisati prečku koju nijedan zadatak ne nudi znači obećati vežbu koje nema.
    val livingRungs = modules.flatMap { it.tasks }
        .flatMap { it.supports }
        .distinct()
        .sortedBy { it.ordinal }

    // Veštine za koje provera postoji. Isti spisak koji se i nudi na kartici,
    // da uputstvo ne obeća merenje kog nema.
    val checked = checkups.mapTo(mutableSetOf()) { it.skill }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
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
                Section(titleRes = R.string.guide_intro_title) {
                    Body(R.string.guide_intro)
                }
            }

            // ---- Veštine ---------------------------------------------------
            item(key = "skills") {
                Section(titleRes = R.string.guide_skills_title) {
                    Body(R.string.guide_skills_intro)
                }
            }

            items(Skill.entries.toList(), key = { "skill_${it.key}" }) { skill ->
                SkillEntry(
                    skill = skill,
                    measuredBy = byMeasuredSkill[skill].orEmpty(),
                    hasCheckup = skill in checked
                )
            }

            // ---- Preduslovi ------------------------------------------------
            item(key = "deps") {
                Section(titleRes = R.string.guide_deps_title) {
                    // Slika ide **pre** teksta: ona zamenjuje pola objašnjenja,
                    // a ostatak tek posle nje ima gde da se zakači.
                    Spacer(Modifier.height(4.dp))
                    // Slika ovde stoji **bez tvog stanja**, namerno: uputstvo
                    // objašnjava pravilo, a pravilo je isto za svakoga. Ista slika
                    // sa tvojim stanjem stoji u Napretku, gde se i pita „gde
                    // stojim" — ovde bi to bio odgovor na pitanje koje se ne
                    // postavlja.
                    SkillGraph()
                    Spacer(Modifier.height(14.dp))
                    Body(R.string.guide_deps)
                    Spacer(Modifier.height(10.dp))
                    // Sa slike se odmah vidi da neke veštine nemaju nastavak, i
                    // to je prvo što se o njoj pita. Bolje odgovoriti tu nego
                    // pustiti da izgledaju kao višak.
                    Body(R.string.guide_deps_leaves)
                }
            }

            // ---- Modul, zadatak, prečka -------------------------------------
            // **Zašto plan** dolazi odmah posle slike zavisnosti: slika pokazuje
            // da red postoji, a ovo kaže zašto se on ne sme birati po osećaju.
            item(key = "plan") {
                Section(titleRes = R.string.guide_plan_title) {
                    Body(R.string.guide_plan)
                }
            }

            item(key = "levels") {
                Section(titleRes = R.string.guide_levels_title) {
                    Body(R.string.guide_levels)
                    Spacer(Modifier.height(10.dp))
                    Body(R.string.guide_rungs_intro)
                    Spacer(Modifier.height(8.dp))
                    livingRungs.forEach { rung ->
                        Text(
                            text = stringResource(rung.labelRes()).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(rung.guideRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Body(R.string.guide_rungs_effect)
                }
            }

            // **Ležeći** ide posle oslonca, jer se tek tu vidi šta „bez table"
            // zaista znači: ne teži ekran nego nikakav.
            item(key = "posture") {
                Section(titleRes = R.string.guide_posture_title) {
                    Body(R.string.guide_posture)
                }
            }

            item(key = "measure") {
                Section(titleRes = R.string.guide_measure_title) {
                    Body(R.string.guide_measure)
                }
            }

            // ---- Provera ----------------------------------------------------
            item(key = "checkup") {
                Section(titleRes = R.string.guide_checkup_title) {
                    Body(R.string.guide_checkup)
                    Spacer(Modifier.height(10.dp))
                    // Koje veštine provera pokriva se čita iz `Checkups`. Da je
                    // prepisano, prvi dodatak provere bi ovde ostavio laž.
                    val covered = checked
                    val rows = Skill.entries.map { skill ->
                        val mark = if (skill in covered) "✓" else "—"
                        "$mark  ${stringResource(skill.labelRes())}"
                    }
                    Text(
                        text = rows.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "path") {
                Section(titleRes = R.string.guide_path_title) {
                    Body(R.string.guide_path)
                }
            }

            // ---- Moduli ------------------------------------------------------
            item(key = "modules") {
                Section(titleRes = R.string.guide_modules_title) {
                    Body(R.string.guide_modules_intro)
                }
            }

            items(modules, key = { "module_${it.id.key}" }) { module ->
                ModuleEntry(module)
            }
        }
    }
}

/** Jedna veština: ime, rečenica, kako otkazuje, čime se vežba, ima li proveru. */
@Composable
private fun SkillEntry(
    skill: Skill,
    measuredBy: List<Pair<TrainingModule, TaskSpec>>,
    hasCheckup: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(skill.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(skill.hintRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(skill.guideRes()),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Veština koju nijedan zadatak ne meri se **ne prećutkuje**: to je
            // podatak o aplikaciji, ne o korisniku, i bolje da ga pročita ovde
            // nego da ga naslućuje iz praznog profila.
            Text(
                text = if (measuredBy.isEmpty()) {
                    stringResource(R.string.guide_trains_none)
                } else {
                    val names = measuredBy.map { (module, task) ->
                        "${stringResource(taskLabelRes(task.id))}" +
                            " (${stringResource(module.titleRes)})"
                    }
                    stringResource(R.string.guide_trains, names.joinToString())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    if (hasCheckup) R.string.guide_has_checkup else R.string.guide_no_checkup
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Jedan modul: ono što već piše u meniju, plus zadaci i prečke. */
@Composable
private fun ModuleEntry(module: TrainingModule) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(module.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(module.descriptionRes),
                style = MaterialTheme.typography.bodyMedium
            )

            if (module.tasks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val taskNames = module.tasks.map { stringResource(taskLabelRes(it.id)) }
                val rungNames = module.tasks.flatMap { it.supports }
                    .distinct()
                    .sortedBy { it.ordinal }
                    .map { stringResource(it.labelRes()) }

                Text(
                    text = stringResource(R.string.guide_module_tasks, taskNames.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.guide_module_rungs, rungNames.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Body(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium
    )
}
