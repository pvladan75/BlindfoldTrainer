package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.progress.SkillEntry

/**
 * Kriva vremena po zadatku kroz sesije, sa dve vodoravne linije.
 *
 * ### Šta je na kojoj osi i zašto
 *
 * **Vodoravno stoji redni broj sesije, ne datum.** Ko vežba dvaput nedeljno
 * dobio bi grafik od samih praznina; datum je podatak, ne mera — isti razlog iz
 * kog je i prozor trenda po broju pokušaja.
 *
 * **Uspravno stoji vreme, ne procenat.** Tačnost se zasiti brzo — dođe do 9/10 i
 * tu stane, pa linija umre — dok vreme pada mnogo duže i pokazuje napredak i kad
 * procenat miruje. Tačnost nije izgubljena: nosi je **sama tačka**, puna kad je
 * sesija dostigla traženu tačnost, šuplja kad nije.
 *
 * ### Zašto dve linije
 *
 * Orijentir sam, pet puta ispod početnikove krive, nije cilj nego **zid** — takav
 * se grafik otvori jednom. Zato uz njega ide i **tvoj najbolji**: dostižan,
 * pomera se sa tobom, i najčešće je ono što se zaista goni. Rastojanje između
 * njih je priča o napretku umesto podsetnika koliko fali.
 *
 * ### Zašto ne ispod tri sesije
 *
 * Kriva kroz dve tačke nije trend nego nagoveštaj koji ume da slaže u oba
 * pravca. Dotle **piše koliko ih ima** — prazno mesto se čita kao da grafika
 * nema, a on samo čeka treću sesiju.
 */
@Composable
fun ProgressChart(
    sessions: List<SkillEntry>,
    benchmark: Benchmark?,
    modifier: Modifier = Modifier
) {
    val points = sessions.mapNotNull { entry ->
        val perAttempt = entry.tally.millisPerAttempt ?: return@mapNotNull null
        val accuracy = if (entry.tally.attempted == 0) {
            0f
        } else {
            entry.tally.solved.toFloat() / entry.tally.attempted
        }
        perAttempt to accuracy
    }

    if (points.size < MIN_POINTS) {
        // **Odsustvo krive se ne prećutkuje.** Prazno mesto izgleda kao da
        // grafika za taj oslonac uopšte nema, a on samo čeka treću sesiju. Isti
        // razlog zbog kog „nije mereno" stoji ispisano umesto nule: ćutanje se
        // čita kao „ne postoji", a istina je „još se ne zna".
        Text(
            text = stringResource(R.string.chart_too_few, points.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val best = points.minOf { it.first }
    val target = benchmark?.millisPerAttempt

    // Skala prati **podatke, ne nulu**. Sa nulom bi se sve između tri i pet
    // sekundi slepilo u gornju trećinu, a razlika koja se prati je baš ta —
    // pola sekunde po zadatku.
    val lowest = minOf(points.minOf { it.first }, target ?: Long.MAX_VALUE)
    val highest = maxOf(points.maxOf { it.first }, target ?: 0L)
    val margin = ((highest - lowest) * 0.15f).coerceAtLeast(500f)
    val bottom = (lowest - margin).coerceAtLeast(0f)
    val top = highest + margin

    val line = MaterialTheme.colorScheme.primary
    val bestColor = MaterialTheme.colorScheme.tertiary
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            fun y(millis: Long): Float =
                size.height * (1f - (millis - bottom) / (top - bottom))
            fun x(index: Int): Float =
                if (points.size == 1) 0f else size.width * index / (points.size - 1)

            target?.let {
                drawLine(
                    color = targetColor,
                    start = Offset(0f, y(it)),
                    end = Offset(size.width, y(it)),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }

            drawLine(
                color = bestColor,
                start = Offset(0f, y(best)),
                end = Offset(size.width, y(best)),
                strokeWidth = 2f
            )

            points.forEachIndexed { index, (millis, _) ->
                if (index == 0) return@forEachIndexed
                val (previous, _) = points[index - 1]
                drawLine(
                    color = line,
                    start = Offset(x(index - 1), y(previous)),
                    end = Offset(x(index), y(millis)),
                    strokeWidth = 4f
                )
            }

            // Puna tačka = sesija je stigla do tražene tačnosti; šuplja = nije.
            // Tako jedan grafik nosi i vreme i tačnost, bez druge ose.
            points.forEachIndexed { index, (millis, accuracy) ->
                val reachedAccuracy = benchmark == null || accuracy >= benchmark.minAccuracy
                drawCircle(
                    color = if (reachedAccuracy) line else surface,
                    radius = 7f,
                    center = Offset(x(index), y(millis))
                )
                if (!reachedAccuracy) {
                    drawCircle(
                        color = line,
                        radius = 7f,
                        center = Offset(x(index), y(millis)),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bez brojeva na osi grafik kaže samo „ide na dole", a razlika od pola
        // sekunde po zadatku je upravo ono zbog čega se gleda.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.chart_scale, format(bottom), format(top)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.chart_sessions, points.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Uz linije stoje i brojevi: bez njih se mora pogađati koliko iznose, a
        // kad najbolji padne ispod orijentira — što je dobra vest — dve gole
        // linije izgledaju kao da su zamenile mesta.
        Legend(
            bestColor = bestColor,
            bestLabel = format(best.toFloat()),
            targetColor = targetColor,
            targetLabel = target?.let { format(it.toFloat()) }
        )
    }
}

@Composable
private fun Legend(
    bestColor: Color,
    bestLabel: String,
    targetColor: Color,
    targetLabel: String?
) {
    Text(
        text = stringResource(R.string.chart_best, bestLabel),
        style = MaterialTheme.typography.labelSmall,
        color = bestColor
    )
    if (targetLabel != null) {
        Text(
            text = stringResource(R.string.chart_target, targetLabel),
            style = MaterialTheme.typography.labelSmall,
            color = targetColor
        )
    }
}

/** Sekunde sa jednom decimalom — milisekunde ovde nikome ništa ne znače. */
private fun format(millis: Float): String = String.format("%.1f", millis / 1000f)

private const val MIN_POINTS = 3
