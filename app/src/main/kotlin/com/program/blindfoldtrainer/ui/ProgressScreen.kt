package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.progress.Benchmarks
import com.program.blindfoldtrainer.core.progress.Depth
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SkillEntry
import com.program.blindfoldtrainer.core.progress.SkillProfile
import com.program.blindfoldtrainer.core.progress.TaskProfile
import com.program.blindfoldtrainer.core.progress.SkillTrend

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
fun ProgressScreen(
    progress: ProgressSnapshot,
    /** Zadaci iz registra — po njima se znaju orijentiri. Modul ih prijavljuje. */
    tasks: Map<String, TaskSpec>,
    onBack: () -> Unit
) {
    // Dok je merena samo jedna veština, „najslabija" nema sa čim da se poredi
    // — a na jedinoj merenoj zvuči kao prekor umesto kao putokaz.
    val weakest = progress.weakestSkill.takeIf { progress.measuredSkills.size >= 2 }

    // Automatizam se meri prema orijentiru zadatka, a orijentire zna registar —
    // isti spisak zadataka koji ovaj ekran ionako dobija.
    val benchmarks = remember(tasks) { Benchmarks.of(tasks.values) }

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
                    profile = progress.bySkill[skill],
                    checkup = progress.lastCheckup(skill),
                    isAutomatic = progress.isAutomatic(skill, benchmarks),
                    foundationsMissing = progress.foundationsMissing(skill, benchmarks),
                    isWeakest = skill == weakest,
                    trendFor = { taskId -> progress.trendFor(skill, taskId) },
                    depthFor = { taskId -> progress.depthFor(taskId) },
                    specFor = { taskId -> tasks[taskId] },
                    sessionsFor = { taskId, rung -> progress.sessionsFor(skill, taskId, rung) }
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
    profile: SkillProfile?,
    checkup: SkillEntry?,
    isAutomatic: Boolean,
    foundationsMissing: Set<Skill>,
    isWeakest: Boolean,
    trendFor: (String) -> SkillTrend?,
    depthFor: (String) -> Depth?,
    specFor: (String) -> TaskSpec?,
    sessionsFor: (String, Support) -> List<SkillEntry>
) {
    var expanded by remember(skill) { mutableStateOf(false) }

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
            // Naslov i nivo idu **jedno ispod drugog**, ne jedno pored drugog.
            // U redu su se prvo preklapali, a kad je naslov dobio težinu, počeo
            // je da se lomi nasred reči — „Koordinatn / a / automatik / a".
            // Nijedna raspodela širine ne radi kad su oba teksta dugačka.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(skill.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Nivo dolazi **iz provere** — jedinog merenja koje je svima
                // jednako. Vežbe daju napredak, ali ne i mesto na lestvici.
                if (checkup != null) {
                    Text(
                        text = stringResource(
                            R.string.checkup_level,
                            checkup.tally.solved,
                            checkup.tally.attempted,
                            secondsLabel(checkup.tally.millisPerAttempt)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (profile == null) {
                    Text(
                        text = stringResource(R.string.progress_not_measured),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isAutomatic) {
                    Text(
                        text = stringResource(R.string.progress_automatic),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // „Još nema podatka" važi samo kad ga zaista nema. Uz nivo iz provere
            // je kartica govorila oba: **nivo 5/5** i ispod njega „veština o kojoj
            // se još ne zna". Provera jeste podatak, i to jedini koji je svima
            // jednak — vežba daje napredak, provera daje nivo.
            Text(
                text = stringResource(
                    if (profile == null && checkup == null) {
                        R.string.progress_not_measured_hint
                    } else {
                        skill.hintRes()
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Vežbana a neproverena veština ima napredak ali nema nivo, i to se
            // kaže — inače bi obim vežbanja izgledao kao dokaz o nivou.
            if (checkup == null && profile != null) {
                Text(
                    text = stringResource(R.string.checkup_never),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Preduslovi ne zaključavaju ništa — samo objasne zašto ovo ide
            // teško i šta bi pomoglo.
            if (foundationsMissing.isNotEmpty()) {
                val names = foundationsMissing.map { stringResource(it.labelRes()) }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.progress_foundation, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Po jedan odeljak za svaki zadatak. Zbir preko zadataka namerno ne
            // postoji: pitanje u Geometriji i pozicija u Završnici su oba „jedan
            // pokušaj", a nemaju ni istu cenu ni istu težinu.
            //
            // **Sklopljeno po zatečenom.** Rasklopljeno, jedna veština ume da
            // zauzme ceo ekran — po zadatku ide trend, dubina, pa za svaku prečku
            // traka, orijentir i kriva. Ko otvori Napredak pita „gde stojim", a to
            // je jedan red po zadatku; ostalo je za onoga ko je već stao i gleda.
            // Isti postupak kao na kartici modula.
            val tasks = profile?.tasks.orEmpty()

            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                tasks.forEach { (taskId, task) ->
                    if (expanded) {
                        Spacer(Modifier.height(14.dp))
                        TaskRows(
                            taskId = taskId,
                            task = task,
                            trend = trendFor(taskId),
                            depth = depthFor(taskId),
                            spec = specFor(taskId),
                            sessionsFor = { rung -> sessionsFor(taskId, rung) }
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        TaskHeader(taskId = taskId, task = task)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.progress_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(R.string.progress_details),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isWeakest) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.progress_weakest),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.progress_estimate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Ime zadatka i **prečka koju drži** — jedini red koji se vidi dok je veština
 * sklopljena.
 *
 * To je i odgovor na pitanje zbog kog se Napredak otvara: ne koliko si pokušaja
 * imao, nego dokle si stigao.
 */
@Composable
private fun TaskHeader(taskId: String, task: TaskProfile) {
    val held = task.heldRung()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(taskLabelRes(taskId)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = if (held == null) {
                stringResource(R.string.progress_no_rung_held)
            } else {
                stringResource(R.string.progress_holds, stringResource(held.labelRes()))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskRows(
    taskId: String,
    task: TaskProfile,
    trend: SkillTrend?,
    depth: Depth?,
    spec: TaskSpec?,
    sessionsFor: (Support) -> List<SkillEntry>
) {
    TaskHeader(taskId = taskId, task = task)

    TaskTrend(trend)

    // Dokle se izdržalo pre prve greške. Stoji uz trend jer odgovara na drugo
    // pitanje od tačnosti: ne koliko si pogodio, nego dokle je slika držala.
    depth?.let {
        Text(
            text = stringResource(R.string.progress_depth, it.last, it.best),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    task.triedRungs.forEach { rung ->
        val tally = task.at(rung) ?: return@forEach
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(rung.labelRes()),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.progress_skill_score, tally.solved, tally.attempted),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (tally.attempted > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { tally.solved.toFloat() / tally.attempted },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }

        val benchmark = spec?.benchmarkFor(rung)
        if (benchmark != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (task.hasReached(rung, benchmark)) {
                    stringResource(R.string.progress_target_reached)
                } else {
                    // Orijentir se kaže i pre nego što je dostignut — cilj koji
                    // se ne vidi ne vuče nikuda.
                    stringResource(
                        R.string.progress_target,
                        secondsLabel(benchmark.millisPerAttempt),
                        (benchmark.minAccuracy * 100).toInt()
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Kriva ide po prečki, ne preko njih: linija koja meša prečke ponovila
        // bi grešku zbog koje se prečka uopšte i upisuje.
        Spacer(Modifier.height(6.dp))
        ProgressChart(
            sessions = sessionsFor(rung),
            benchmark = benchmark,
            modifier = Modifier.fillMaxWidth()
        )
    }

}

/**
 * Trend se gleda unutar istog zadatka — inače bi poredio dve sekunde po pitanju
 * sa tri minuta po poziciji. Vreme je ovde važnije od procenta: procenat ume da
 * bude dobar odavno, a vežba i dalje spora.
 */
@Composable
private fun TaskTrend(trend: SkillTrend?) {
    if (trend != null && trend.hasComparison) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.progress_trend_earlier,
                trend.earlier.solved,
                trend.earlier.attempted,
                secondsLabel(trend.earlier.millisPerAttempt)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.progress_trend_recent,
                trend.recent.solved,
                trend.recent.attempted,
                secondsLabel(trend.recent.millisPerAttempt)
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Vreme po zadatku, u sekundama sa jednom decimalom.
 *
 * Milisekunde se ne pokazuju: razlika od 1900 i 2000 ms nikome ništa ne znači, a
 * „1,9 s" i „3,4 s" se razlikuju na prvi pogled.
 */
@Composable
private fun secondsLabel(millisPerAttempt: Long?): String {
    val seconds = (millisPerAttempt ?: 0L) / 1000f
    return stringResource(R.string.progress_seconds, String.format("%.1f", seconds))
}
