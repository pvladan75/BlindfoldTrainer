package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support

/** Napredak u jednom modulu. */
data class ModuleProgress(
    val sessions: Int = 0,
    val attempted: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val perfectSessions: Int = 0,
    val xp: Int = 0,
    val timeMillis: Long = 0,
    /** Najbolji rezultat po težini, u poenima. Za „lični rekord" u meniju. */
    val bestXpByDifficulty: Map<Difficulty, Int> = emptyMap()
) {
    val accuracy: Float
        get() = if (attempted == 0) 0f else solved.toFloat() / attempted

    operator fun plus(result: SessionResult): ModuleProgress {
        val gained = Xp.forSession(result)
        val best = bestXpByDifficulty[result.difficulty] ?: 0
        return ModuleProgress(
            sessions = sessions + 1,
            attempted = attempted + result.attempted,
            solved = solved + result.solved,
            mistakes = mistakes + result.mistakes,
            perfectSessions = perfectSessions + if (result.isPerfect) 1 else 0,
            xp = xp + gained,
            timeMillis = timeMillis + result.elapsedMillis,
            bestXpByDifficulty = bestXpByDifficulty + (result.difficulty to maxOf(best, gained))
        )
    }
}

/**
 * Ceo napredak korisnika, sabran iz istorije sesija.
 *
 * Ovo je izvedena vrednost, ne zapis: čuva se samo sirova istorija, a snimak se
 * računa iz nje. Zato promena pravila bodovanja ne ostavlja nesaglasne podatke.
 */
data class ProgressSnapshot(
    val totalXp: Int = 0,
    val sessions: Int = 0,
    val attempted: Int = 0,
    val solved: Int = 0,
    val perfectSessions: Int = 0,
    val timeMillis: Long = 0,
    val byModule: Map<ModuleId, ModuleProgress> = emptyMap(),
    /** Besprekorne sesije po težini — dostignuća razlikuju lako od teškog. */
    val perfectByDifficulty: Map<Difficulty, Int> = emptyMap(),
    /** Koliko besprekornih sesija traje u nizu upravo sada. */
    val perfectStreak: Int = 0,
    /** Najduži takav niz do sada. */
    val bestPerfectStreak: Int = 0,

    /**
     * Profil: koliko je svaka veština vežbana i sa kojim uspehom.
     *
     * Zbir razlaganja iz sesija, i **jedino mesto** odakle se zna šta je
     * korisniku jako a šta slabo. Veština koje ovde nema **nije mereno** — ne
     * znači nula, nego da o njoj još ništa ne znamo.
     *
     * Razlaže se i **po prečkama**: isti procenat uz tablu i bez nje nije isti
     * podatak, pa se ne sme sabrati u jedan broj.
     */
    val bySkill: Map<Skill, SkillProfile> = emptyMap()
) {
    val rank: Rank get() = Rank.forXp(totalXp)
    val rankProgress: RankProgress get() = RankProgress.forXp(totalXp)

    /** Moduli koje je korisnik bar jednom probao. */
    val startedModules: Set<ModuleId> get() = byModule.keys

    /** Osvojena dostignuća. Izvedena su iz stanja, pa se nigde ne pamte. */
    val achievements: Set<Achievement> get() = Achievement.earnedIn(this)

    /** Veštine o kojima uopšte ima podatka. Ostale stoje kao „nije mereno". */
    val measuredSkills: Set<Skill> get() = bySkill.keys

    /**
     * Veština sa najslabijim učinkom, među **merenima**.
     *
     * Odavde kasnije kreće preporuka. Nemerena veština se namerno ne vraća: o
     * njoj se ne zna da je slaba, nego se ne zna ništa, a to su dve različite
     * stvari i ne smeju da se pomešaju.
     *
     * Poredi se učinak **na prečki koju veština drži** — inače bi onaj ko sve
     * radi uz punu podršku izgledao jači od onoga ko se muči bez table.
     */
    val weakestSkill: Skill?
        get() = bySkill.entries
            .filter { (_, profile) -> profile.attempted > 0 }
            .minByOrNull { (_, profile) -> profile.standing }
            ?.key

    operator fun plus(result: SessionResult): ProgressSnapshot {
        val module = byModule[result.moduleId] ?: ModuleProgress()
        val streak = if (result.isPerfect) perfectStreak + 1 else 0
        return ProgressSnapshot(
            totalXp = totalXp + Xp.forSession(result),
            sessions = sessions + 1,
            attempted = attempted + result.attempted,
            solved = solved + result.solved,
            perfectSessions = perfectSessions + if (result.isPerfect) 1 else 0,
            timeMillis = timeMillis + result.elapsedMillis,
            byModule = byModule + (result.moduleId to (module + result)),
            perfectByDifficulty = if (result.isPerfect) {
                perfectByDifficulty + (result.difficulty to (perfectByDifficulty[result.difficulty] ?: 0) + 1)
            } else {
                perfectByDifficulty
            },
            perfectStreak = streak,
            bestPerfectStreak = maxOf(bestPerfectStreak, streak),
            // Sesije bez razlaganja ili bez upisane prečke profil **ne pomeraju**.
            // Prečka je deo podatka, ne ukras: bez nje se ne zna koliko uspeh
            // vredi, pa je bolje ne znati ništa nego znati pogrešno.
            bySkill = result.support?.let { support ->
                result.bySkill.entries.fold(bySkill) { profile, (skill, tally) ->
                    val current = profile[skill] ?: SkillProfile()
                    profile + (skill to current.plus(support, tally))
                }
            } ?: bySkill
        )
    }

    companion object {
        val EMPTY = ProgressSnapshot()
    }
}

/**
 * Šta se o jednoj veštini zna — **po prečkama**.
 *
 * Nivo veštine je prečka koju drži, a ne procenat. Procenat izgleda tačno a
 * nije: sastavljen je od nejednakih zadataka, a uspeh uz punu podršku i uspeh
 * bez table nisu isti dokaz. Zato se čuvaju razdvojeno i sabiraju tek za prikaz.
 */
data class SkillProfile(val bySupport: Map<Support, SkillTally> = emptyMap()) {

    val attempted: Int get() = bySupport.values.sumOf { it.attempted }
    val solved: Int get() = bySupport.values.sumOf { it.solved }

    fun at(support: Support): SkillTally? = bySupport[support]

    /** Prečke na kojima je veština uopšte probana, od najlakše ka najtežoj. */
    val triedRungs: List<Support> get() = bySupport.keys.sortedBy { it.ordinal }

    /**
     * Najteža prečka koju veština **drži**.
     *
     * Drži je kad je na njoj bilo dovoljno pokušaja i dovoljno tačno. Jedan
     * srećan pogodak bez table nije dokaz, pa prag postoji — a jeste nizak, jer
     * je ovo merilo napretka a ne ispit.
     */
    fun heldRung(minAttempts: Int = 8, minAccuracy: Float = 0.8f): Support? =
        bySupport.entries
            .filter { (_, tally) ->
                tally.attempted >= minAttempts &&
                    tally.solved.toFloat() / tally.attempted >= minAccuracy
            }
            .maxByOrNull { (support, _) -> support.ordinal }
            ?.key

    /**
     * Jedan broj za poređenje veština, u kom **prečka vredi više od procenta**.
     *
     * Bez toga bi onaj ko sve radi uz punu podršku izgledao jači od onoga ko se
     * muči bez table — a upravo je ovaj drugi dalje odmakao.
     */
    val standing: Float
        get() {
            if (attempted == 0) return 0f
            return bySupport.entries.sumOf { (support, tally) ->
                val accuracy = if (tally.attempted == 0) 0.0 else tally.solved.toDouble() / tally.attempted
                (accuracy * (support.ordinal + 1) * tally.attempted)
            }.toFloat() / bySupport.values.sumOf { it.attempted }
        }

    internal fun plus(support: Support, tally: SkillTally): SkillProfile =
        SkillProfile(bySupport + (support to ((bySupport[support] ?: SkillTally(0, 0)) + tally)))
}

/**
 * Sabira istoriju u snimak. **Redosled je bitan** — niz besprekornih sesija se
 * prekida prvom u kojoj ima greške, pa istorija mora stizati hronološki.
 */
fun Iterable<SessionResult>.toProgressSnapshot(): ProgressSnapshot =
    fold(ProgressSnapshot.EMPTY) { snapshot, result -> snapshot + result }
