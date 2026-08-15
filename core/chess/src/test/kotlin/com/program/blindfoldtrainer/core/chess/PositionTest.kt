package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PositionTest {

    private fun sq(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    @Test
    fun `fen ide u oba smera bez gubitka`() {
        val fens = listOf(
            Fen.STARTING,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq e3 5 42"
        )

        for (fen in fens) {
            val position = assertNotNull(Position.fromFen(fen), "nije parsirano: $fen")
            assertEquals(fen, position.toFen())
        }
    }

    @Test
    fun `skraceni fen dobija podrazumevane brojace`() {
        val position = assertNotNull(Position.fromFen("8/8/8/8/8/8/8/K6k w - -"))

        assertEquals(0, position.halfmoveClock)
        assertEquals(1, position.fullmoveNumber)
    }

    @Test
    fun `en passant uklanja pesaka pored odredisnog polja`() {
        // Beli pešak na e5, crni upravo odigrao d7-d5.
        val position = assertNotNull(Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"))
        val after = position.applyMove(Move(sq("e5"), sq("d6")))

        assertEquals(Piece(PieceType.PAWN, Color.WHITE), after.board[sq("d6")])
        assertNull(after.board[sq("d5")], "pojedeni pešak je stajao na d5, ne na d6")
        assertNull(after.board[sq("e5")])
    }

    @Test
    fun `dvokoracni skok pesaka otvara en passant polje`() {
        val after = Position.STARTING.applyMove(Move(sq("e2"), sq("e4")))

        assertEquals(sq("e3"), after.enPassantTarget)
    }

    @Test
    fun `en passant polje traje samo jedan potez`() {
        val after = Position.STARTING
            .applyMove(Move(sq("e2"), sq("e4")))
            .applyMove(Move(sq("e7"), sq("e5")))
            .applyMove(Move(sq("g1"), sq("f3")))

        assertNull(after.enPassantTarget)
    }

    @Test
    fun `promocija menja tip figure`() {
        val position = assertNotNull(Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
        val after = position.applyMove(Move(sq("a7"), sq("a8"), PieceType.QUEEN))

        assertEquals(Piece(PieceType.QUEEN, Color.WHITE), after.board[sq("a8")])
    }

    @Test
    fun `brojac poluhodova se resetuje na potez pesakom i na uzimanje`() {
        val quiet = assertNotNull(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 7 20"))
        assertEquals(8, quiet.applyMove(Move(sq("a1"), sq("a2"))).halfmoveClock, "tih potez povećava brojač")

        val pawn = assertNotNull(Position.fromFen("4k3/8/8/8/8/8/P7/4K3 w - - 7 20"))
        assertEquals(0, pawn.applyMove(Move(sq("a2"), sq("a3"))).halfmoveClock, "potez pešakom resetuje")

        val capture = assertNotNull(Position.fromFen("4k3/8/8/8/8/8/r7/R3K3 w - - 7 20"))
        assertEquals(0, capture.applyMove(Move(sq("a1"), sq("a2"))).halfmoveClock, "uzimanje resetuje")
    }

    @Test
    fun `broj poteza raste tek posle crnog`() {
        val afterWhite = Position.STARTING.applyMove(Move(sq("e2"), sq("e4")))
        assertEquals(1, afterWhite.fullmoveNumber)

        val afterBlack = afterWhite.applyMove(Move(sq("e7"), sq("e5")))
        assertEquals(2, afterBlack.fullmoveNumber)
    }

    @Test
    fun `vezana figura ne sme da se pomeri`() {
        // Beli skakač na e2 je vezan crnim topom na e8 za kralja na e1.
        val position = assertNotNull(Position.fromFen("4r3/8/8/8/8/8/4N3/4K3 w - - 0 1"))
        val knightMoves = position.legalMoves().filter { it.from == sq("e2") }

        assertEquals(emptyList(), knightMoves, "skakač je vezan, ne sme nikuda")
    }

    @Test
    fun `mat nema legalnih poteza`() {
        // Mat u ćošku — kralj na h8, dama h7 podržana kraljem g6.
        val position = assertNotNull(Position.fromFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1"))

        assertEquals(emptyList(), position.legalMoves())
        assertEquals(true, position.isInCheck, "mat znači da je kralj u šahu")
    }

    @Test
    fun `pat nema legalnih poteza ali nema ni saha`() {
        val position = assertNotNull(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))

        assertEquals(emptyList(), position.legalMoves())
        assertEquals(false, position.isInCheck, "pat znači da kralj nije u šahu")
    }
}
