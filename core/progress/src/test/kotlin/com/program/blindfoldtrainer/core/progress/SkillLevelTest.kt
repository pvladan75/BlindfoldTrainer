package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Nivo veštine je **prečka koju drži**, ne procenat. Test drži da se to izvodi iz
 * orijentira koji već postoje, a ne iz nekog novog broja.
 */
class SkillLevelTest {

    private val color = TaskSpec(
        id = "square_color",
        skills = listOf(Skill.COORDINATES),
        supports = listOf(Support.FULL, Support.NONE),
        benchmarks = mapOf(
            Support.FULL to Benchmark(3_000, 0.9f),
            Support.NONE to Benchmark(4_500, 0.9f)
        )
    )

    /** Drugi zadatak iste veštine — da se vidi da se prečke sabiraju preko njih. */
    private val reach = TaskSpec(
        id = "reach_on_line",
        skills = listOf(Skill.COORDINATES),
        supports = listOf(Support.FULL),
        benchmarks = mapOf(Support.FULL to Benchmark(15_000, 0.8f))
    )

    /** Veštinu samo **nosi**, ne meri je. */
    private val carries = TaskSpec(
        id = "walk_piece",
        skills = listOf(Skill.POSITION_UPDATE, Skill.COORDINATES),
        supports = listOf(Support.NONE),
        benchmarks = mapOf(Support.NONE to Benchmark(10_000, 0.85f))
    )

    private val tasks = listOf(color, reach, carries)

    private var clock = 1_000_000L

    private fun sessions(
        taskId: String,
        support: Support,
        count: Int,
        attempted: Int = 10,
        solved: Int = 10,
        millis: Long = 10_000
    ) = List(count) {
        SessionResult(
            moduleId = ModuleId.GEOMETRY,
            difficulty = Difficulty.EASY,
            attempted = attempted,
            solved = solved,
            mistakes = attempted - solved,
            elapsedMillis = millis,
            bySkill = mapOf(Skill.COORDINATES to SkillTally(attempted, solved, millis)),
            support = support,
            finishedAtMillis = clock++,
            taskId = taskId
        )
    }

    @Test
    fun `vestinu koju niko ne meri prijavljuje kao rupu u ponudi, ne kao slabost`() {
        val level = ProgressSnapshot.EMPTY.levelOf(Skill.RECOVERY, tasks)

        assertEquals(SkillStage.NOT_MEASURED, level.stage)
        assertNull(level.ceiling)
    }

    @Test
    fun `merena a nedodirnuta vestina zna dokle moze da dogura`() {
        val level = ProgressSnapshot.EMPTY.levelOf(Skill.COORDINATES, tasks)

        assertEquals(SkillStage.UNTRIED, level.stage)
        assertEquals(Support.NONE, level.ceiling)
        assertNull(level.holds)
    }

    /** Vežbanje samo po sebi nije nivo — orijentir mora da se dostigne. */
    @Test
    fun `vezbana bez dostignutog orijentira je tek u izgradnji`() {
        val history = sessions("square_color", Support.FULL, count = 3, solved = 4)
        val level = history.toProgressSnapshot().levelOf(Skill.COORDINATES, tasks)

        assertEquals(SkillStage.STARTED, level.stage)
        assertNull(level.holds)
    }

    @Test
    fun `dostignut orijentir uz tablu drzi tu precku, ali nije savladano`() {
        val history = sessions("square_color", Support.FULL, count = 3, millis = 20_000)
        val level = history.toProgressSnapshot().levelOf(Skill.COORDINATES, tasks)

        assertEquals(SkillStage.HOLDING, level.stage)
        assertEquals(Support.FULL, level.holds)
        assertEquals(Support.NONE, level.ceiling)
    }

    /** Najteža prečka koju ijedan merilac nudi — odatle nadalje ide održavanje. */
    @Test
    fun `orijentir na najtezoj precki je savladano`() {
        val history = sessions("square_color", Support.FULL, count = 3, millis = 20_000) +
            sessions("square_color", Support.NONE, count = 3, millis = 30_000)

        val level = history.toProgressSnapshot().levelOf(Skill.COORDINATES, tasks)

        assertEquals(SkillStage.MASTERED, level.stage)
        assertEquals(Support.NONE, level.holds)
    }

    /**
     * Dovoljno je da se prečka pokaže u **jednom** zadatku: zadaci su različiti
     * poslovi iste veštine, a traženje svih bi kažnjavalo modul sa više zadataka.
     */
    @Test
    fun `precka se priznaje iz bilo kog zadatka koji vestinu meri`() {
        val history = sessions("reach_on_line", Support.FULL, count = 3, millis = 60_000)
        val level = history.toProgressSnapshot().levelOf(Skill.COORDINATES, tasks)

        assertEquals(SkillStage.HOLDING, level.stage)
        assertEquals(Support.FULL, level.holds)
    }

    /**
     * Zadatak koji veštinu samo **nosi** ne sme da diže njen nivo: po njemu se u
     * profil te veštine i ne upisuje, pa bi obećavao prečku do koje se vežbanjem
     * baš te veštine ne dolazi.
     */
    @Test
    fun `zadatak koji vestinu samo nosi ne ulazi u njen domet`() {
        assertEquals(listOf(Support.FULL, Support.NONE), rungsFor(Skill.COORDINATES, tasks))
        assertEquals(emptyList<Support>(), rungsFor(Skill.POSITION_HOLD, tasks))
    }

    /** Ko pročita da negde stoji slabo, ima pravo da odmah sazna i kuda po nju. */
    @Test
    fun `kaze kojim zadatkom i na kojoj precki se vestina sad gradi`() {
        val step = ProgressSnapshot.EMPTY.practiceFor(Skill.COORDINATES, tasks)!!

        assertEquals(Support.FULL, step.support)
        assertEquals(true, step.task.id in setOf("square_color", "reach_on_line"))
    }

    /**
     * Nosi i **težinu**. Bez nje bi je pozivalac ukucao — a to je već jednom
     * napravljeno na kartici Predloga, gde je svakoga ko sluša predlog slalo na
     * najlakšu.
     */
    @Test
    fun `korak nosi i tezinu, po istom pravilu kao predlog`() {
        val step = ProgressSnapshot.EMPTY.practiceFor(Skill.COORDINATES, tasks)!!

        assertEquals(Difficulty.EASY, step.difficulty)
    }

    /** Modul koji težine ne nudi je ne dobija ni ovde — izmišljati je značilo bi lagati. */
    @Test
    fun `modul bez tezina ne dobija tezinu ni u koraku`() {
        val step = ProgressSnapshot.EMPTY
            .practiceFor(Skill.COORDINATES, tasks, difficultiesFor = { emptyList() })!!

        assertNull(step.difficulty)
    }

    @Test
    fun `vestina koju niko ne meri nema ni kuda po nju`() {
        assertNull(ProgressSnapshot.EMPTY.practiceFor(Skill.RECOVERY, tasks))
    }
}
