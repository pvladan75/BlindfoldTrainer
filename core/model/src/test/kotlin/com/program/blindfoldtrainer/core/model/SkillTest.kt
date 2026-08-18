package com.program.blindfoldtrainer.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillTest {

    private val squareColor = TaskSpec(
        id = "square_color",
        skills = listOf(Skill.COORDINATES),
        supports = listOf(Support.FULL, Support.NONE)
    )

    private val followMove = TaskSpec(
        id = "where_is_piece",
        skills = listOf(Skill.POSITION_UPDATE, Skill.POSITION_HOLD),
        supports = listOf(Support.FULL, Support.PARTIAL, Support.TRACE, Support.NONE)
    )

    @Test
    fun `podrska ide od najvise pomoci ka najmanjoj`() {
        assertEquals(Support.PARTIAL, Support.FULL.harder())
        assertEquals(Support.FULL, Support.PARTIAL.easier())

        assertEquals(null, Support.NONE.harder(), "ispod najteže nema ničega")
        assertEquals(null, Support.FULL.easier(), "iznad najlakše nema ničega")
    }

    @Test
    fun `zadatak meri prvu vestinu, ostale nosi uz nju`() {
        assertEquals(Skill.POSITION_UPDATE, followMove.measures)
        assertTrue(Skill.POSITION_HOLD in followMove.skills)
    }

    @Test
    fun `najniza precka je najteza koju zadatak ume`() {
        assertEquals(Support.NONE, squareColor.hardest)
        assertEquals(
            Support.TRACE,
            TaskSpec("x", listOf(Skill.NOTATION), listOf(Support.FULL, Support.TRACE)).hardest
        )
    }

    /**
     * Kad tražene prečke nema, presuđuje **udaljenost**; pri jednakoj
     * udaljenosti bira se ona sa više pomoći, jer se vežba teža nego što je
     * čovek tražio ne završi, a lakša se bar odradi.
     */
    @Test
    fun `nepostojeca precka se zamenjuje najblizom`() {
        // Geometrija ima samo krajeve. PARTIAL je jednu prečku od FULL, a tri
        // od NONE — dobija FULL.
        assertEquals(Support.FULL, squareColor.nearestSupport(Support.PARTIAL))

        // TRACE je bliži kraju bez podrške, pa se ne spušta nazad na tablu.
        assertEquals(Support.NONE, squareColor.nearestSupport(Support.TRACE))

        // Tražena prečka koja postoji se ne dira.
        assertEquals(Support.NONE, squareColor.nearestSupport(Support.NONE))
        assertEquals(Support.FULL, squareColor.nearestSupport(Support.FULL))
    }

    /** Pri jednakoj udaljenosti pobeđuje lakša strana. */
    @Test
    fun `na jednakoj udaljenosti pobedjuje vise pomoci`() {
        val onlyEnds = TaskSpec(
            id = "x",
            skills = listOf(Skill.NOTATION),
            supports = listOf(Support.FULL, Support.TRACE)
        )

        // PARTIAL je jednako udaljen od oba — bira se FULL.
        assertEquals(Support.FULL, onlyEnds.nearestSupport(Support.PARTIAL))
    }

    @Test
    fun `zadatak zna koje precke ume`() {
        assertTrue(squareColor.supports(Support.NONE))
        assertFalse(squareColor.supports(Support.PARTIAL))
    }

    @Test
    fun `zadatak bez vestine ili bez precke se odbija`() {
        assertFailsWith<IllegalArgumentException> {
            TaskSpec("x", emptyList(), listOf(Support.FULL))
        }
        assertFailsWith<IllegalArgumentException> {
            TaskSpec("x", listOf(Skill.COORDINATES), emptyList())
        }
    }

    @Test
    fun `svaka vestina ima svoj kljuc`() {
        val keys = Skill.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `razlaganje po vestinama se sabira`() {
        val first = SkillTally(attempted = 10, solved = 8)
        val second = SkillTally(attempted = 5, solved = 2)

        assertEquals(SkillTally(15, 10), first + second)
    }

    @Test
    fun `resenih ne sme biti vise nego pokusanih`() {
        assertFailsWith<IllegalArgumentException> { SkillTally(attempted = 3, solved = 4) }
    }

    /**
     * Prazno razlaganje znači **„nije mereno"**, ne nulu — sesije upisane pre
     * uvođenja veština ga nemaju, i tako se korisniku i kaže.
     */
    @Test
    fun `sesija bez razlaganja nije sesija sa nulom`() {
        val result = SessionResult(
            moduleId = ModuleId.GEOMETRY,
            difficulty = Difficulty.EASY,
            attempted = 10,
            solved = 9,
            mistakes = 1,
            elapsedMillis = 60_000
        )

        assertTrue(result.skills.isEmpty())
        assertEquals(emptyMap(), result.bySkill)
    }
}
