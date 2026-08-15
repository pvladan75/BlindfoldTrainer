package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressSnapshotTest {

    private fun session(
        moduleId: ModuleId = ModuleId.GEOMETRY,
        difficulty: Difficulty = Difficulty.EASY,
        attempted: Int = 5,
        solved: Int = 5,
        mistakes: Int = 0
    ) = SessionResult(
        moduleId = moduleId,
        difficulty = difficulty,
        attempted = attempted,
        solved = solved,
        mistakes = mistakes,
        elapsedMillis = 30_000
    )

    @Test
    fun `prazan snimak nema poene ni sesije`() {
        val snapshot = emptyList<SessionResult>().toProgressSnapshot()
        assertEquals(0, snapshot.totalXp)
        assertEquals(0, snapshot.sessions)
        assertEquals(Rank.BEGINNER, snapshot.rank)
    }

    @Test
    fun `sabiranje istorije daje isti rezultat kao dodavanje jedne po jedne`() {
        val history = listOf(
            session(),
            session(moduleId = ModuleId.PAIRS, difficulty = Difficulty.HARD, mistakes = 2),
            session(moduleId = ModuleId.ENDGAME, attempted = 3, solved = 1, mistakes = 1)
        )

        val folded = history.fold(ProgressSnapshot.EMPTY) { snapshot, result -> snapshot + result }
        assertEquals(folded, history.toProgressSnapshot())
    }

    @Test
    fun `napredak se vodi odvojeno po modulima`() {
        val snapshot = listOf(
            session(moduleId = ModuleId.GEOMETRY),
            session(moduleId = ModuleId.GEOMETRY),
            session(moduleId = ModuleId.PAIRS, attempted = 4, solved = 2, mistakes = 1)
        ).toProgressSnapshot()

        assertEquals(setOf(ModuleId.GEOMETRY, ModuleId.PAIRS), snapshot.startedModules)
        assertEquals(2, snapshot.byModule.getValue(ModuleId.GEOMETRY).sessions)
        assertEquals(10, snapshot.byModule.getValue(ModuleId.GEOMETRY).solved)
        assertEquals(2, snapshot.byModule.getValue(ModuleId.PAIRS).solved)
    }

    @Test
    fun `ukupni poeni su zbir poena po modulima`() {
        val snapshot = listOf(
            session(),
            session(moduleId = ModuleId.PAIRS, difficulty = Difficulty.MEDIUM),
            session(moduleId = ModuleId.ENDGAME, difficulty = Difficulty.HARD, mistakes = 4)
        ).toProgressSnapshot()

        assertEquals(snapshot.byModule.values.sumOf { it.xp }, snapshot.totalXp)
    }

    @Test
    fun `najbolji rezultat po tezini se pamti kao maksimum`() {
        val snapshot = listOf(
            session(attempted = 5, solved = 5),
            session(attempted = 5, solved = 2, mistakes = 3),
            session(difficulty = Difficulty.HARD, attempted = 5, solved = 3)
        ).toProgressSnapshot()

        val geometry = snapshot.byModule.getValue(ModuleId.GEOMETRY)
        assertEquals(75, geometry.bestXpByDifficulty.getValue(Difficulty.EASY))
        assertEquals(105, geometry.bestXpByDifficulty.getValue(Difficulty.HARD))
    }

    @Test
    fun `besprekorne sesije se broje`() {
        val snapshot = listOf(
            session(),
            session(mistakes = 1),
            session(attempted = 5, solved = 4)
        ).toProgressSnapshot()

        assertEquals(1, snapshot.perfectSessions)
    }
}
