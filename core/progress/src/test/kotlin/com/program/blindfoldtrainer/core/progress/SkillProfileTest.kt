package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Profil je zbir razlaganja iz sesija, i **razlaže se po prečkama**: isti
 * procenat uz tablu i bez nje nije isti podatak.
 */
class SkillProfileTest {

    private fun session(
        moduleId: ModuleId = ModuleId.GEOMETRY,
        attempted: Int = 10,
        solved: Int = 10,
        support: Support? = Support.FULL,
        bySkill: Map<Skill, SkillTally> = emptyMap()
    ) = SessionResult(
        moduleId = moduleId,
        difficulty = Difficulty.EASY,
        attempted = attempted,
        solved = solved,
        mistakes = attempted - solved,
        elapsedMillis = 60_000,
        bySkill = bySkill,
        support = support
    )

    @Test
    fun `profil se sabira kroz sesije, po preckama`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9))),
            session(
                support = Support.NONE,
                bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 5))
            )
        )

        val profile = history.toProgressSnapshot().bySkill.getValue(Skill.COORDINATES)

        assertEquals(SkillTally(20, 17), profile.at(Support.FULL))
        assertEquals(SkillTally(10, 5), profile.at(Support.NONE))
        assertEquals(30, profile.attempted)
    }

    /**
     * Sesija bez upisane prečke ne ulazi u profil. Prečka je deo podatka, ne
     * ukras — bez nje se ne zna koliko uspeh vredi, pa je bolje ne znati ništa
     * nego znati pogrešno.
     */
    @Test
    fun `sesija bez precke ne ulazi u profil`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(support = null, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 1)))
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(SkillTally(10, 8), snapshot.bySkill.getValue(Skill.COORDINATES).at(Support.FULL))
        assertEquals(10, snapshot.bySkill.getValue(Skill.COORDINATES).attempted)
        assertEquals(2, snapshot.sessions)
    }

    @Test
    fun `sesija bez razlaganja ne razblazuje profil`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(attempted = 20, solved = 5)
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(10, snapshot.bySkill.getValue(Skill.COORDINATES).attempted)
        assertEquals(1, snapshot.bySkill.size)
    }

    /**
     * Prečka koju veština **drži** je najteža na kojoj ima dovoljno pokušaja i
     * dovoljno tačno. Jedan srećan pogodak bez table nije dokaz.
     */
    @Test
    fun `drzana precka trazi i dovoljno pokusaja i dovoljno tacnosti`() {
        val solid = SkillProfile()
            .plus(Support.FULL, SkillTally(20, 19))
            .plus(Support.NONE, SkillTally(10, 9))

        assertEquals(Support.NONE, solid.heldRung())

        // Bez table je probano, ali premalo — drži se i dalje samo uz tablu.
        val shaky = SkillProfile()
            .plus(Support.FULL, SkillTally(20, 19))
            .plus(Support.NONE, SkillTally(2, 2))

        assertEquals(Support.FULL, shaky.heldRung())

        // Ima pokušaja, ali tačnost ne drži ni na jednoj prečki.
        val failing = SkillProfile().plus(Support.FULL, SkillTally(20, 5))
        assertNull(failing.heldRung())
    }

    /**
     * Ovo je razlog zbog kog se prečka uopšte upisuje: bez nje bi onaj ko sve
     * radi uz punu podršku izgledao jači od onoga ko se muči bez table.
     */
    @Test
    fun `precka vredi vise od procenta pri poredjenju`() {
        val comfortable = SkillProfile().plus(Support.FULL, SkillTally(20, 20))
        val harder = SkillProfile().plus(Support.NONE, SkillTally(20, 14))

        assertTrue(
            "vežba bez table mora da stoji bolje od savršene uz tablu",
            harder.standing > comfortable.standing
        )
    }

    @Test
    fun `najslabija vestina se meri po tome gde je postignuta`() {
        val history = listOf(
            session(support = Support.NONE, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 7))),
            session(support = Support.FULL, bySkill = mapOf(Skill.POSITION_UPDATE to SkillTally(10, 8)))
        )

        // Ažuriranje ima bolji procenat, ali samo uz tablu — koordinate stoje
        // bolje jer su postignute na težoj prečki.
        assertEquals(Skill.POSITION_UPDATE, history.toProgressSnapshot().weakestSkill)
    }

    @Test
    fun `nemereno nije slabost`() {
        val empty = ProgressSnapshot.EMPTY

        assertNull(empty.weakestSkill)
        assertTrue(empty.measuredSkills.isEmpty())
    }
}
