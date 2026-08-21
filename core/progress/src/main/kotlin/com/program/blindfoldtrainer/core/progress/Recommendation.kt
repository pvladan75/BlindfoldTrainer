package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
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
    STRENGTH,

    /**
     * **Savladano a zapušteno** — ide se da se potvrdi da još stoji.
     *
     * Savladano nije završeno: naslepo vene brže nego što se stiče. Bez ovog
     * razloga bi put uvek išao ka najslabijem i tiho puštao da najjače propada, a
     * to bi se otkrilo tek na pravoj partiji.
     */
    UPKEEP
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
    /**
     * Težina, ili `null` kad je modul **ne nudi**.
     *
     * Do sada je predlog nosio prečku a težinu prećutkivao, pa je školjka
     * upisivala najlakšu — ko je slušao predlog, dobijao je najlakšu trećinu
     * svakog modula i to nigde nije pisalo. Prećutana odluka je i dalje odluka;
     * ovde se bar vidi.
     */
    val difficulty: Difficulty?,
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
 *
 * [nowMillis] služi samo za **održavanje**: jedino ono broji dane, jer pita
 * koliko dugo veština nije dodirnuta, a za to pokušaji ne kažu ništa.
 *
 * [difficultiesFor] kaže koje težine modul tog zadatka uopšte nudi. Stiže kao
 * funkcija a ne kao polje na [TaskSpec] jer težine deklariše **modul**, a ovaj
 * sloj module ne poznaje — i ne treba da ih poznaje. Zatečeno je „sve tri", što
 * je i ono što ugovor modula podrazumeva.
 */
fun ProgressSnapshot.recommend(
    tasks: List<TaskSpec>,
    lastTaskId: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
    difficultiesFor: (String) -> List<Difficulty> = { Difficulty.entries }
): Recommendation? {
    if (tasks.isEmpty()) return null

    val candidates = tasks.filterNot { it.id == lastTaskId }.ifEmpty { tasks }

    // **Održavanje ide pre svega ostalog.** Ne zato što je preče od najslabijeg,
    // nego zato što je jedino sa rokom: slabo mesto će sačekati sledeći put, a
    // zapušteno u međuvremenu propada dalje. Kad ničega zapuštenog nema — a to je
    // najveći deo vremena — pravilo se i ne oseti.
    upkeepFor(candidates, nowMillis)?.let { return it }

    // Orijentiri stižu iz istog spiska zadataka koji se i predlaže — bez njih se
    // ne zna šta je „brzo", pa ni šta je temelj koji drži.
    val benchmarks = Benchmarks.of(tasks)

    val wantsStrength = sessions > 0 && sessions % STRENGTH_EVERY == 0
    val chosen = if (wantsStrength) {
        candidates.maxByOrNull { standingOf(it) } ?: return null
    } else {
        candidates.minByOrNull { priorityOf(it, benchmarks) } ?: return null
    }

    // Prečka se bira prva, pa se težina bira **za nju**. Obrnuto se ne može:
    // orijentir po kom se meri uspeh visi o prečki, pa dok se ne zna prečka, ne
    // zna se ni šta je na njoj bio uspeh.
    val support = nextRung(chosen)

    return Recommendation(
        skill = chosen.measures,
        taskId = chosen.id,
        support = support,
        difficulty = nextStep(chosen, support, difficultiesFor(chosen.id)),
        // Redosled razloga nije proizvoljan: prvo ono što je **osnovnija
        // činjenica**. Da nisi ni probao je jače od svega ostalog što bi se o
        // tome moglo reći, pa ide ispred toga što je veština i temelj.
        reason = when {
            wantsStrength -> Reason.STRENGTH
            bySkill[chosen.measures] == null -> Reason.NEVER_TRIED
            isUnautomaticFoundation(chosen.measures, benchmarks) -> Reason.FOUNDATION
            else -> Reason.WEAKEST
        }
    )
}

/**
 * Predlog da se potvrdi **savladano a zapušteno**, ili `null` ako takvog nema.
 *
 * Ide se na **najtežu prečku** koju veština drži: potvrda na lakšoj ne dokazuje
 * ništa o onome što je tvrdnja. Ako padne, nivo sam spada na nižu prečku —
 * `levelOf` gleda skorašnje pokušaje, pa se ništa ne mora posebno „vraćati u
 * cilj"; veština se prosto opet nađe među slabima.
 */
private fun ProgressSnapshot.upkeepFor(
    candidates: List<TaskSpec>,
    nowMillis: Long
): Recommendation? {
    val skill = staleMastery(candidates, nowMillis).firstOrNull() ?: return null
    val ceiling = levelOf(skill, candidates).ceiling ?: return null

    val task = candidates.filter { it.measures == skill }
        .maxByOrNull { it.hardest.ordinal } ?: return null

    return Recommendation(
        skill = skill,
        taskId = task.id,
        support = task.nearestSupport(ceiling),
        // Težinu bira školjka po zatečenom pravilu: održavanje ne menja koliko
        // je vežba duga, nego samo šta se vežba.
        difficulty = null,
        reason = Reason.UPKEEP
    )
}

/**
 * Manje je preče. Neprobano ide prvo, pa ono što ništa ne koči, pa slabije.
 *
 * Veština čiji temelji nisu automatski se **ne zabranjuje** nego samo pomera
 * unazad: ako je sve ostalo pokriveno, i ona će doći na red.
 */
private fun ProgressSnapshot.priorityOf(task: TaskSpec, benchmarks: Benchmarks): Float {
    val skill = task.measures
    val blocked = if (foundationsMissing(skill, benchmarks).isEmpty()) 0f else BLOCKED_PENALTY

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
private fun ProgressSnapshot.isUnautomaticFoundation(
    skill: Skill,
    benchmarks: Benchmarks
): Boolean = !isAutomatic(skill, benchmarks) && Skill.entries.any { skill in it.requires }

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
internal fun ProgressSnapshot.nextRungFor(task: TaskSpec): Support = nextRung(task)

internal fun ProgressSnapshot.nextStepFor(
    task: TaskSpec,
    support: Support,
    offered: List<Difficulty>
): Difficulty? = nextStep(task, support, offered)

private fun ProgressSnapshot.nextRung(task: TaskSpec): Support {
    val history = skillHistory
        .filter { !it.isCheckup && it.taskId == task.id }
        .takeLast(2)

    if (history.isEmpty()) return task.supports.minByOrNull { it.ordinal } ?: Support.FULL

    val last = history.last()

    if (!last.meets(task)) return task.easierThan(last.support) ?: last.support

    val twiceOnSame = history.size == 2 &&
        history.all { it.support == last.support } &&
        history.all { it.meets(task) }

    if (!twiceOnSame) return last.support

    return task.harderThan(last.support) ?: last.support
}

/**
 * Težina za sledeći put, po istom pravilu kao prečka — ali **na toj prečki**.
 *
 * Dve lestvice se time ne penju uporedo, nego jedna po jedna. Kad prečka ima
 * kuda da se pomeri, težina zatekne praznu istoriju na novoj prečki i vrati se
 * na najlakšu; kad je prečka na kraju svoje lestvice, težina preuzima posao.
 * Zato „dvaput dobro" ne pomera obe stvari odjednom, što bi bio dvostruki skok.
 *
 * Nema izmišljene treće lestvice: prečka je i dalje prava mera težine za rad
 * naslepo, a ovo je samo ono što se unutar nje moglo skalirati a nije se.
 *
 * Prazna ponuda znači da modul težine **ne nudi** — tada nema šta da se bira i
 * vraća se `null`, umesto da se izmisli najlakša.
 */
private fun ProgressSnapshot.nextStep(
    task: TaskSpec,
    support: Support,
    offered: List<Difficulty>
): Difficulty? {
    val ladder = offered.sortedBy { it.ordinal }
    val easiest = ladder.firstOrNull() ?: return null

    val history = skillHistory
        .filter { !it.isCheckup && it.taskId == task.id && it.support == support }
        .takeLast(2)

    val last = history.lastOrNull() ?: return easiest

    // Težina koju modul više ne nudi se ne nasleđuje — pada na najlakšu, isto
    // kao što se prečka koju zadatak nema svodi na najbližu koju ume.
    val current = last.difficulty.takeIf { it in ladder } ?: easiest
    val step = ladder.indexOf(current)

    if (!last.meets(task)) return ladder.getOrNull(step - 1) ?: current

    val twiceOnSame = history.size == 2 &&
        history.all { it.difficulty == current } &&
        history.all { it.meets(task) }

    if (!twiceOnSame) return current

    return ladder.getOrNull(step + 1) ?: current
}

/**
 * Da li je ova sesija ispunila orijentir **svoje** prečke.
 *
 * Sesija bez ijednog pokušaja se ne računa kao uspeh: nula pokušaja nije dokaz
 * ni za ni protiv, a jedina druga mogućnost bi bila da prazna sesija gura
 * naviše.
 *
 * Gde zadatak nema orijentir za tu prečku, meri se praznom rukom —
 * [DEFAULT_SUCCESS] tačnosti.
 */
private fun SkillEntry.meets(task: TaskSpec): Boolean {
    if (tally.attempted == 0) return false
    val threshold = task.benchmarkFor(support)?.minAccuracy ?: DEFAULT_SUCCESS
    return tally.solved.toFloat() / tally.attempted >= threshold
}

/** Neprobano ide ispred svega merenog, ali iza ničega. */
private const val NEVER_TRIED_PRIORITY = -1f

/** Koliko se unazad pomera veština čiji temelji nisu automatski. */
private const val BLOCKED_PENALTY = 10f

/** Na svakih toliko sesija predlaže se ono što ide dobro. */
private const val STRENGTH_EVERY = 5

/** Kad zadatak nema orijentir za tu prečku, uspehom se smatra ovoliko. */
private const val DEFAULT_SUCCESS = 0.8f
