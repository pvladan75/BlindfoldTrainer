package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SanTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private fun position(fen: String) = requireNotNull(Position.fromFen(fen))

    private fun play(vararg san: String): Position =
        san.fold(Position.STARTING) { position, token ->
            position.applyMove(requireNotNull(San.parse(position, token)) { "ne može $token" })
        }

    @Test
    fun `potez pesakom i figurom`() {
        val afterE4 = requireNotNull(San.parse(Position.STARTING, "e4"))
        assertEquals(Move(square("e2"), square("e4")), afterE4)

        val afterNf3 = requireNotNull(San.parse(Position.STARTING, "Nf3"))
        assertEquals(Move(square("g1"), square("f3")), afterNf3)
    }

    @Test
    fun `malo slovo je pesak, veliko je figura`() {
        // "b4" je potez pešaka, "Bb4" bi bio lovac — razlika je samo u veličini slova.
        assertEquals(Move(square("b2"), square("b4")), San.parse(Position.STARTING, "b4"))
    }

    @Test
    fun `uzimanje, sah i mat se citaju sa ukrasima`() {
        // Školski mat: potez sa "#" mora da se pročita isto kao i bez njega.
        val position = play("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
        val mate = requireNotNull(San.parse(position, "Qxf7#"))
        assertEquals(Move(square("h5"), square("f7")), mate)
        assertEquals(true, position.applyMove(mate).isCheckmate)
    }

    @Test
    fun `obe rokade`() {
        val short = play("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        assertEquals(Move(square("e1"), square("g1")), San.parse(short, "O-O"))

        val long = play("d4", "d5", "Nc3", "Nc6", "Bf4", "Bf5", "Qd2", "Qd7")
        assertEquals(Move(square("e1"), square("c1")), San.parse(long, "O-O-O"))
    }

    @Test
    fun `nula umesto slova O u rokadi`() {
        val position = play("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        assertEquals(San.parse(position, "O-O"), San.parse(position, "0-0"))
    }

    @Test
    fun `razlikovanje po koloni i po redu`() {
        // Dva topa na a1 i h1 mogu na d1 — razlikuju se po koloni. Kralj je
        // sklonjen sa prvog reda, inače bi stajao između topova i odredišta.
        val rooks = position("4k3/8/8/8/4K3/8/8/R6R w - - 0 1")
        assertEquals(Move(square("a1"), square("d1")), San.parse(rooks, "Rad1"))
        assertEquals(Move(square("h1"), square("d1")), San.parse(rooks, "Rhd1"))

        // Dva topa u istoj koloni mogu na a3 — razlikuju se po redu.
        val stacked = position("4k3/8/8/8/R7/8/8/R3K3 w - - 0 1")
        assertEquals(Move(square("a1"), square("a3")), San.parse(stacked, "R1a3"))
        assertEquals(Move(square("a4"), square("a3")), San.parse(stacked, "R4a3"))
    }

    @Test
    fun `promocija`() {
        val position = position("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals(Move(square("a7"), square("a8"), PieceType.QUEEN), San.parse(position, "a8=Q"))
        assertEquals(Move(square("a7"), square("a8"), PieceType.KNIGHT), San.parse(position, "a8=N"))
    }

    @Test
    fun `en passant`() {
        val position = play("e4", "a6", "e5", "d5")
        val enPassant = requireNotNull(San.parse(position, "exd6"))
        assertEquals(Move(square("e5"), square("d6")), enPassant)
        // Pojedeni pešak stoji pored odredišnog polja, ne na njemu.
        assertNull(position.applyMove(enPassant).board[square("d5")])
    }

    @Test
    fun `nemoguc potez se ne cita`() {
        assertNull(San.parse(Position.STARTING, "e5"))
        assertNull(San.parse(Position.STARTING, "Qh5"))
        assertNull(San.parse(Position.STARTING, "O-O"))
        assertNull(San.parse(Position.STARTING, "bezveze"))
    }

    @Test
    fun `zapis i citanje su jedno drugom obrnuti`() {
        val positions = listOf(
            Position.STARTING,
            play("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"),
            position("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"),
            position("4k3/P6P/8/8/8/8/8/R3K3 w - - 0 1"),
            position("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")
        )

        for (position in positions) {
            for (move in position.legalMoves()) {
                val san = San.format(position, move)
                assertEquals(move, San.parse(position, san), "$san u poziciji $position")
            }
        }
    }

    @Test
    fun `zapis dodaje razlikovanje samo kad treba`() {
        assertEquals("Nf3", San.format(Position.STARTING, Move(square("g1"), square("f3"))))

        val rooks = position("4k3/8/8/8/4K3/8/8/R6R w - - 0 1")
        assertEquals("Rad1", San.format(rooks, Move(square("a1"), square("d1"))))
    }

    @Test
    fun `zapis oznacava sah i mat`() {
        val mate = play("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
        assertEquals("Qxf7#", San.format(mate, Move(square("h5"), square("f7"))))

        // Top na osmi red daje šah, ali kralj ima kuda — dakle "+", ne "#".
        val check = position("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        val rookCheck = Move(square("a1"), square("a8"))
        assertEquals("Ra8+", San.format(check, rookCheck))
        assertNotNull(San.parse(check, "Ra8+"))
    }
}
