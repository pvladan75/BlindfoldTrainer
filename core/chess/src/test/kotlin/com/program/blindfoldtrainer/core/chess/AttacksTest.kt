package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testovi za detekciju napada. Prvi test pokriva bag zbog kog je stara
 * aplikacija dozvoljavala kralju da stane na polje koje pešak brani.
 */
class AttacksTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    @Test
    fun `pesak napada prazno polje po dijagonali`() {
        val board = Board.of(mapOf(square("e4") to Piece(PieceType.PAWN, Color.WHITE)))

        assertTrue(board.isAttackedBy(square("d5"), Color.WHITE), "d5 je prazno ali pod napadom")
        assertTrue(board.isAttackedBy(square("f5"), Color.WHITE), "f5 je prazno ali pod napadom")
    }

    @Test
    fun `pesak ne napada polje ispred sebe`() {
        val board = Board.of(mapOf(square("e4") to Piece(PieceType.PAWN, Color.WHITE)))

        assertFalse(board.isAttackedBy(square("e5"), Color.WHITE), "pešak ne uzima unapred")
    }

    @Test
    fun `crni pesak napada nanize`() {
        val board = Board.of(mapOf(square("e5") to Piece(PieceType.PAWN, Color.BLACK)))

        assertTrue(board.isAttackedBy(square("d4"), Color.BLACK))
        assertTrue(board.isAttackedBy(square("f4"), Color.BLACK))
        assertFalse(board.isAttackedBy(square("d6"), Color.BLACK))
    }

    @Test
    fun `kralj ne sme na prazno polje koje brani pesak`() {
        // Beli kralj na e4, crni pešak na d6 — taj pešak brani c5 i e5.
        val position = requireNotNull(Position.fromFen("8/8/3p4/8/4K3/8/8/8 w - - 0 1"))
        val destinations = position.legalMoves().map { it.to.toString() }.toSet()

        assertFalse("e5" in destinations, "e5 je prazno, ali ga brani pešak sa d6")
        assertTrue("d5" in destinations, "d5 niko ne brani, tu kralj sme")
    }

    @Test
    fun `klizeca figura je zaustavljena preprekom`() {
        val board = Board.of(
            mapOf(
                square("a1") to Piece(PieceType.ROOK, Color.WHITE),
                square("a4") to Piece(PieceType.PAWN, Color.BLACK)
            )
        )

        assertTrue(board.isAttackedBy(square("a4"), Color.WHITE), "top napada figuru koja ga blokira")
        assertFalse(board.isAttackedBy(square("a5"), Color.WHITE), "iza prepreke top ne dopire")
    }

    @Test
    fun `skakac napada u obliku slova L`() {
        val board = Board.of(mapOf(square("d4") to Piece(PieceType.KNIGHT, Color.WHITE)))

        assertTrue(board.isAttackedBy(square("e6"), Color.WHITE))
        assertTrue(board.isAttackedBy(square("c2"), Color.WHITE))
        assertFalse(board.isAttackedBy(square("d5"), Color.WHITE))
    }
}
