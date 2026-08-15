package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementTest {

    private fun session(
        moduleId: ModuleId = ModuleId.GEOMETRY,
        difficulty: Difficulty = Difficulty.EASY,
        attempted: Int = 5,
        solved: Int = 5,
        mistakes: Int = 0,
        elapsedMillis: Long = 30_000
    ) = SessionResult(
        moduleId = moduleId,
        difficulty = difficulty,
        attempted = attempted,
        solved = solved,
        mistakes = mistakes,
        elapsedMillis = elapsedMillis
    )

    @Test
    fun `prazan napredak nema nijedno dostignuce`() {
        assertTrue(ProgressSnapshot.EMPTY.achievements.isEmpty())
    }

    @Test
    fun `prva sesija donosi prva dva dostignuca`() {
        val snapshot = ProgressSnapshot.EMPTY + session()
        assertTrue(Achievement.FIRST_SESSION in snapshot.achievements)
        assertTrue(Achievement.FIRST_PERFECT in snapshot.achievements)
    }

    @Test
    fun `sesija sa greskom ne donosi besprekornost`() {
        val snapshot = ProgressSnapshot.EMPTY + session(mistakes = 1)
        assertTrue(Achievement.FIRST_SESSION in snapshot.achievements)
        assertFalse(Achievement.FIRST_PERFECT in snapshot.achievements)
    }

    @Test
    fun `niz besprekornih se prekida greskom`() {
        val history = List(4) { session() } + session(mistakes = 1) + List(3) { session() }
        val snapshot = history.toProgressSnapshot()

        assertEquals(3, snapshot.perfectStreak)
        assertEquals(4, snapshot.bestPerfectStreak)
        assertFalse(Achievement.PERFECT_STREAK_FIVE in snapshot.achievements)
    }

    @Test
    fun `pet besprekornih zaredom donosi dostignuce i ostaje osvojeno`() {
        val snapshot = (List(5) { session() } + session(mistakes = 2)).toProgressSnapshot()

        assertEquals(0, snapshot.perfectStreak)
        assertEquals(5, snapshot.bestPerfectStreak)
        assertTrue(Achievement.PERFECT_STREAK_FIVE in snapshot.achievements)
    }

    @Test
    fun `besprekorno na teskom se razlikuje od besprekornog na lakom`() {
        val easy = ProgressSnapshot.EMPTY + session(difficulty = Difficulty.EASY)
        assertFalse(Achievement.PERFECT_ON_HARD in easy.achievements)

        val hard = easy + session(difficulty = Difficulty.HARD)
        assertTrue(Achievement.PERFECT_ON_HARD in hard.achievements)
    }

    @Test
    fun `tri razlicita modula`() {
        val two = listOf(
            session(moduleId = ModuleId.GEOMETRY),
            session(moduleId = ModuleId.PAIRS)
        ).toProgressSnapshot()
        assertFalse(Achievement.THREE_MODULES in two.achievements)

        val three = two + session(moduleId = ModuleId.ENDGAME)
        assertTrue(Achievement.THREE_MODULES in three.achievements)
    }

    @Test
    fun `sat vremena treninga`() {
        val almost = ProgressSnapshot.EMPTY + session(elapsedMillis = 59 * 60_000L)
        assertFalse(Achievement.HOUR_OF_TRAINING in almost.achievements)

        val hour = almost + session(elapsedMillis = 60_000L)
        assertTrue(Achievement.HOUR_OF_TRAINING in hour.achievements)
    }

    @Test
    fun `dostignuca se samo dodaju kako istorija raste`() {
        var snapshot = ProgressSnapshot.EMPTY
        var earned = emptySet<Achievement>()

        repeat(30) { index ->
            snapshot += session(
                moduleId = ModuleId.entries[index % 3],
                difficulty = Difficulty.entries[index % 3],
                mistakes = if (index % 7 == 0) 1 else 0
            )
            assertTrue(
                earned.all { it in snapshot.achievements },
                "dostignuće je nestalo posle sesije $index"
            )
            earned = snapshot.achievements
        }
    }
}
