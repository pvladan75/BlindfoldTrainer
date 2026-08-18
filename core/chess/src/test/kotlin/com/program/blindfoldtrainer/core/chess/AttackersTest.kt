package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ko gađa dato polje.
 *
 * Odgovara na drugo pitanje od `isAttackedBy`: za pravila je dovoljno znati da
 * li je polje napadnuto, za vežbu je potrebno **odakle** — jer se u partiji
 * naslepo ne zaboravlja gde figure stoje, nego šta drže.
 */
class AttackersTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private fun board(vararg pieces: Pair<String, Piece>) =
        Board.of(pieces.associate { (notation, piece) -> square(notation) to piece })

    @Test
    fun `pesak gadja dijagonalu i kad je polje prazno`() {
        val board = board("d4" to Piece(PieceType.PAWN, Color.WHITE))

        assertEquals(setOf(square("d4")), board.attackersOf(square("e5"), Color.WHITE))
        assertEquals(setOf(square("d4")), board.attackersOf(square("c5"), Color.WHITE))

        // Pravo ispred sebe pešak ne gađa ništa — tamo se samo pomera.
        assertTrue(board.attackersOf(square("d5"), Color.WHITE).isEmpty())
    }

    @Test
    fun `skakac gadja svojih osam polja`() {
        val board = board("d4" to Piece(PieceType.KNIGHT, Color.BLACK))

        assertEquals(setOf(square("d4")), board.attackersOf(square("e6"), Color.BLACK))
        assertEquals(setOf(square("d4")), board.attackersOf(square("f5"), Color.BLACK))
        assertTrue(board.attackersOf(square("d5"), Color.BLACK).isEmpty())
    }

    @Test
    fun `klizece figure zaustavlja prva figura na putu`() {
        val open = board("a1" to Piece(PieceType.ROOK, Color.WHITE))
        assertEquals(setOf(square("a1")), open.attackersOf(square("a8"), Color.WHITE))

        val blocked = board(
            "a1" to Piece(PieceType.ROOK, Color.WHITE),
            "a4" to Piece(PieceType.PAWN, Color.BLACK)
        )
        assertTrue(blocked.attackersOf(square("a8"), Color.WHITE).isEmpty())

        // Figura koja smeta i sama je napadnuta.
        assertEquals(setOf(square("a1")), blocked.attackersOf(square("a4"), Color.WHITE))
    }

    /** Ovo je slučaj zbog kog zadatak i postoji: više figura gađa isto polje. */
    @Test
    fun `vise napadaca na istom polju`() {
        val board = board(
            "d6" to Piece(PieceType.PAWN, Color.BLACK),
            // g7, ne f7: dijagonala do e5 ide g7-f6-e5. Sa f7 se gađa d5.
            "g7" to Piece(PieceType.BISHOP, Color.BLACK),
            "e8" to Piece(PieceType.ROOK, Color.BLACK),
            "h5" to Piece(PieceType.QUEEN, Color.WHITE)
        )

        assertEquals(
            setOf(square("d6"), square("g7"), square("e8")),
            board.attackersOf(square("e5"), Color.BLACK)
        )
    }

    @Test
    fun `sopstvena boja se ne broji`() {
        val board = board(
            "d4" to Piece(PieceType.PAWN, Color.WHITE),
            "d6" to Piece(PieceType.PAWN, Color.BLACK)
        )

        assertEquals(setOf(square("d4")), board.attackersOf(square("e5"), Color.WHITE))
        assertEquals(setOf(square("d6")), board.attackersOf(square("e5"), Color.BLACK))
    }

    /** Slaže se sa postojećom proverom; dva odgovora na isto pitanje ne smeju da se raziđu. */
    @Test
    fun `spisak napadaca se slaze sa isAttackedBy`() {
        val board = Position.STARTING.board

        Square.ALL.forEach { target ->
            Color.entries.forEach { color ->
                assertEquals(
                    board.isAttackedBy(target, color),
                    board.attackersOf(target, color).isNotEmpty(),
                    "$target $color"
                )
            }
        }
    }
}
