package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rokada je mesto gde je stara aplikacija imala najviše rupa: nije proveravala
 * ni da top postoji, ni da kralj nije u šahu, ni da ne prelazi preko
 * napadnutog polja.
 */
class CastlingTest {

    private fun castlingDestinations(fen: String): Set<String> {
        val position = requireNotNull(Position.fromFen(fen))
        val kingSquare = requireNotNull(position.board.kingSquare(position.sideToMove))
        return position.legalMoves()
            .filter { it.from == kingSquare && kotlin.math.abs(it.to.fileIndex - it.from.fileIndex) == 2 }
            .map { it.to.toString() }
            .toSet()
    }

    @Test
    fun `obe rokade su moguce kad je put cist`() {
        val destinations = castlingDestinations("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertEquals(setOf("g1", "c1"), destinations)
    }

    @Test
    fun `nema rokade iz saha`() {
        // Crni top na e8 drži belog kralja u šahu duž e-linije.
        val destinations = castlingDestinations("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertTrue(destinations.isEmpty(), "iz šaha se ne rokira ni na jednu stranu")
    }

    @Test
    fun `nema rokade preko napadnutog polja`() {
        // Crni top na f8 napada f1 — polje preko kog kralj prelazi na malu rokadu.
        val destinations = castlingDestinations("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertFalse("g1" in destinations, "kralj bi prešao preko napadnutog f1")
        assertTrue("c1" in destinations, "velika rokada nije ugrožena")
    }

    @Test
    fun `nema rokade kad top ne postoji`() {
        // Prava kažu KQ, ali topova nema na tabli.
        val destinations = castlingDestinations("4k3/8/8/8/8/8/8/4K3 w KQ - 0 1")

        assertTrue(destinations.isEmpty(), "bez topa nema rokade koliko god prava pisala")
    }

    @Test
    fun `velika rokada je moguca i kad je b1 napadnut`() {
        // Crni top na b8 napada b1, ali kralj tuda ne prolazi (e1-d1-c1).
        val destinations = castlingDestinations("1r6/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertTrue("c1" in destinations, "b1 nije na kraljevom putu")
    }

    @Test
    fun `rokada pomera i topa`() {
        val position = requireNotNull(Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        val after = position.applyMove(Move(Square.of('e', 1)!!, Square.of('g', 1)!!))

        assertEquals(Piece(PieceType.KING, Color.WHITE), after.board[Square.of('g', 1)!!])
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), after.board[Square.of('f', 1)!!])
        assertNull(after.board[Square.of('h', 1)!!], "top je napustio h1")
        assertNull(after.board[Square.of('e', 1)!!], "kralj je napustio e1")
    }

    @Test
    fun `pomeranje kralja gasi oba prava`() {
        val position = requireNotNull(Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        val after = position.applyMove(Move(Square.of('e', 1)!!, Square.of('e', 2)!!))

        assertFalse(after.castlingRights.whiteKingSide)
        assertFalse(after.castlingRights.whiteQueenSide)
    }

    @Test
    fun `pojeden top na svom polju gasi pravo protivniku`() {
        // Beli top sa a1 uzima crnog topa na a8.
        val position = requireNotNull(Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        val after = position.applyMove(Move(Square.of('a', 1)!!, Square.of('a', 8)!!))

        assertFalse(after.castlingRights.blackQueenSide, "crni je izgubio veliku rokadu")
        assertTrue(after.castlingRights.blackKingSide, "mala rokada crnog je netaknuta")
    }
}
