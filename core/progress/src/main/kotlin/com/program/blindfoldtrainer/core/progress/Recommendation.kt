package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.requires
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec

/**
 * Zašto je baš ovo predloženo.
 *
 * Razlog je **obavezan deo predloga**, ne ukras: preporuka bez razloga je
 * proročanstvo, a proročanstvu se ne veruje kad promaši. Sa razlogom je argument
 * i sme da se odbije.
 */
enum class Reason {
    /** Veština koja još nije ni probana — istraživanje ide pre mlaćenja poznatog. */
    NEVER_TRIED,

    /** Najslabija među merenima. */
    WEAKEST,

    /**
     * **Ovo je temelj** za druge veštine, a još nije automatsko.
     *
     * Veza ide u ovom smeru namerno: razlog objašnjava zašto je vredno raditi
     * baš **ovo**, a ne zašto nešto drugo ne ide. „Koordinate su temelj" je
     * argument; „držanju pozicije fale temelji" je prigovor.
     */
    FOUNDATION,

    /** Ono što ide dobro — da predlog ne bude uvek najgore mesto. */
    STRENGTH
}

/**
 * Šta raditi sledeće: koja veština, kojim zadatkom i na kojoj prečki.
 *
 * **Predlog, ne šina.** Meni ostaje netaknut ispod njega, odbijanje nema
 * posledice, i ništa se ne zaključava — isto pravilo po kom rang ništa ne
 * otključava.
 */
data class Recommendation(
    val skill: Skill,
    val taskId: String,
    val support: Support,
    val reason: Reason
)

/**
 * Sledeći korak: **cilj iz onoga što se zna, korak iz onoga što se poslednje
 * dogodilo**.
 *
 * Redosled odlučivanja:
 *
 * 1. **Nikad dvaput isto zaredom** — zastoj se ne probija ponavljanjem, a ostale
 *    veštine u međuvremenu venu. Zadatak od prošlog puta se preskače, osim ako je
 *    jedini.
 * 2. **Povremeno ono što ide dobro** — na svakih [STRENGTH_EVERY] sesija.
 *    Preporuka koja uvek šalje na najgore je preporuka koja se prestane
 *    otvarati; uspeh je gorivo, ne nagrada.
 * 3. **Temelj pre nadgradnje** — veština čiji preduslovi nisu automatski ide
 *    iza onih koje ništa ne koči, jer je radna memorija jedna.
 * 4. **Neprobano pre slabog** — o neprobanom se ne zna ništa, a to je vrednije
 *    saznanje od još jedne potvrde da je nešto slabo.
 * 5. **Inače najslabije** — po [SkillProfile.standing], gde prečka vredi više od
 *    procenta.
 *
 * [lastTaskId] je zadatak poslednje **vežbe** (ne provere); `null` ako je vežbe
 * još nema.
 */
fun ProgressSnapshot.recommend(
    tasks: List<TaskSpec>,
    lastTaskId: String? = null
): Recommendation? {
    if (tasks.isEmpty()) return null

    val candidates = tasks.filterNot { it.id == lastTaskId }.ifEmpty { tasks }

    val wantsStrength = sessions > 0 && sessions % STRENGTH_EVERY == 0
    val chosen = if (wantsStrength) {
        candidates.maxByOrNull { standingOf(it) } ?: return null
    } else {
        candidates.minByOrNull { priorityOf(it) } ?: return null
    }

    return Recommendation(
        skill = chosen.measures,
        taskId = chosen.id,
        support = nextRung(chosen),
        // Redosled razloga nije proizvoljan: prvo ono što je **osnovnija
        // činjenica**. Da nisi ni probao je jače od svega ostalog što bi se o
        // tome moglo reći, pa ide ispred toga što je veština i temelj.
        reason = when {
            wantsStrength -> Reason.STRENGTH
            bySkill[chosen.measures] == null -> Reason.NEVER_TRIED
            isUnautomaticFoundation(chosen.measures) -> Reason.FOUNDATION
            else -> Reason.WEAKEST
        }
    )
}

/**
 * Manje je preče. Neprobano ide prvo, pa ono što ništa ne koči, pa slabije.
 *
 * Veština čiji temelji nisu automatski se **ne zabranjuje** nego samo pomera
 * unazad: ako je sve ostalo pokriveno, i ona će doći na red.
 */
private fun ProgressSnapshot.priorityOf(task: TaskSpec): Float {
    val skill = task.measures
    val blocked = if (foundationsMissing(skill).isEmpty()) 0f else BLOCKED_PENALTY

    val profile = bySkill[skill]?.byTask?.get(task.id)
        ?: return NEVER_TRIED_PRIORITY + blocked

    return profile.standing + blocked
}

/**
 * Da li je ova veština **temelj drugima**, a još nije automatska.
 *
 * To je najjači razlog koji preporuka ume da ponudi, jer ne govori o ovoj
 * veštini nego o svemu što na njoj stoji: dok temelj troši pažnju, iznad njega
 * se napreduje sporo ma koliko se vežbalo.
 */
private fun ProgressSnapshot.isUnautomaticFoundation(skill: Skill): Boolean =
    !isAutomatic(skill) && Skill.entries.any { skill in it.requires }

private fun ProgressSnapshot.standingOf(task: TaskSpec): Float =
    bySkill[task.measures]?.byTask?.get(task.id)?.standing ?: 0f

/**
 * Prečka za sledeći put, po onome što se poslednje dogodilo u tom zadatku.
 *
 * - **dva puta uspešno zaredom na istoj prečki → prečka niže** (manje pomoći);
 * - **promašaj → prečka nazad**, bez kazne i bez komentara;
 * - inače se ostaje gde se bilo.
 *
 * Uspeh se meri orijentirom te prečke kad ga ima; gde ga nema, praznom rukom —
 * [DEFAULT_SUCCESS] tačnosti.
 */
private fun ProgressSnapshot.nextRung(task: TaskSpec): Support {
    val history = skillHistory
        .filter { !it.isCheckup && it.taskId == task.id }
        .takeLast(2)

    if (history.isEmpty()) return task.supports.minByOrNull { it.ordinal } ?: Support.FULL

    val last = history.last()
    val threshold = task.benchmarkFor(last.support)?.minAccuracy ?: DEFAULT_SUCCESS
    val accuracy = if (last.tally.attempted == 0) {
        0f
    } else {
        last.tally.solved.toFloat() / last.tally.attempted
    }

    if (accuracy < threshold) {
        return task.easierThan(last.support) ?: last.support
    }

    val twiceOnSame = history.size == 2 &&
        history.all { it.support == last.support } &&
        history.all { entry ->
            val limit = task.benchmarkFor(entry.support)?.minAccuracy ?: DEFAULT_SUCCESS
            entry.tally.attempted > 0 &&
                entry.tally.solved.toFloat() / entry.tally.attempted >= limit
        }

    if (!twiceOnSame) return last.support

    return task.harderThan(last.support) ?: last.support
}

/** Neprobano ide ispred svega merenog, ali iza ničega. */
private const val NEVER_TRIED_PRIORITY = -1f

/** Koliko se unazad pomera veština čiji temelji nisu automatski. */
private const val BLOCKED_PENALTY = 10f

/** Na svakih toliko sesija predlaže se ono što ide dobro. */
private const val STRENGTH_EVERY = 5

/** Kad zadatak nema orijentir za tu prečku, uspehom se smatra ovoliko. */
private const val DEFAULT_SUCCESS = 0.8f
