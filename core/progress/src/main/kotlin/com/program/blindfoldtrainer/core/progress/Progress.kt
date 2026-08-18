package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally

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
     */
    val bySkill: Map<Skill, SkillTally> = emptyMap()
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
     */
    val weakestSkill: Skill?
        get() = bySkill.entries
            .filter { (_, tally) -> tally.attempted > 0 }
            .minByOrNull { (_, tally) -> tally.solved.toFloat() / tally.attempted }
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
            // Sesije upisane pre uvođenja veština nemaju razlaganje; one profil
            // ne pomeraju, umesto da ga razblaže nulama.
            bySkill = result.bySkill.entries.fold(bySkill) { profile, (skill, tally) ->
                profile + (skill to ((profile[skill] ?: SkillTally(0, 0)) + tally))
            }
        )
    }

    companion object {
        val EMPTY = ProgressSnapshot()
    }
}

/**
 * Sabira istoriju u snimak. **Redosled je bitan** — niz besprekornih sesija se
 * prekida prvom u kojoj ima greške, pa istorija mora stizati hronološki.
 */
fun Iterable<SessionResult>.toProgressSnapshot(): ProgressSnapshot =
    fold(ProgressSnapshot.EMPTY) { snapshot, result -> snapshot + result }
