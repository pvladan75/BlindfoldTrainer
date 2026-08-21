package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec

/**
 * Dokle je veština stigla — **jedna lestvica, pet stanja**.
 *
 * Do sada su o istoj stvari govorila tri različita broja: nivo iz provere
 * (`4/5`), `standing` kao mešavina prečke i tačnosti, i orijentir po zadatku i
 * prečki. Svaki je merio nešto tačno, ali nijedan nije odgovarao na pitanje zbog
 * kog se Napredak otvara — **gde stojim i šta mi još fali**.
 *
 * Ovo je taj odgovor, i ne uvodi nov podatak: sve se izvodi iz orijentira koji
 * već postoje.
 */
enum class SkillStage {
    /** Nijedan zadatak je ne meri. Nije slabost nego rupa u ponudi. */
    NOT_MEASURED,

    /** Meri se, ali je nisi dodirnuo. */
    UNTRIED,

    /** Vežbana, ali orijentir još nigde nije dostignut. */
    STARTED,

    /** Orijentir dostignut na nekoj prečki — vidi [SkillLevel.holds]. */
    HOLDING,

    /**
     * Orijentir dostignut na **najtežoj prečki** koju ijedan njen zadatak nudi.
     *
     * Savladano nije završeno: naslepo vene brže nego što se stiče, pa odavde
     * veština ide u održavanje.
     */
    MASTERED
}

/**
 * Nivo veštine: **prečka koju drži**, i dokle uopšte može da dogura.
 *
 * Namerno bez procenta. Broj kao „73%" izgleda tačno a nije — sastavljen je od
 * nejednakih zadataka i pada od jedne loše večeri. Prečka je grublja i istinita.
 */
data class SkillLevel(
    val stage: SkillStage,
    /** Najteža prečka na kojoj je orijentir dostignut; `null` dok nijedna nije. */
    val holds: Support? = null,
    /** Najteža prečka koju veština uopšte može da dosegne; `null` ako je niko ne meri. */
    val ceiling: Support? = null
) {
    /** Koliko je prečki od dna do [holds], računajući i nju. Nula dok se ništa ne drži. */
    fun stepsTaken(rungs: List<Support>): Int =
        holds?.let { rungs.count { rung -> rung.ordinal <= it.ordinal } } ?: 0
}

/**
 * Sve prečke koje veština može da dosegne, od najlakše ka najtežoj.
 *
 * Unija prečki **zadataka koji je mere**. Zadaci koji je samo nose ne ulaze:
 * po njima se ne upisuje u profil, pa bi obećavali prečku do koje se ne može
 * doći vežbanjem baš te veštine.
 */
fun rungsFor(skill: Skill, tasks: List<TaskSpec>): List<Support> =
    tasks.filter { it.measures == skill }
        .flatMap { it.supports }
        .distinct()
        .sortedBy { it.ordinal }

/**
 * Nivo veštine, izveden iz orijentira.
 *
 * [tasks] je ceo spisak zadataka u aplikaciji; ovde se sam prosejava na one koji
 * ovu veštinu **mere**.
 */
fun ProgressSnapshot.levelOf(skill: Skill, tasks: List<TaskSpec>): SkillLevel {
    val measuring = tasks.filter { it.measures == skill }
    if (measuring.isEmpty()) return SkillLevel(SkillStage.NOT_MEASURED)

    val ceiling = measuring.maxByOrNull { it.hardest.ordinal }?.hardest
    val profile = bySkill[skill]
        ?: return SkillLevel(SkillStage.UNTRIED, ceiling = ceiling)

    // Prečka se drži ako je na njoj orijentir dostignut — u **bilo kom** zadatku
    // koji veštinu meri. Zadaci su različiti poslovi iste veštine; dovoljno je da
    // se na toj prečki pokaže u jednom, jer bi traženje svih kažnjavalo modul sa
    // više zadataka.
    val held = measuring.flatMap { spec ->
        val task = profile.byTask[spec.id] ?: return@flatMap emptyList()
        spec.supports.filter { rung ->
            val benchmark = spec.benchmarkFor(rung) ?: return@filter false
            task.hasReached(rung, benchmark)
        }
    }.maxByOrNull { it.ordinal }

    return when {
        held == null -> SkillLevel(SkillStage.STARTED, ceiling = ceiling)
        held == ceiling -> SkillLevel(SkillStage.MASTERED, holds = held, ceiling = ceiling)
        else -> SkillLevel(SkillStage.HOLDING, holds = held, ceiling = ceiling)
    }
}

/**
 * Kojim zadatkom i na kojoj prečki se ova veština **sad gradi**.
 *
 * Ovo je ono što je do sada znao samo Predlog, i to za jednu jedinu veštinu —
 * onu koju je sam izabrao. Ko na kartici veštine pročita da negde stoji slabo,
 * imao je pravo da odmah sazna i kuda po nju.
 *
 * Bira se zadatak sa **najslabijim stanjem** među onima koji veštinu mere, jer
 * on je i mesto na kom se najviše dobija. Prečka je ista ona koju bi put
 * poručio — pravilo se ne prepisuje nego zove.
 *
 * `null` ako veštinu ne meri nijedan zadatak.
 */
fun ProgressSnapshot.practiceFor(skill: Skill, tasks: List<TaskSpec>): Pair<TaskSpec, Support>? {
    val measuring = tasks.filter { it.measures == skill }.ifEmpty { return null }
    val profile = bySkill[skill]

    val chosen = measuring.minByOrNull { spec ->
        profile?.byTask?.get(spec.id)?.standing ?: UNTOUCHED_FIRST
    } ?: return null

    return chosen to nextRungFor(chosen)
}

/** Neprobano ide pre svega merenog: o njemu se ne zna ništa, a to je vrednije. */
private const val UNTOUCHED_FIRST = -1f
