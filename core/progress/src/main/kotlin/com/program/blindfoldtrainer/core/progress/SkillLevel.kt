package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
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
        if (profile.byTask[spec.id] == null) return@flatMap emptyList()
        spec.supports.filter { rung -> holdsAt(skill, spec, rung) }
    }.maxByOrNull { it.ordinal }

    return when {
        held == null -> SkillLevel(SkillStage.STARTED, ceiling = ceiling)
        held == ceiling -> SkillLevel(SkillStage.MASTERED, holds = held, ceiling = ceiling)
        else -> SkillLevel(SkillStage.HOLDING, holds = held, ceiling = ceiling)
    }
}

/**
 * Da li se orijentir drži **sada**, po skorašnjim pokušajima.
 *
 * `TaskProfile.hasReached` gleda **sve od pamtiveka**, i to je za trajni zbir
 * ispravno — ali kao nivo bi značilo da se savladano jednom osvoji zauvek.
 * Naslepo vene brže nego što se stiče: ko je pre dva meseca držao bez table, a
 * otad ne, i dalje bi na ekranu stajao kao savladan.
 *
 * Prozor je **po broju pokušaja, ne po danima** — isto pravilo po kom se računa i
 * trend. „Poslednjih deset dana" je prazno kod onoga ko vežba dvaput nedeljno, a
 * baš njemu mera najviše treba. Uzima se onoliko poslednjih sesija koliko treba
 * da se skupi [WINDOW_ATTEMPTS] pokušaja; ispod [MIN_ATTEMPTS] se ne tvrdi ništa.
 */
fun ProgressSnapshot.holdsAt(skill: Skill, task: TaskSpec, rung: Support): Boolean {
    val benchmark = task.benchmarkFor(rung) ?: return false

    var attempted = 0
    var solved = 0
    var millis = 0L

    for (entry in sessionsFor(skill, task.id, rung).asReversed()) {
        attempted += entry.tally.attempted
        solved += entry.tally.solved
        millis += entry.tally.millis
        if (attempted >= WINDOW_ATTEMPTS) break
    }

    if (attempted < MIN_ATTEMPTS) return false

    val perAttempt = millis / attempted
    val accuracy = solved.toFloat() / attempted

    return perAttempt <= benchmark.millisPerAttempt && accuracy >= benchmark.minAccuracy
}

/**
 * Veštine koje su **savladane a zapuštene** — vreme im je da se potvrde.
 *
 * Savladano nije završeno. Bez ovoga bi put uvek išao ka najslabijem i tiho
 * puštao da najjače propada, a to bi se otkrilo tek na pravoj partiji.
 *
 * Ovde se **dani ipak broje**, i to je jedino mesto gde smeju: pitanje nije
 * koliko si dobro radio nego koliko dugo nisi. Za to pokušaji ne kažu ništa — ko
 * mesec dana nije dodirnuo veštinu nema ni jedan pokušaj da se izbroji.
 *
 * Vraća zapuštene veštine, od **najduže zapuštene** naniže.
 */
fun ProgressSnapshot.staleMastery(
    tasks: List<TaskSpec>,
    nowMillis: Long,
    afterDays: Int = UPKEEP_DAYS
): List<Skill> = Skill.entries
    .mapNotNull { skill ->
        val level = levelOf(skill, tasks)
        if (level.stage != SkillStage.MASTERED) return@mapNotNull null

        val ceiling = level.ceiling ?: return@mapNotNull null
        val last = tasks.filter { it.measures == skill }
            .flatMap { sessionsFor(skill, it.id, ceiling) }
            .maxOfOrNull { it.atMillis } ?: return@mapNotNull null

        val idleDays = (nowMillis - last) / DAY_MILLIS
        if (idleDays < afterDays) null else skill to idleDays
    }
    .sortedByDescending { (_, idleDays) -> idleDays }
    .map { (skill, _) -> skill }

/** Koliko se pokušaja gleda unazad kad se pita drži li se orijentir sada. */
private const val WINDOW_ATTEMPTS = 16

/** Ispod ovoliko pokušaja se ne tvrdi ni da drži ni da ne drži. */
private const val MIN_ATTEMPTS = 8

/**
 * Posle koliko dana savladana veština traži potvrdu.
 *
 * Prvi predlog, kao i orijentiri. Dovoljno dugo da se ne upada u proveru posle
 * svake druge sesije, dovoljno kratko da se propadanje uhvati pre partije.
 */
const val UPKEEP_DAYS = 10

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

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
fun ProgressSnapshot.practiceFor(
    skill: Skill,
    tasks: List<TaskSpec>,
    difficultiesFor: (String) -> List<Difficulty> = { Difficulty.entries }
): PracticeStep? {
    val measuring = tasks.filter { it.measures == skill }.ifEmpty { return null }
    val profile = bySkill[skill]

    val chosen = measuring.minByOrNull { spec ->
        profile?.byTask?.get(spec.id)?.standing ?: UNTOUCHED_FIRST
    } ?: return null

    val support = nextRungFor(chosen)

    return PracticeStep(
        task = chosen,
        support = support,
        difficulty = nextStepFor(chosen, support, difficultiesFor(chosen.id)),
        others = measuring.filterNot { it.id == chosen.id }
    )
}

/**
 * Šta tačno da otvoriš da bi ovu veštinu pomerio.
 *
 * Nosi i **težinu**, ne samo zadatak i oslonac. Bez nje bi je pozivalac ukucao —
 * a to je već jednom napravljeno na kartici Predloga, gde je svakoga ko
 * sluša predlog slalo na najlakšu. Pravilo za težinu stoji na jednom mestu i odavde se zove.
 */
data class PracticeStep(
    val task: TaskSpec,
    val support: Support,
    /** `null` kad modul težine ne nudi. */
    val difficulty: Difficulty?,
    /**
     * Ostali zadaci koji istu veštinu **mere**.
     *
     * Predlog ostaje **jedan** — spisak od tri jednako dobre opcije nije pomoć
     * nego odlaganje. Ali ni prećutati ih ne valja: ko je „Boju polja" odradio
     * triput danas ima pravo da zna da isto gradi i „Domet na liniji", umesto da
     * misli da za tu veštinu postoji samo jedna vežba.
     */
    val others: List<TaskSpec> = emptyList()
)

/** Neprobano ide pre svega merenog: o njemu se ne zna ništa, a to je vrednije. */
private const val UNTOUCHED_FIRST = -1f
