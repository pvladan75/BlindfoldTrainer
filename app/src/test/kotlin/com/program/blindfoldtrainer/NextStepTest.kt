package com.program.blindfoldtrainer

import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.Reason
import com.program.blindfoldtrainer.core.progress.Recommendation
import com.program.blindfoldtrainer.core.progress.SkillEntry
import com.program.blindfoldtrainer.ui.NextStep
import com.program.blindfoldtrainer.ui.chooseNext
import com.program.blindfoldtrainer.ui.startingChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kartica „Sledeće" nudi **jedno**: proveru dok se veština ne izmeri, ali ne
 * dva puta zaredom; inače vežbu. Ono drugo stoji kao alternativa.
 */
class NextStepTest {

    private val recommendation = Recommendation(
        skill = Skill.PIECE_GEOMETRY,
        taskId = "shortest_path",
        support = Support.FULL,
        difficulty = Difficulty.EASY,
        reason = Reason.NEVER_TRIED
    )

    private val checkup = Checkup(
        skill = Skill.COORDINATES,
        moduleId = ModuleId.GEOMETRY,
        taskId = "square_color",
        difficulty = Difficulty.EASY
    )

    private fun entry(
        skill: Skill,
        taskId: String,
        isCheckup: Boolean = false,
        support: Support = Support.FULL,
        difficulty: Difficulty = Difficulty.EASY,
        at: Long = 0L
    ) = SkillEntry(
        atMillis = at,
        skill = skill,
        taskId = taskId,
        support = support,
        difficulty = difficulty,
        tally = SkillTally(attempted = 10, solved = 8),
        isCheckup = isCheckup
    )

    private fun progress(vararg history: SkillEntry) =
        ProgressSnapshot(skillHistory = history.toList())

    @Test
    fun `bez icega nema ni ponude`() {
        assertNull(chooseNext(recommendation = null, checkup = null, progress = progress()))
    }

    @Test
    fun `samo vezba - ona je glavna, bez alternative`() {
        val offer = chooseNext(recommendation, checkup = null, progress = progress())!!

        assertEquals(NextStep.Practise(recommendation), offer.primary)
        assertNull(offer.alternative)
    }

    @Test
    fun `samo provera - ona je glavna, bez alternative`() {
        val offer = chooseNext(recommendation = null, checkup = checkup, progress = progress())!!

        assertEquals(NextStep.Measure(checkup), offer.primary)
        assertNull(offer.alternative)
    }

    /** Prva stvar u aplikaciji je kratka provera — profil mora odnekud da počne. */
    @Test
    fun `prazan profil - provera ide prva, vezba je alternativa`() {
        val offer = chooseNext(recommendation, checkup, progress())!!

        assertEquals(NextStep.Measure(checkup), offer.primary)
        assertEquals(NextStep.Practise(recommendation), offer.alternative)
    }

    /** Dve provere zaredom se ne nude — između stane vežba. */
    @Test
    fun `odmah posle provere ide vezba`() {
        val justMeasured = progress(entry(Skill.PIECE_GEOMETRY, "shortest_path", isCheckup = true))

        val offer = chooseNext(recommendation, checkup, justMeasured)!!

        assertEquals(NextStep.Practise(recommendation), offer.primary)
        assertEquals(NextStep.Measure(checkup), offer.alternative)
    }

    /** Posle vežbe je nemerena veština opet prva. */
    @Test
    fun `posle vezbe se opet nudi nemerena provera`() {
        val history = progress(
            entry(Skill.PIECE_GEOMETRY, "shortest_path", isCheckup = true, at = 1),
            entry(Skill.PIECE_GEOMETRY, "shortest_path", at = 2)
        )

        val offer = chooseNext(recommendation, checkup, history)!!

        assertEquals(NextStep.Measure(checkup), offer.primary)
    }

    /** Izmerena veština ne gura proveru ispred vežbe — ponovno merenje čeka u drugom redu. */
    @Test
    fun `izmerena vestina - vezba je glavna, ponovno merenje alternativa`() {
        val history = progress(
            entry(Skill.COORDINATES, "square_color", isCheckup = true, at = 1),
            entry(Skill.PIECE_GEOMETRY, "shortest_path", at = 2)
        )

        val offer = chooseNext(recommendation, checkup, history)!!

        assertEquals(NextStep.Practise(recommendation), offer.primary)
        assertEquals(NextStep.Measure(checkup), offer.alternative)
    }

    // ---- sa čime modul kreće ----

    private fun module(vararg tasks: TaskSpec) = object : TrainingModule {
        override val id = ModuleId.FOLLOW_GAME
        override val titleRes = 0
        override val descriptionRes = 0
        override val iconRes = 0
        override val tasks = tasks.toList()
        override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

        @Composable
        override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) = Unit
    }

    private val whereIsPiece = TaskSpec(
        id = "where_is_piece",
        skills = listOf(Skill.POSITION_UPDATE),
        supports = listOf(Support.FULL, Support.NONE)
    )
    private val attackers = TaskSpec(
        id = "attackers",
        skills = listOf(Skill.SQUARE_CONTROL),
        supports = listOf(Support.FULL, Support.PARTIAL, Support.NONE)
    )

    @Test
    fun `prvi ulaz krece od zatecenog zadatka, najvise pomoci i najlakse tezine`() {
        val choice = module(whereIsPiece, attackers).startingChoice(progress(), eyesFree = false)

        assertEquals("where_is_piece", choice.taskId)
        assertEquals(Support.FULL, choice.support)
        assertEquals(Difficulty.EASY, choice.difficulty)
    }

    @Test
    fun `ko vezba bez ekrana krece bez table`() {
        val choice = module(whereIsPiece, attackers).startingChoice(progress(), eyesFree = true)

        assertEquals(Support.NONE, choice.support)
    }

    @Test
    fun `nastavlja se gde se stalo - zadatak, oslonac i tezina poslednje vezbe`() {
        val history = progress(
            entry(Skill.POSITION_UPDATE, "where_is_piece", at = 1),
            entry(
                Skill.SQUARE_CONTROL, "attackers",
                support = Support.PARTIAL, difficulty = Difficulty.HARD, at = 2
            )
        )

        val choice = module(whereIsPiece, attackers).startingChoice(history, eyesFree = false)

        assertEquals("attackers", choice.taskId)
        assertEquals(Support.PARTIAL, choice.support)
        assertEquals(Difficulty.HARD, choice.difficulty)
    }

    /** Provera je uvek ista i ne kaže gde je čovek stao u vežbi. */
    @Test
    fun `provera se ne racuna kao poslednja vezba`() {
        val history = progress(
            entry(
                Skill.SQUARE_CONTROL, "attackers",
                support = Support.NONE, difficulty = Difficulty.MEDIUM, at = 1
            ),
            entry(Skill.POSITION_UPDATE, "where_is_piece", isCheckup = true, at = 2)
        )

        val choice = module(whereIsPiece, attackers).startingChoice(history, eyesFree = false)

        assertEquals("attackers", choice.taskId)
        assertEquals(Support.NONE, choice.support)
        assertEquals(Difficulty.MEDIUM, choice.difficulty)
    }

    /** Tuđa istorija ne dira ovaj modul. */
    @Test
    fun `istorija drugog modula se ne gleda`() {
        val history = progress(
            entry(Skill.COORDINATES, "square_color", support = Support.NONE, difficulty = Difficulty.HARD)
        )

        val choice = module(whereIsPiece, attackers).startingChoice(history, eyesFree = false)

        assertEquals("where_is_piece", choice.taskId)
        assertEquals(Support.FULL, choice.support)
        assertEquals(Difficulty.EASY, choice.difficulty)
    }

    /** Gde oslonac nije lestvica, ne nudi se — ali se i dalje zna šta će biti. */
    @Test
    fun `oslonac koji nije lestvica se ne bira, ali se vidi`() {
        val squareColor = TaskSpec(
            id = "square_color",
            skills = listOf(Skill.COORDINATES),
            supports = listOf(Support.FULL, Support.NONE),
            supportIsLadder = false
        )
        val history = progress(entry(Skill.COORDINATES, "square_color", support = Support.NONE))

        val choice = module(squareColor).startingChoice(history, eyesFree = false)

        assertNull(choice.support)
        assertEquals(Support.FULL, choice.shownSupport)
        assertEquals("square_color", choice.taskId)
    }
}
