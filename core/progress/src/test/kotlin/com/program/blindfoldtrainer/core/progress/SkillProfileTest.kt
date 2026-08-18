package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Profil je zbir razlaganja iz sesija i jedino mesto odakle se zna šta je
 * korisniku jako a šta slabo.
 */
class SkillProfileTest {

    private fun session(
        moduleId: ModuleId = ModuleId.GEOMETRY,
        attempted: Int = 10,
        solved: Int = 10,
        bySkill: Map<Skill, SkillTally> = emptyMap()
    ) = SessionResult(
        moduleId = moduleId,
        difficulty = Difficulty.EASY,
        attempted = attempted,
        solved = solved,
        mistakes = attempted - solved,
        elapsedMillis = 60_000,
        bySkill = bySkill
    )

    @Test
    fun `profil se sabira kroz sesije`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9))),
            session(
                moduleId = ModuleId.FOLLOW_GAME,
                bySkill = mapOf(Skill.POSITION_UPDATE to SkillTally(6, 3))
            )
        )

        val profile = history.toProgressSnapshot().bySkill

        assertEquals(SkillTally(20, 17), profile[Skill.COORDINATES])
        assertEquals(SkillTally(6, 3), profile[Skill.POSITION_UPDATE])
    }

    /**
     * Sesije upisane pre uvođenja veština nemaju razlaganje. One profil **ne
     * pomeraju** — ni na gore ni na dole — umesto da ga razblaže nulama.
     */
    @Test
    fun `sesija bez razlaganja ne razblazuje profil`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(attempted = 20, solved = 5)
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(SkillTally(10, 8), snapshot.bySkill[Skill.COORDINATES])
        assertEquals(1, snapshot.bySkill.size)
        assertEquals(2, snapshot.sessions)
    }

    @Test
    fun `najslabija vestina je ona sa najnizim ucinkom`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9))),
            session(bySkill = mapOf(Skill.POSITION_UPDATE to SkillTally(10, 4))),
            session(bySkill = mapOf(Skill.NOTATION to SkillTally(10, 7)))
        )

        assertEquals(Skill.POSITION_UPDATE, history.toProgressSnapshot().weakestSkill)
    }

    /**
     * Nemerena veština se ne vraća kao najslabija. „Ne zna se da je slaba" i
     * „zna se da je slaba" su dve različite stvari; mešanje bi poslalo korisnika
     * da popravlja ono o čemu nemamo nijedan podatak.
     */
    @Test
    fun `nemereno nije slabost`() {
        val empty = ProgressSnapshot.EMPTY

        assertNull(empty.weakestSkill)
        assertTrue(empty.measuredSkills.isEmpty())

        val onlyOne = listOf(session(bySkill = mapOf(Skill.COORDINATES to SkillTally(4, 4))))
            .toProgressSnapshot()

        assertEquals(Skill.COORDINATES, onlyOne.weakestSkill)
        assertEquals(setOf(Skill.COORDINATES), onlyOne.measuredSkills)
    }
}
