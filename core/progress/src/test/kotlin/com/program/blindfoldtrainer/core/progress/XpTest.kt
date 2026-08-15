package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XpTest {

    private fun session(
        difficulty: Difficulty = Difficulty.EASY,
        attempted: Int = 5,
        solved: Int = 5,
        mistakes: Int = 0,
        completed: Boolean = true
    ) = SessionResult(
        moduleId = ModuleId.GEOMETRY,
        difficulty = difficulty,
        attempted = attempted,
        solved = solved,
        mistakes = mistakes,
        elapsedMillis = 60_000,
        completed = completed
    )

    @Test
    fun `besprekorna sesija nosi dodatak`() {
        // 5 x 10 = 50, plus 50 odsto = 75
        assertEquals(75, Xp.forSession(session()))
    }

    @Test
    fun `promasaji skidaju poene ali nema dodatka`() {
        // 5 x 10 = 50, minus 3 x 2 = 44; dodatka nema jer sesija nije besprekorna
        assertEquals(44, Xp.forSession(session(mistakes = 3)))
    }

    @Test
    fun `teze sesije nose vise poena`() {
        val easy = Xp.forSession(session(difficulty = Difficulty.EASY))
        val medium = Xp.forSession(session(difficulty = Difficulty.MEDIUM))
        val hard = Xp.forSession(session(difficulty = Difficulty.HARD))
        assertTrue(easy < medium && medium < hard, "$easy < $medium < $hard")
    }

    @Test
    fun `mnogo promasaja ne daje negativan rezultat`() {
        assertEquals(0, Xp.forSession(session(solved = 1, attempted = 5, mistakes = 99)))
    }

    @Test
    fun `napustena sesija donosi resene zadatke ali ne i dodatak`() {
        // Sve rešeno i bez greške, ali sesija nije dovršena — dodatka nema.
        assertEquals(50, Xp.forSession(session(completed = false)))
    }

    @Test
    fun `sesija bez ijednog resenog zadatka ne nosi poene`() {
        assertEquals(0, Xp.forSession(session(attempted = 4, solved = 0)))
    }
}
