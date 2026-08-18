package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.requires
import com.program.blindfoldtrainer.core.model.skillFloors

/**
 * Slika zavisnosti među veštinama.
 *
 * **Crta se iz [Skill.requires], ne prepisuje.** Ista veza koja odlučuje šta će
 * put predložiti odlučuje i šta se ovde vidi — pa slika ne može da laže ni kad se
 * doda veština, ni kad se veza promeni.
 *
 * Sprat veštine je **najduži put do korena**, ne najkraći: veština se crta ispod
 * svih svojih temelja, jer bi je najkraći put povukao gore i onda bi strelica
 * išla unazad.
 *
 * Slepima i ovo mora nešto da znači, pa uz platno ide i opis istih tih grana
 * rečima — isto izvedeno, ne otkucano.
 */
@Composable
fun SkillGraph(
    modifier: Modifier = Modifier,
    /** Veštine koje treba istaći — za sada nijedna; kasnije: šta je automatsko. */
    highlight: Set<Skill> = emptySet()
) {
    val layers = remember { skillFloors() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val boxColor = MaterialTheme.colorScheme.surfaceVariant
    val strongColor = MaterialTheme.colorScheme.primaryContainer
    val edgeColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface

    val labels = Skill.entries.associateWith { stringResource(it.labelRes()) }

    // Isti podatak, rečima: „Ažuriranje pozicije stoji na: Držanje pozicije,
    // Domet figure." Bez ovoga je platno za čitač ekrana prazna slika.
    // `map` je inline pa sme da zove stringResource; `joinToString` nije, pa se
    // redovi prvo pokupe a tek onda spoje.
    val spokenRows = Skill.entries.map { skill ->
        val needs = skill.requires.map { labels.getValue(it) }
        if (needs.isEmpty()) {
            "${labels.getValue(skill)} — ${stringResource(R.string.guide_deps_root)}"
        } else {
            "${labels.getValue(skill)} — " +
                stringResource(R.string.guide_deps_needs, needs.joinToString())
        }
    }
    val spoken = spokenRows.joinToString(". ")

    val boxHeight = 46.dp
    val rowGap = 30.dp
    val totalHeight = boxHeight * layers.size + rowGap * (layers.size - 1)

    val boxHeightPx = with(density) { boxHeight.toPx() }
    val rowGapPx = with(density) { rowGap.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 8.dp.toPx() }
    val strokePx = with(density) { 1.5.dp.toPx() }

    val labelStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = textColor,
        textAlign = TextAlign.Center
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .semantics { contentDescription = spoken }
    ) {
        // Prvo mesta, pa tek onda crtanje: grane se povlače između pravougaonika
        // koji još nisu nacrtani, a pravougaonici idu preko njih da grana koja
        // preskače sprat ne prolazi kroz tuđe ime.
        val places = HashMap<Skill, Rect>()
        layers.forEachIndexed { row, skills ->
            val boxWidth = (size.width - gapPx * (skills.size + 1)) / skills.size
            val top = row * (boxHeightPx + rowGapPx)
            skills.forEachIndexed { column, skill ->
                val left = gapPx + column * (boxWidth + gapPx)
                places[skill] = Rect(Offset(left, top), Size(boxWidth, boxHeightPx))
            }
        }

        places.forEach { (skill, rect) ->
            skill.requires.forEach { need ->
                val from = places[need] ?: return@forEach
                drawLine(
                    color = edgeColor,
                    start = Offset(from.center.x, from.bottom),
                    end = Offset(rect.center.x, rect.top),
                    strokeWidth = strokePx
                )
                // Tačka na dolasku umesto strelice: na ovoj veličini je vrh
                // strelice mrlja, a smer se ionako čita iz toga što grane uvek
                // idu naniže.
                drawCircle(
                    color = edgeColor,
                    radius = strokePx * 2f,
                    center = Offset(rect.center.x, rect.top)
                )
            }
        }

        places.forEach { (skill, rect) ->
            val filled = skill in highlight
            drawRoundRect(
                color = if (filled) strongColor else boxColor,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
            )
            if (filled) {
                drawRoundRect(
                    color = edgeColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )
            }

            val text = measurer.measure(
                text = labels.getValue(skill),
                style = labelStyle,
                constraints = Constraints(maxWidth = (rect.width - gapPx).toInt().coerceAtLeast(1))
            )
            drawText(
                textLayoutResult = text,
                topLeft = Offset(
                    x = rect.center.x - text.size.width / 2f,
                    y = rect.center.y - text.size.height / 2f
                )
            )
        }
    }
}
