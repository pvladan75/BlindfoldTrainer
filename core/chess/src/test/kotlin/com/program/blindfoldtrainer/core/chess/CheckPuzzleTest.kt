package com.program.blindfoldtrainer.core.chess

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Skakač koji mora da da šah, a da dotle ostane živ.
 *
 * Za razliku od prazne table, gde je svako polje dostupno iz svakog, ovde put
 * ume i **da ne postoji** — pa se zadatak mora proveriti pre nego što se ponudi.
 */
class CheckPuzzleTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private fun board(vararg pieces: Pair<String, Piece>) =
        Board.of(pieces.associate { (notation, piece) -> square(notation) to piece })

    @Test
    fun `zauzeto polje nije bezbedno ni u lakoj varijanti`() {
        val board = board("c3" to Piece(PieceType.PAWN, Color.BLACK))

        assertFalse(board.isSafeForKnight(square("c3"), avoidAttacked = false))
        assertTrue(board.isSafeForKnight(square("d3"), avoidAttacked = false))
    }

    @Test
    fun `napadnuto polje otpada tek u tezoj varijanti`() {
        // Crni pešak sa c3 drži b2 i d2.
        val board = board("c3" to Piece(PieceType.PAWN, Color.BLACK))

        assertTrue(board.isSafeForKnight(square("b2"), avoidAttacked = false))
        assertFalse(board.isSafeForKnight(square("b2"), avoidAttacked = true))
    }

    /**
     * Skakačev skok je uzajaman: sa kojih polja on gađa kralja, ta ista polja
     * čine njegov krug oko kralja.
     */
    @Test
    fun `polja sa kojih se daje sah su skakacev krug oko kralja`() {
        val board = board("e5" to Piece(PieceType.KING, Color.BLACK))

        val checking = board.checkingSquaresFor(square("e5"), avoidAttacked = false)

        assertEquals(KnightPath.movesFrom(square("e5")).toSet(), checking)
        assertTrue(square("f7") in checking)
        assertFalse(square("e6") in checking, "susedno polje nije skok")
    }

    @Test
    fun `zauzeto polje ne moze da da sah`() {
        val board = board(
            "e5" to Piece(PieceType.KING, Color.BLACK),
            "f7" to Piece(PieceType.ROOK, Color.BLACK)
        )

        val checking = board.checkingSquaresFor(square("e5"), avoidAttacked = false)

        assertFalse(square("f7") in checking, "tamo stoji figura")
    }

    @Test
    fun `put ide samo kroz bezbedna polja`() {
        val board = board(
            "e5" to Piece(PieceType.KING, Color.BLACK),
            "c3" to Piece(PieceType.QUEEN, Color.BLACK)
        )

        val targets = board.checkingSquaresFor(square("e5"), avoidAttacked = false)
        val path = board.safeKnightPath(square("a1"), targets, avoidAttacked = false)

        assertTrue(path.isNotEmpty(), "put mora da postoji")
        assertEquals(square("a1"), path.first())
        assertTrue(path.last() in targets, "put mora da završi šahom")

        path.forEach { step ->
            assertTrue(board.isSafeForKnight(step, avoidAttacked = false), "$step nije bezbedno")
        }
        path.zipWithNext().forEach { (from, to) ->
            assertTrue(KnightPath.isKnightMove(from, to), "$from -> $to nije skok skakača")
        }
    }

    @Test
    fun `zatvoren skakac nema put`() {
        val board = board(
            "e5" to Piece(PieceType.KING, Color.BLACK),
            "b3" to Piece(PieceType.PAWN, Color.BLACK),
            "c2" to Piece(PieceType.PAWN, Color.BLACK)
        )

        val targets = board.checkingSquaresFor(square("e5"), avoidAttacked = false)

        assertTrue(board.safeKnightPath(square("a1"), targets, false).isEmpty())
    }

    /** Zadatak se nudi tek kad je proveren: nerešiv raspored se odbacuje. */
    @Test
    fun `napravljen zadatak uvek ima resenje`() {
        repeat(30) { seed ->
            val puzzle = randomCheckPuzzle(
                pieceCount = 5,
                avoidAttacked = true,
                minMoves = 2,
                random = Random(seed.toLong())
            )

            assertNotNull(puzzle, "zadatak nije napravljen za seme $seed")
            assertTrue(puzzle.solution.isNotEmpty())
            assertEquals(puzzle.start, puzzle.solution.first())
            assertTrue(puzzle.isCheck(puzzle.solution.last()), "put ne završava šahom")
            assertTrue(puzzle.optimalMoves >= 2, "prekratko")
            assertFalse(puzzle.isCheck(puzzle.start), "šah već na početku nije zadatak")

            puzzle.solution.forEach { step ->
                assertTrue(puzzle.isSafe(step), "$step nije bezbedno")
            }
        }
    }

    /** Kralj je uvek na tabli — bez njega zadatak nema cilj. */
    @Test
    fun `kralj je deo pozicije`() {
        val puzzle = requireNotNull(
            randomCheckPuzzle(pieceCount = 4, avoidAttacked = false, random = Random(7))
        )

        assertEquals(Piece(PieceType.KING, Color.BLACK), puzzle.board[puzzle.king])
    }
}
