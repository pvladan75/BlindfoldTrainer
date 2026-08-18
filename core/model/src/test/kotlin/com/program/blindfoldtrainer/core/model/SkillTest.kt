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
    /**
     * Slika u uputstvu se crta iz [skillFloors], pa ovi testovi čuvaju **sliku**,
     * ne računicu: ako neko doda vezu koja gura veštinu iznad njenog temelja,
     * grana bi na ekranu išla nagore a niko to ne bi prijavio.
     */
    @Test
    fun `svaka vestina stoji ispod svih svojih temelja`() {
        val floors = skillFloors()
        val floorOf = floors.flatMapIndexed { index: Int, skills: List<Skill> ->
            skills.map { it to index }
        }.toMap()

        Skill.entries.forEach { skill ->
            skill.requires.forEach { need ->
                assertTrue(
                    floorOf.getValue(need) < floorOf.getValue(skill),
                    "$need mora biti iznad $skill"
                )
            }
        }
    }

    @Test
    fun `svaka vestina je tacno na jednom spratu`() {
        val floors = skillFloors()

        assertEquals(Skill.entries.size, floors.sumOf { it.size })
        assertEquals(Skill.entries.toSet(), floors.flatten().toSet())
    }

    /** Prvi sprat je ono što se vežba bez ičega pre sebe — inače nema odakle da se krene. */
    @Test
    fun `prvi sprat ne trazi nista pre sebe`() {
        assertTrue(skillFloors().first().all { it.requires.isEmpty() })
        assertTrue(skillFloors().first().isNotEmpty())
    }

    /**
     * **Ovo je test slike, ne računice.** Grana koja preskoči sprat na ekranu
     * prolazi kroz tuđe ime i slika prestaje da se prati okom — a to je jedini
     * razlog zbog kog se sprat uopšte računa „što kasnije".
     *
     * Pada ako neko doda vezu koja razvuče veštinu od onoga što je hrani. Tada
     * treba popraviti raspored, ne obrisati test.
     */
    @Test
    fun `nijedna grana ne preskace sprat`() {
        val floorOf = skillFloors().flatMapIndexed { index: Int, skills: List<Skill> ->
            skills.map { it to index }
        }.toMap()

        Skill.entries.forEach { skill ->
            skill.requires.forEach { need ->
                assertEquals(
                    floorOf.getValue(skill) - 1,
                    floorOf.getValue(need),
                    "$need hrani $skill, pa mora biti tačno jedan sprat iznad"
                )
            }
        }
    }
}
