package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Perft prebrojava sve različite nizove legalnih poteza do zadate dubine.
 * Očekivane vrednosti su objavljene i opštepoznate, pa svako odstupanje znači
 * da generator negde greši — promašena rokada, en passant, promocija ili
 * vezana figura. Jedan test koji pokriva sva pravila odjednom.
 */
class PerftTest {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1L
        val moves = position.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(position.applyMove(it), depth - 1) }
    }

    private fun assertPerft(fen: String, expected: List<Long>) {
        val position = requireNotNull(Position.fromFen(fen)) { "Neispravan FEN: $fen" }
        expected.forEachIndexed { index, count ->
            val depth = index + 1
            assertEquals(count, perft(position, depth), "perft($depth) za \"$fen\"")
        }
    }

    @Test
    fun `pocetna pozicija`() {
        assertPerft(Fen.STARTING, listOf(20, 400, 8902, 197281))
    }

    @Test
    fun `kiwipete - rokada, en passant i vezane figure`() {
        assertPerft(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            listOf(48, 2039, 97862)
        )
    }

    @Test
    fun `zavrsnica sa en passant otkrivenim sahom`() {
        assertPerft(
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            listOf(14, 191, 2812, 43238)
        )
    }

    @Test
    fun `promocije sa uzimanjem`() {
        assertPerft(
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            listOf(6, 264, 9467)
        )
    }

    @Test
    fun `pozicija bez rokade za crnog`() {
        assertPerft(
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            listOf(44, 1486, 62379)
        )
    }
}
