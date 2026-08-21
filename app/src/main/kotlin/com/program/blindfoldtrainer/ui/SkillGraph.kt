package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import com.program.blindfoldtrainer.R
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.requires
import com.program.blindfoldtrainer.core.model.skillFloors
import com.program.blindfoldtrainer.core.progress.SkillStage
import kotlin.math.abs

/**
 * Slika zavisnosti među veštinama.
 *
 * **Crta se iz [Skill.requires], ne prepisuje.** Ista veza koja odlučuje šta će
 * put predložiti odlučuje i šta se ovde vidi — pa slika ne može da laže ni kad se
 * doda veština, ni kad se veza promeni.
 *
 * Četiri odluke je čine čitljivom, i sve četiri su naučene sa uređaja — prva
 * verzija je bila splet ukrštenih dijagonala, druga je imala dve grane stopljene
 * u jednu podebljanu liniju:
 *
 * - **nijedna grana ne preskače sprat** — o tome vodi računa `skillFloors`;
 * - **grane idu pod pravim uglom**, dole pa vodoravno pa dole. Dijagonala se na
 *   raskrsnici ne razlikuje od susedne, pravi ugao se prati okom;
 * - **red se slaže po roditeljima** — veština staje iznad proseka onih na koje
 *   se oslanja, pa broj ukrštanja sam pada;
 * - **trake se dele po međuredu, ne po cilju** — inače dve grane iz različitih
 *   kutija dobiju skoro isti razmak i vodoravni delovi im se stope.
 *
 * Uz platno ide i opis istih tih grana rečima. Aplikacija koja ima režim bez
 * ekrana ne sme čitaču ekrana da ostavi praznu sliku.
 */
@Composable
fun SkillGraph(
    modifier: Modifier = Modifier,
    /**
     * Dokle je koja veština stigla.
     *
     * Slika je dotle pokazivala **samo redosled gradnje** — šta na čemu stoji —
     * a ne i gde se ti nalaziš u njoj. To je bila i najveća rupa: stablo je
     * objašnjavalo tuđu teoriju umesto tvog stanja.
     *
     * Prazna mapa daje sliku kakva je i bila, pa uputstvo radi i pre nego što
     * ijedna sesija postoji.
     */
    levels: Map<Skill, SkillStage> = emptyMap()
) {
    val floors = remember { orderedFloors() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val edgeColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val onMasteredColor = MaterialTheme.colorScheme.onPrimary
    val fills = stageFills()

    val labels = Skill.entries.associateWith { stringResource(it.labelRes()) }

    // Isti podatak, rečima: „Ažuriranje pozicije — stoji na: Držanje pozicije,
    // Domet figure." `map` je inline pa sme da zove stringResource, `joinToString`
    // nije, pa se redovi prvo pokupe a tek onda spoje.
    val spokenRows = Skill.entries.map { skill ->
        val needs = skill.requires.map { labels.getValue(it) }
        val base = if (needs.isEmpty()) {
            "${labels.getValue(skill)} — ${stringResource(R.string.guide_deps_root)}"
        } else {
            "${labels.getValue(skill)} — " +
                stringResource(R.string.guide_deps_needs, needs.joinToString())
        }
        // Boja je za oko; onome ko sliku sluša isto stanje mora stići rečima.
        val stage = levels[skill]?.let { stringResource(it.labelRes()) }
        if (stage == null) base else "$base ($stage)"
    }
    val spoken = spokenRows.joinToString(". ")

    val boxHeight = 54.dp
    val rowGap = 44.dp
    val totalHeight = boxHeight * floors.size + rowGap * (floors.size - 1)

    val boxHeightPx = with(density) { boxHeight.toPx() }
    val rowGapPx = with(density) { rowGap.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }
    val cornerPx = with(density) { 10.dp.toPx() }
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
        // Sve kutije su **iste širine**, po najgušćem redu, a redovi se centriraju.
        // Da se širina delila po redu, sprat sa jednom veštinom dobio bi kutiju
        // preko celog ekrana i izgledao kao naslov umesto kao čvor.
        val widest = floors.maxOf { it.size }
        val boxWidth = (size.width - gapPx * (widest + 1)) / widest

        val places = HashMap<Skill, Rect>()
        floors.forEachIndexed { row, skills ->
            val rowWidth = skills.size * boxWidth + (skills.size - 1) * gapPx
            val left = (size.width - rowWidth) / 2f
            val top = row * (boxHeightPx + rowGapPx)
            skills.forEachIndexed { column, skill ->
                places[skill] = Rect(
                    Offset(left + column * (boxWidth + gapPx), top),
                    Size(boxWidth, boxHeightPx)
                )
            }
        }

        // Grane prvo, kutije preko njih: tako vodoravni deo grane koja ide daleko
        // ustranu ne prolazi preko tuđeg imena.
        //
        // Trake se dele **po međuredu, ne po cilju.** Kad je svaka grana sama
        // birala traku prema tome koji je po redu roditelj svog cilja, dve grane
        // iz različitih kutija dobijale su skoro isti razmak i vodoravni delovi
        // su se stapali u jednu podebljanu liniju.
        floors.forEachIndexed { row, skills ->
            if (row == 0) return@forEachIndexed

            val edges = skills.flatMap { skill ->
                val target = places.getValue(skill)
                skill.requires.mapNotNull { need -> places[need]?.let { it to target } }
            }

            // Grana pravo naniže nema vodoravni deo, pa joj traka ni ne treba —
            // a i ne sme da je zauzme, jer bi ostalima ostalo manje mesta.
            val (straight, bent) = edges.partition { (from, target) ->
                abs(from.center.x - target.center.x) < 1f
            }

            straight.forEach { (from, target) ->
                drawLine(
                    edgeColor, Offset(from.center.x, from.bottom),
                    Offset(target.center.x, target.top), strokePx
                )
            }

            // Najduža grana dobija traku najbliže polazištu: kraće onda prolaze
            // ispod nje umesto da je seku po sredini.
            val spread = bent.sortedByDescending { (from, target) ->
                abs(from.center.x - target.center.x)
            }

            spread.forEachIndexed { index, (from, target) ->
                val lane = 0.25f + 0.5f * (index + 1) / (spread.size + 1)
                val channel = from.bottom + (target.top - from.bottom) * lane

                drawLine(
                    edgeColor, Offset(from.center.x, from.bottom),
                    Offset(from.center.x, channel), strokePx
                )
                drawLine(
                    edgeColor, Offset(from.center.x, channel),
                    Offset(target.center.x, channel), strokePx
                )
                drawLine(
                    edgeColor, Offset(target.center.x, channel),
                    Offset(target.center.x, target.top), strokePx
                )
            }

            // Tačka na dolasku umesto strelice: na ovoj veličini je vrh strelice
            // mrlja, a smer se ionako čita iz toga što grane uvek idu naniže.
            edges.forEach { (_, target) ->
                drawCircle(edgeColor, strokePx * 2f, Offset(target.center.x, target.top))
            }
        }

        places.forEach { (skill, rect) ->
            // Boja raste sa stanjem, pa se slika čita jednim pogledom: bledo je
            // ono što još nije dodirnuto, puno je ono što je savladano.
            val stage = levels[skill]

            drawRoundRect(
                color = fills.getValue(stage ?: SkillStage.UNTRIED),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )

            // **Drugi kanal uz boju: debljina ivice.** Stanja se razlikuju i po
            // svetlini i po okviru, pa se slika čita i kad se boje ne razaznaju.
            val border = when (stage) {
                SkillStage.STARTED -> strokePx
                SkillStage.HOLDING -> strokePx * 2f
                else -> 0f
            }
            if (border > 0f) {
                drawRoundRect(
                    color = edgeColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = border)
                )
            }

            val text = measurer.measure(
                // Kvačica uz ime je **treći kanal**, i nosi ga baš stanje kome je
                // najviše stalo da se prepozna. Bez nje bi „savladano" zavisilo
                // samo od toga koliko je kutija tamnija od susedne.
                text = if (stage == SkillStage.MASTERED) {
                    "${labels.getValue(skill)}  ✓"
                } else {
                    labels.getValue(skill)
                },
                // Savladano je puna boja, pa tekst na njoj mora da promeni svoju —
                // inače se ime izgubi baš na veštini koju treba da pohvali.
                style = if (stage == SkillStage.MASTERED) {
                    labelStyle.copy(color = onMasteredColor)
                } else {
                    labelStyle
                },
                constraints = Constraints(
                    maxWidth = (rect.width - gapPx).toInt().coerceAtLeast(1)
                )
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

/**
 * Spratovi iz `skillFloors`, ali sa **redom unutar sprata**.
 *
 * Veština se stavlja iznad proseka onih na koje se oslanja — poznat potez za
 * smanjenje ukrštanja. Ona koja u spratu iznad nema nijednog roditelja ide na
 * kraj reda, jer je slobodna a svaka druga nije.
 *
 * Red je stvar crtanja, ne modela, pa stoji ovde a ne uz `skillFloors`.
 */
private fun orderedFloors(): List<List<Skill>> {
    val ordered = mutableListOf<List<Skill>>()
    var above: List<Skill> = emptyList()

    skillFloors().forEach { floor ->
        val row = if (above.isEmpty()) {
            floor
        } else {
            // `sortedBy` je stabilan, pa veštine sa istim prosekom zadržavaju
            // redosled deklaracije umesto da skakuću između dva crtanja.
            floor.sortedBy { skill ->
                val columns = skill.requires.map { above.indexOf(it) }.filter { it >= 0 }
                if (columns.isEmpty()) Float.MAX_VALUE else columns.average().toFloat()
            }
        }
        ordered += row
        above = row
    }

    return ordered
}

/**
 * Boja kutije po stanju — **jedna boja, četiri svetline**.
 *
 * Prvo su stanja bila u različitim bojama (druga, treća, primarna), i to je sa
 * uređaja stiglo nazad kao neupotrebljivo: dve od njih se ne razlikuju svakome.
 * Oko osam odsto muškaraca slabije razlikuje boje, i to se ne popravlja biranjem
 * „boljih" nijansi.
 *
 * Zato se hue više ne koristi kao podatak. Nosi ga **svetlina**, koja se vidi bez
 * obzira na vrstu daltonizma, a uz nju idu još dva kanala: **debljina ivice** i
 * **kvačica** na savladanom. Ko boje razaznaje, vidi isto što i pre; ko ne, i
 * dalje čita sliku.
 */
@Composable
private fun stageFills(): Map<SkillStage, Color> {
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.surfaceVariant
    return mapOf(
        SkillStage.NOT_MEASURED to idle,
        SkillStage.UNTRIED to idle,
        SkillStage.STARTED to accent.copy(alpha = 0.30f),
        SkillStage.HOLDING to accent.copy(alpha = 0.65f),
        SkillStage.MASTERED to accent
    )
}

/**
 * Šta koja boja na slici znači.
 *
 * Bez ovoga su boje ukras: čovek vidi da su neke kutije tamnije, ali ne zna je
 * li to bolje ili gore. Legenda stoji **odmah ispod slike**, jer se gleda uz nju
 * a ne posle nje.
 *
 * „Nije mereno" se ne pokazuje — to nije stanje tvoje veštine nego rupa u onome
 * što aplikacija nudi, i objašnjeno je tekstom ispod.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillGraphLegend(modifier: Modifier = Modifier) {
    val fills = stageFills()
    val entries = listOf(
        SkillStage.UNTRIED,
        SkillStage.STARTED,
        SkillStage.HOLDING,
        SkillStage.MASTERED
    ).map { it to fills.getValue(it) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { (stage, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    // Kvačica stoji i u legendi, da se veza sa slikom vidi odmah.
                    text = if (stage == SkillStage.MASTERED) {
                        "${stringResource(stage.labelRes())} ✓"
                    } else {
                        stringResource(stage.labelRes())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Stanje veštine rečima — za čitač ekrana i za legendu ispod slike. */
@StringRes
internal fun SkillStage.labelRes(): Int = when (this) {
    SkillStage.NOT_MEASURED -> R.string.progress_not_measured
    SkillStage.UNTRIED -> R.string.stage_untried
    SkillStage.STARTED -> R.string.stage_started
    SkillStage.HOLDING -> R.string.stage_holding_short
    SkillStage.MASTERED -> R.string.stage_mastered
}
