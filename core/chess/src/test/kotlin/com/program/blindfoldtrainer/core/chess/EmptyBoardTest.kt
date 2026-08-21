package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Domet po praznoj tabli — geometrija bez pozicije. */
class EmptyBoardTest {

    private fun squares(vararg names: String) =
        names.map { Square.fromAlgebraic(it)!! }.toSet()

    private fun reach(from: String, type: PieceType) =
        EmptyBoard.reach(Square.fromAlgebraic(from)!!, type).toSet()

    private fun onFile(from: String, type: PieceType, file: Char) =
        EmptyBoard.reachOnFile(Square.fromAlgebraic(from)!!, type, file - 'a').toSet()

    @Test
    fun `top sa e5 drzi svoj red i svoju kolonu`() {
        val expected = squares(
            "a5", "b5", "c5", "d5", "f5", "g5", "h5",
            "e1", "e2", "e3", "e4", "e6", "e7", "e8"
        )
        assertEquals(expected, reach("e5", PieceType.ROOK))
    }

    @Test
    fun `lovac sa e5 drzi obe dijagonale`() {
        val expected = squares(
            "d6", "c7", "b8",
            "f6", "g7", "h8",
            "d4", "c3", "b2", "a1",
            "f4", "g3", "h2"
        )
        assertEquals(expected, reach("e5", PieceType.BISHOP))
    }

    @Test
    fun `dama je zbir topa i lovca`() {
        val from = Square.fromAlgebraic("e5")!!
        val expected = EmptyBoard.reach(from, PieceType.ROOK).toSet() +
            EmptyBoard.reach(from, PieceType.BISHOP).toSet()

        assertEquals(expected, reach("e5", PieceType.QUEEN))
    }

    @Test
    fun `kralj ide jedan korak, iz ugla samo tri`() {
        assertEquals(squares("a2", "b1", "b2"), reach("a1", PieceType.KING))
        assertEquals(8, reach("e5", PieceType.KING).size)
    }

    /** Pešak nije šetač: potez mu zavisi od boje i od toga ima li šta da uzme. */
    @Test
    fun `pesak po praznoj tabli ne daje domet`() {
        assertTrue(reach("e5", PieceType.PAWN).isEmpty())
    }

    /**
     * Pitanje zadatka „Domet na liniji", doslovno. Lovac sa e5 dodiruje b-liniju
     * na dva mesta — po jedno sa svake dijagonale.
     */
    @Test
    fun `lovac sa e5 stize na b8 i b2`() {
        assertEquals(squares("b8", "b2"), onFile("e5", PieceType.BISHOP, 'b'))
    }

    /** Dami se na istu liniju pridružuje i potez topa. */
    @Test
    fun `dama sa e5 stize i na b5`() {
        assertEquals(squares("b8", "b5", "b2"), onFile("e5", PieceType.QUEEN, 'b'))
    }

    @Test
    fun `top sa e5 dodiruje b-liniju samo na b5`() {
        assertEquals(squares("b5"), onFile("e5", PieceType.ROOK, 'b'))
    }

    /**
     * **Prazan odgovor je valjan odgovor.** Skakač sa e5 stiže na kolone c, d, f
     * i g — na b-liniju nijednim potezom. Bez ovoga bi se zadatak mogao rešavati
     * nagađanjem, jer bi svako pitanje imalo bar jedno tačno polje.
     */
    @Test
    fun `skakac sa e5 ne stize ni na jedno polje b-linije`() {
        assertTrue(onFile("e5", PieceType.KNIGHT, 'b').isEmpty())
        assertEquals(squares("c6", "c4"), onFile("e5", PieceType.KNIGHT, 'c'))
    }

    @Test
    fun `domet po redu se racuna isto kao po koloni`() {
        val from = Square.fromAlgebraic("e5")!!
        assertEquals(
            squares("b8", "h8"),
            EmptyBoard.reachOnRank(from, PieceType.BISHOP, 7).toSet()
        )
    }

    /** Sa svakog polja se ide bar nekud — ni jedan ugao nije mrtav. */
    @Test
    fun `nijedna figura sa nijednog polja nije bez poteza`() {
        for (square in Square.ALL) {
            for (type in listOf(PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)) {
                assertTrue(
                    EmptyBoard.reach(square, type).isNotEmpty(),
                    "$type sa $square nema nijedan potez"
                )
            }
        }
    }
}
