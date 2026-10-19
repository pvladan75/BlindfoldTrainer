package com.program.blindfoldtrainer

import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SkillEntry
import com.program.blindfoldtrainer.core.progress.SkillStage
import com.program.blindfoldtrainer.ui.shiftOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sažetak sesije kaže **šta se pomerilo**: nivo veštine pre i posle, i da li je
 * orijentir na ovoj prečki ispunjen — i vreme i tačnost, ne jedno od toga.
 */
class SessionShiftTest {

    private val squareColor = TaskSpec(
        id = "square_color",
        skills = listOf(Skill.COORDINATES),
        supports = listOf(Support.FULL, Support.NONE),
        benchmarks = mapOf(Support.FULL to Benchmark(millisPerAttempt = 3_000, minAccuracy = 0.8f))
    )
    private val tasks = listOf(squareColor)

    private fun session(
        attempted: Int = 10,
        solved: Int = 9,
        elapsed: Long = 20_000,
        support: Support? = Support.FULL,
        taskId: String? = "square_color"
    ) = SessionResult(
        moduleId = ModuleId.GEOMETRY,
        difficulty = Difficulty.EASY,
        attempted = attempted,
        solved = solved,
        mistakes = attempted - solved,
        elapsedMillis = elapsed,
        support = support,
        taskId = taskId
    )

    /** Istorija jedne vežbe; spora (6 s po pitanju), pa orijentir od 3 s ne drži. */
    private fun practised(millis: Long = 60_000) = ProgressSnapshot(
        skillHistory = listOf(
            SkillEntry(
                atMillis = 1,
                skill = Skill.COORDINATES,
                taskId = "square_color",
                support = Support.FULL,
                difficulty = Difficulty.EASY,
                tally = SkillTally(attempted = 10, solved = 9, millis = millis)
            )
        )
    )

    @Test
    fun `sesija bez poznatog zadatka ne tvrdi nista`() {
        assertNull(shiftOf(session(taskId = "unknown"), ProgressSnapshot.EMPTY, practised(), tasks))
    }

    @Test
    fun `prva vezba pomera vestinu iz neprobanog u izgradnju`() {
        val shift = shiftOf(session(), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertEquals(Skill.COORDINATES, shift.skill)
        assertEquals(SkillStage.UNTRIED, shift.before.stage)
        assertEquals(SkillStage.STARTED, shift.after.stage)
        assertTrue(shift.levelChanged)
    }

    /** Brza istorija (2 s po pitanju, 90%) drži orijentir, pa je nivo „drži". */
    @Test
    fun `istorija koja drzi orijentir daje nivo drzi`() {
        val shift = shiftOf(session(), ProgressSnapshot.EMPTY, practised(millis = 20_000), tasks)!!

        assertEquals(SkillStage.HOLDING, shift.after.stage)
        assertEquals(Support.FULL, shift.after.holds)
    }

    @Test
    fun `dok se upis ne desi, nivo se nije pomerio`() {
        val shift = shiftOf(session(), practised(), practised(), tasks)!!

        assertFalse(shift.levelChanged)
    }

    /** 20 s za 10 pitanja je 2 s po pitanju, uz 90% — oba uslova ispunjena. */
    @Test
    fun `orijentir je ispunjen kad su i vreme i tacnost tu`() {
        val shift = shiftOf(session(), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertEquals(2_000L, shift.millisPerAttempt)
        assertTrue(shift.metBenchmark)
    }

    @Test
    fun `brzo ali netacno nije orijentir`() {
        val shift = shiftOf(session(solved = 6), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertFalse(shift.metBenchmark)
    }

    @Test
    fun `tacno ali sporo nije orijentir`() {
        val shift = shiftOf(session(elapsed = 40_000), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertFalse(shift.metBenchmark)
    }

    /** Prečka bez orijentira nema šta da ispuni — i ne tvrdi se da jeste. */
    @Test
    fun `bez orijentira na precki nema ni ispunjenja`() {
        val shift = shiftOf(session(support = Support.NONE), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertNull(shift.benchmark)
        assertFalse(shift.metBenchmark)
    }

    @Test
    fun `bez ijednog pokusaja nema vremena po pokusaju`() {
        val shift = shiftOf(session(attempted = 0, solved = 0), ProgressSnapshot.EMPTY, practised(), tasks)!!

        assertNull(shift.millisPerAttempt)
        assertFalse(shift.metBenchmark)
    }
}
