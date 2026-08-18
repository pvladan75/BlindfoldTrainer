package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Benchmark
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

    /** Vreme raste samo od sebe, da bi istorija bila hronološka. */
    private var clock = 1_000_000L

    private fun session(
        moduleId: ModuleId = ModuleId.GEOMETRY,
        taskId: String? = "square_color",
        attempted: Int = 10,
        solved: Int = 10,
        support: Support? = Support.FULL,
        isCheckup: Boolean = false,
        finishedAtMillis: Long? = clock++,
        bySkill: Map<Skill, SkillTally> = emptyMap()
    ) = SessionResult(
        moduleId = moduleId,
        difficulty = Difficulty.EASY,
        attempted = attempted,
        solved = solved,
        mistakes = attempted - solved,
        elapsedMillis = 60_000,
        bySkill = bySkill,
        support = support,
        finishedAtMillis = finishedAtMillis,
        taskId = taskId,
        isCheckup = isCheckup
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
        val task = profile.byTask.getValue("square_color")

        assertEquals(SkillTally(20, 17), task.at(Support.FULL))
        assertEquals(SkillTally(10, 5), task.at(Support.NONE))
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
        val solid = TaskProfile()
            .plus(Support.FULL, SkillTally(20, 19))
            .plus(Support.NONE, SkillTally(10, 9))

        assertEquals(Support.NONE, solid.heldRung())

        // Bez table je probano, ali premalo — drži se i dalje samo uz tablu.
        val shaky = TaskProfile()
            .plus(Support.FULL, SkillTally(20, 19))
            .plus(Support.NONE, SkillTally(2, 2))

        assertEquals(Support.FULL, shaky.heldRung())

        // Ima pokušaja, ali tačnost ne drži ni na jednoj prečki.
        val failing = TaskProfile().plus(Support.FULL, SkillTally(20, 5))
        assertNull(failing.heldRung())
    }

    /**
     * Ovo je razlog zbog kog se prečka uopšte upisuje: bez nje bi onaj ko sve
     * radi uz punu podršku izgledao jači od onoga ko se muči bez table.
     */
    @Test
    fun `precka vredi vise od procenta pri poredjenju`() {
        val comfortable = TaskProfile().plus(Support.FULL, SkillTally(20, 20))
        val harder = TaskProfile().plus(Support.NONE, SkillTally(20, 14))

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

    /**
     * Vreme završetka je uslov kao i prečka: bez njega se ne zna **kad** je
     * postignuto, pa nema ni trenda ni poređenja.
     */
    @Test
    fun `sesija bez vremena ne ulazi u profil`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(
                finishedAtMillis = null,
                bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 1))
            )
        )

        assertEquals(10, history.toProgressSnapshot().bySkill.getValue(Skill.COORDINATES).attempted)
    }

    /** Trend traži oba prozora — jedan bez drugog nije poređenje. */
    @Test
    fun `trend poredi skorasnje sa ranijim`() {
        val history = (1..3).map {
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 4, millis = 40_000)))
        } + (1..2).map {
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9, millis = 20_000)))
        }

        val trend = history.toProgressSnapshot().trendFor(Skill.COORDINATES, "square_color")!!

        assertTrue("mora imati sa čim da poredi", trend.hasComparison)
        assertEquals(20, trend.recent.attempted)
        assertEquals(18, trend.recent.solved)
        assertEquals(2_000L, trend.recent.millisPerAttempt)
        assertEquals(4_000L, trend.earlier.millisPerAttempt)
    }

    @Test
    fun `bez dovoljno istorije nema poredjenja`() {
        val history = listOf(session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9))))
        val trend = history.toProgressSnapshot().trendFor(Skill.COORDINATES, "square_color")!!

        assertTrue("jedan prozor nije trend", !trend.hasComparison)
        assertNull(history.toProgressSnapshot().trendFor(Skill.CALCULATION, "play_out"))
    }

    /**
     * Automatska nije isto što i tačna. Tačan ali spor odgovor znači da veština
     * još troši pažnju — a radna memorija je jedna, pa se na takvoj ne može
     * graditi sledeća.
     */
    @Test
    fun `tacno ali sporo nije automatski`() {
        val fast = listOf(
            session(
                support = Support.NONE,
                bySkill = mapOf(Skill.COORDINATES to SkillTally(20, 19, millis = 30_000))
            )
        ).toProgressSnapshot()

        val slow = listOf(
            session(
                support = Support.NONE,
                bySkill = mapOf(Skill.COORDINATES to SkillTally(20, 19, millis = 120_000))
            )
        ).toProgressSnapshot()

        assertTrue("1,5 s po zadatku je automatski", fast.isAutomatic(Skill.COORDINATES))
        assertTrue("6 s po zadatku nije", !slow.isAutomatic(Skill.COORDINATES))
    }

    /**
     * Preduslovi **ništa ne zaključavaju** — samo se kaže šta bi ubrzalo posao.
     */
    @Test
    fun `temelj koji nije automatski se prijavljuje`() {
        val nothing = ProgressSnapshot.EMPTY

        assertEquals(setOf(Skill.COORDINATES), nothing.foundationsMissing(Skill.POSITION_HOLD))
        assertEquals(emptySet<Skill>(), nothing.foundationsMissing(Skill.COORDINATES))

        val withCoordinates = listOf(
            session(
                support = Support.NONE,
                bySkill = mapOf(Skill.COORDINATES to SkillTally(20, 19, millis = 30_000))
            )
        ).toProgressSnapshot()

        assertEquals(emptySet<Skill>(), withCoordinates.foundationsMissing(Skill.POSITION_HOLD))
        assertEquals(
            setOf(Skill.PIECE_GEOMETRY, Skill.POSITION_HOLD),
            withCoordinates.foundationsMissing(Skill.POSITION_UPDATE)
        )
    }

    /**
     * Sesije drugog zadatka ne ulaze u trend, ma koliko ih bilo — inače bi
     * prelazak na drugi modul izgledao kao nazadovanje.
     */
    @Test
    fun `trend ne meri drugi zadatak`() {
        val history = (1..4).map {
            session(
                taskId = "reconstruct",
                bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 3, millis = 200_000))
            )
        } + listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9, millis = 20_000)))
        )

        val trend = history.toProgressSnapshot().trendFor(Skill.COORDINATES, "square_color")!!

        assertEquals(10, trend.recent.attempted)
        assertEquals(0, trend.earlier.attempted)
        assertTrue("jedan prozor nije trend", !trend.hasComparison)
    }

    /**
     * Orijentir se priznaje tek kad su **oba** ispunjena. Da stoji samo vreme,
     * merilo bi pozivalo na žurbu, a žurba obara tačnost.
     */
    @Test
    fun `orijentir trazi i vreme i tacnost`() {
        val target = Benchmark(millisPerAttempt = 3_000, minAccuracy = 0.9f)

        val fastAndAccurate = TaskProfile().plus(Support.FULL, SkillTally(10, 10, 25_000))
        val fastButSloppy = TaskProfile().plus(Support.FULL, SkillTally(10, 7, 25_000))
        val accurateButSlow = TaskProfile().plus(Support.FULL, SkillTally(10, 10, 90_000))

        assertTrue(fastAndAccurate.hasReached(Support.FULL, target))
        assertTrue("žurba se ne priznaje", !fastButSloppy.hasReached(Support.FULL, target))
        assertTrue("sporo se ne priznaje", !accurateButSlow.hasReached(Support.FULL, target))

        // Jedna dobra večer nije dokaz.
        val tooFew = TaskProfile().plus(Support.FULL, SkillTally(3, 3, 6_000))
        assertTrue("premalo pokušaja", !tooFew.hasReached(Support.FULL, target))
    }

    /** Građa za grafik ide po zadatku **i** prečki. */
    @Test
    fun `serija za grafik ne mesa precke`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 8))),
            session(support = Support.NONE, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 5))),
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9)))
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(2, snapshot.sessionsFor(Skill.COORDINATES, "square_color", Support.FULL).size)
        assertEquals(1, snapshot.sessionsFor(Skill.COORDINATES, "square_color", Support.NONE).size)
    }

    /**
     * Provera i vežba mere različite stvari, pa se ne sabiraju: vežba daje
     * napredak unutar svog zadatka, provera daje nivo koji je svima jednak.
     */
    @Test
    fun `provera ne ulazi u profil vezbi`() {
        val history = listOf(
            session(bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 5))),
            session(isCheckup = true, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9)))
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(10, snapshot.bySkill.getValue(Skill.COORDINATES).attempted)
        assertEquals(SkillTally(10, 9), snapshot.lastCheckup(Skill.COORDINATES)?.tally)
        assertEquals(setOf(Skill.COORDINATES), snapshot.checkedSkills)
    }

    @Test
    fun `nivo je poslednja provera, ne prva`() {
        val history = listOf(
            session(isCheckup = true, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 4))),
            session(isCheckup = true, bySkill = mapOf(Skill.COORDINATES to SkillTally(10, 9)))
        )

        val snapshot = history.toProgressSnapshot()

        assertEquals(SkillTally(10, 9), snapshot.lastCheckup(Skill.COORDINATES)?.tally)
        assertEquals(2, snapshot.checkupsFor(Skill.COORDINATES).size)
    }

    /** Provera ne nosi poene — merilo koje nagrađuje prestaje da meri. */
    @Test
    fun `provera ne donosi poene`() {
        val checkup = session(isCheckup = true, solved = 10, bySkill = emptyMap())
        val training = session(solved = 10, bySkill = emptyMap())

        assertEquals(0, Xp.forSession(checkup))
        assertTrue("vežba i dalje nosi poene", Xp.forSession(training) > 0)
    }

    @Test
    fun `nemereno nije slabost`() {
        val empty = ProgressSnapshot.EMPTY

        assertNull(empty.weakestSkill)
        assertTrue(empty.measuredSkills.isEmpty())
    }
}
