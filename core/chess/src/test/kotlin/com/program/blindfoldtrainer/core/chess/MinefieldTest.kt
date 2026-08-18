package com.program.blindfoldtrainer.core.chess

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Skakač kroz minsko polje: put mora da postoji i mora da bude bezbedan.
 *
 * Za razliku od prazne table, gde je svako polje dostupno iz svakog, ovde put
 * ume i **da ne postoji** — pa se zadatak mora proveriti pre nego što se ponudi.
 */
class MinefieldTest {

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

    @Test
    fun `put ide samo kroz bezbedna polja`() {
        val board = board(
            "c3" to Piece(PieceType.QUEEN, Color.BLACK),
            "f6" to Piece(PieceType.ROOK, Color.BLACK)
        )

        val path = board.safeKnightPath(square("a1"), square("h8"), avoidAttacked = false)

        assertTrue(path.isNotEmpty(), "put mora da postoji")
        assertEquals(square("a1"), path.first())
        assertEquals(square("h8"), path.last())

        path.forEach { step ->
            assertTrue(board.isSafeForKnight(step, avoidAttacked = false), "$step nije bezbedno")
        }

        path.zipWithNext().forEach { (from, to) ->
            assertTrue(KnightPath.isKnightMove(from, to), "$from -> $to nije skok skakača")
        }
    }

    /** Kad je cilj zauzet ili napadnut, zadatak nema rešenje i to se mora videti. */
    @Test
    fun `nedostizan cilj daje prazan put`() {
        val board = board("h8" to Piece(PieceType.PAWN, Color.BLACK))

        assertTrue(board.safeKnightPath(square("a1"), square("h8"), false).isEmpty())
    }

    /**
     * Skakač zatvoren sopstvenim skokovima — svih osam odredišta držano.
     * Dama sa c3 pokriva ceo skakačev krug oko a1 preko dijagonale i linija.
     */
    @Test
    fun `zatvoren skakac nema put`() {
        val board = board(
            "b3" to Piece(PieceType.PAWN, Color.BLACK),
            "c2" to Piece(PieceType.PAWN, Color.BLACK)
        )

        assertTrue(board.safeKnightPath(square("a1"), square("h8"), false).isEmpty())
    }

    /** Zadatak se nudi tek kad je proveren: nerešiv raspored se odbacuje. */
    @Test
    fun `napravljen zadatak uvek ima resenje`() {
        repeat(30) { seed ->
            val puzzle = randomMinefield(
                pieceCount = 6,
                avoidAttacked = true,
                minMoves = 2,
                random = Random(seed.toLong())
            )

            assertNotNull(puzzle, "zadatak nije napravljen za seme $seed")
            assertTrue(puzzle.solution.isNotEmpty())
            assertEquals(puzzle.start, puzzle.solution.first())
            assertEquals(puzzle.target, puzzle.solution.last())
            assertTrue(puzzle.optimalMoves >= 2, "prekratko")

            puzzle.solution.forEach { step ->
                assertTrue(puzzle.isSafe(step), "$step nije bezbedno")
            }
        }
    }
}
