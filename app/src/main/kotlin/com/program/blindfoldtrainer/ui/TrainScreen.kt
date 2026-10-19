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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.Recommendation

/**
 * Kartica **Vežbaj** — prva od tri. Odgovara na jedno pitanje: *šta sad*.
 *
 * Gore stoji **jedna** ponuda (provera ili vežba, vidi [chooseNext]), ispod
 * spisak modula za onoga ko hoće da bira sam. Rang, poeni, uputstvo, profil i
 * podešavanja su otišli na karticu **Ja**: ovde je stajalo sve odjednom, pa je
 * ekran na kom se bira vežba ličio na kontrolnu tablu.
 *
 * Redovi modula se **ne rasklapaju**. Dodir vodi na stranicu modula, gde se
 * bira zadatak, oslonac i težina i pritisne jedno dugme. Rasklopljena kartica
 * usred spiska je bila stranica stisnuta u red — i sa tri dugmeta za težinu
 * umesto jednog „kreni".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(
    modules: List<TrainingModule>,
    progress: ProgressSnapshot,
    /** Da li je u Podešavanjima uključen režim bez ekrana. */
    eyesFree: Boolean,
    /** Ime profila koji vežba; stoji u traci da se ne vežba pod tuđim imenom. */
    profileName: String?,
    /** Sledeći korak koji put predlaže, ili `null` dok se nema šta predložiti. */
    recommendation: Recommendation?,
    /** Provera koja se nudi, ili `null` ako je nema. */
    checkup: Checkup?,
    onStartRecommended: (Recommendation) -> Unit,
    onStartCheckup: (Checkup) -> Unit,
    onOpenModule: (TrainingModule) -> Unit
) {
    val offer = remember(recommendation, checkup, progress) {
        chooseNext(recommendation, checkup, progress)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = profileName ?: stringResource(R.string.app_name),
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            offer?.let {
                item(key = "next") {
                    NextCard(
                        offer = it,
                        onStart = { step ->
                            when (step) {
                                is NextStep.Measure -> onStartCheckup(step.checkup)
                                is NextStep.Practise -> onStartRecommended(step.recommendation)
                            }
                        }
                    )
                }
            }

            item(key = "choose") {
                Text(
                    text = stringResource(R.string.train_choose),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }

            items(modules, key = { it.id.key }) { module ->
                ModuleRow(
                    module = module,
                    choice = module.startingChoice(progress, eyesFree),
                    eyesFree = eyesFree,
                    onOpen = { onOpenModule(module) }
                )
            }
        }
    }
}

/**
 * **Sledeće** — jedna ponuda, jedno dugme.
 *
 * Presudan je red sa razlogom: preporuka bez razloga je proročanstvo, a
 * proročanstvu se ne veruje kad promaši. Za proveru razlog je uvek isti — da
 * se izmeri gde se stoji — pa stoji i to da ne nosi poene, **pre** dodira.
 *
 * Ono što nije izabrano stoji ispod kao red teksta: ne gubi se, ne nameće se.
 */
@Composable
private fun NextCard(offer: NextOffer, onStart: (NextStep) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.next_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            when (val step = offer.primary) {
                is NextStep.Measure -> {
                    Text(
                        text = stringResource(
                            R.string.next_measure,
                            stringResource(step.checkup.skill.labelRes())
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.next_measure_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is NextStep.Practise -> {
                    val recommendation = step.recommendation
                    val task = stringResource(taskLabelRes(recommendation.taskId))
                    val rung = stringResource(recommendation.support.labelRes())
                    Text(
                        text = recommendation.difficulty?.let { difficulty ->
                            stringResource(
                                R.string.path_offer_graded,
                                task,
                                rung,
                                stringResource(difficulty.labelRes())
                            )
                        } ?: stringResource(R.string.path_offer, task, rung),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(recommendation.reason.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onStart(offer.primary) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.next_start))
            }

            offer.alternative?.let { other ->
                TextButton(
                    onClick = { onStart(other) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (other) {
                            is NextStep.Measure -> stringResource(
                                R.string.next_or_measure,
                                stringResource(other.checkup.skill.labelRes())
                            )
                            is NextStep.Practise -> stringResource(
                                R.string.next_or_practise,
                                stringResource(taskLabelRes(other.recommendation.taskId))
                            )
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Red modula: ime, ikona i **šta bi se pokrenulo** (zadatak · oslonac · težina).
 *
 * To je jedini podatak koji se traži dok se bira; ostalo je na stranici modula.
 * Da modul nema režim bez ekrana treba znati **pre** ulaska, pa i to stoji
 * ovde — inače bi se saznalo tek unutra, pred tablom u koju se ne gleda.
 */
@Composable
private fun ModuleRow(
    module: TrainingModule,
    choice: ModuleChoice,
    eyesFree: Boolean,
    onOpen: () -> Unit
) {
    val pickableTask = module.tasks.size > 1
    val rungCount = module.tasks.find { it.id == choice.taskId }?.supports?.size
        ?: module.tasks.flatMap { it.supports }.distinct().size

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(module.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(module.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val summary = listOfNotNull(
                        choice.taskId.takeIf { pickableTask }?.let { stringResource(taskLabelRes(it)) },
                        choice.shownSupport.takeIf { rungCount > 1 }?.let { stringResource(it.labelRes()) },
                        module.difficultyDetail(choice.difficulty, choice.taskId)
                            ?: stringResource(choice.difficulty.labelRes())
                    ).joinToString(" · ")

                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Modul sam prijavljuje da ume glasom; meni to samo prikaže.
                if (Capability.VOICE_INPUT in module.needs) {
                    Icon(
                        imageVector = Icons.Default.MicNone,
                        contentDescription = stringResource(R.string.menu_module_voice),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (eyesFree && !module.supportsEyesFree) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.menu_module_no_eyes_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
