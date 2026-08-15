package com.program.blindfoldtrainer.core.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RankTest {

    @Test
    fun `pocetak lestvice je prvi rang`() {
        assertEquals(Rank.BEGINNER, Rank.forXp(0))
        assertEquals(Rank.BEGINNER, Rank.forXp(999))
    }

    @Test
    fun `tacno na pragu se rang menja`() {
        assertEquals(Rank.STUDENT, Rank.forXp(Rank.STUDENT.requiredXp))
    }

    @Test
    fun `preko vrha lestvice ostaje najvisi rang`() {
        assertEquals(Rank.GRANDMASTER, Rank.forXp(Int.MAX_VALUE))
        assertNull(Rank.GRANDMASTER.next)
    }

    @Test
    fun `pragovi rastu`() {
        Rank.entries.zipWithNext { lower, higher ->
            assertTrue(lower.requiredXp < higher.requiredXp, "$lower >= $higher")
        }
    }

    @Test
    fun `traka napretka je na pola izmedju dva praga`() {
        val half = (Rank.BEGINNER.requiredXp + Rank.STUDENT.requiredXp) / 2
        val progress = RankProgress.forXp(half)
        assertEquals(Rank.BEGINNER, progress.current)
        assertEquals(Rank.STUDENT, progress.next)
        assertEquals(0.5f, progress.fraction)
    }

    @Test
    fun `na vrhu lestvice je traka puna`() {
        val progress = RankProgress.forXp(Rank.GRANDMASTER.requiredXp + 5_000)
        assertNull(progress.next)
        assertEquals(1f, progress.fraction)
    }
}
