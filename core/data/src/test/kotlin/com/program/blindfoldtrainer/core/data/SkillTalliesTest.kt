package com.program.blindfoldtrainer.core.data

import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Razlaganje po veštinama putuje u bazu kao tekst.
 *
 * Tekst je izabran iz istog razloga iz kog su ključ modula i ime težine tekst:
 * nova veština ne sme da pomeri značenje već upisanih redova. Cena je što se
 * pisanje i čitanje moraju držati zajedno — otud ovaj test.
 */
class SkillTalliesTest {

    @Test
    fun `upisano se procita isto`() {
        val tallies = mapOf(
            Skill.COORDINATES to SkillTally(attempted = 10, solved = 8),
            Skill.POSITION_HOLD to SkillTally(attempted = 5, solved = 4)
        )

        assertEquals(tallies, tallies.toStored().toSkillTallies())
    }

    @Test
    fun `prazno ostaje prazno u oba smera`() {
        assertEquals("", emptyMap<Skill, SkillTally>().toStored())
        assertEquals(emptyMap<Skill, SkillTally>(), "".toSkillTallies())
        assertEquals(emptyMap<Skill, SkillTally>(), "   ".toSkillTallies())
    }

    /**
     * Nepoznata veština je **očekivan** slučaj: istorija sme da pominje veštinu
     * koja je u međuvremenu preimenovana. Takav unos otpada, ostatak ostaje —
     * ista logika po kojoj nepoznat modul ne obara ceo napredak.
     */
    @Test
    fun `nepoznata vestina otpada, ostatak prezivi`() {
        val stored = "coordinates:10/8;telepatija:5/5;position_hold:4/4"
        val read = stored.toSkillTallies()

        assertEquals(2, read.size)
        assertEquals(SkillTally(10, 8), read[Skill.COORDINATES])
        assertEquals(SkillTally(4, 4), read[Skill.POSITION_HOLD])
    }

    @Test
    fun `ostecen zapis ne obara citanje`() {
        val stored = "coordinates:10/8;bez_dvotacke;notation:x/y;recovery:3/9;position_hold:2/1"
        val read = stored.toSkillTallies()

        // „recovery:3/9" ima više rešenih nego pokušanih — SkillTally to odbija,
        // pa unos otpada umesto da izuzetak obori celu istoriju.
        assertTrue(Skill.RECOVERY !in read)
        assertEquals(SkillTally(10, 8), read[Skill.COORDINATES])
        assertEquals(SkillTally(2, 1), read[Skill.POSITION_HOLD])
    }
}
