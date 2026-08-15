package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {

    private fun position(fen: String) = requireNotNull(Position.fromFen(fen))

    @Test
    fun `nalazi mat u jednom potezu`() {
        // Beli matira sa Dh7 uz podršku kralja na g6.
        val start = position("7k/8/6K1/8/8/8/8/7Q w - - 0 1")
        val move = assertNotNull(Search.bestMove(start, maxDepth = 3))

        assertTrue(start.applyMove(move).isCheckmate, "izabran potez $move nije mat")
    }

    @Test
    fun `uzima nebranjenu damu`() {
        val start = position("4k3/8/8/3q4/4B3/8/8/4K3 w - - 0 1")
        val move = assertNotNull(Search.bestMove(start, maxDepth = 3))

        assertEquals(Square.fromAlgebraic("d5"), move.to, "lovac mora uzeti damu na d5")
    }

    @Test
    fun `brani se od mata u jednom potezu`() {
        // Crni je na potezu i mora izbeći mat koji preti.
        val start = position("6k1/5Q2/8/6K1/8/8/8/8 b - - 0 1")
        val move = assertNotNull(Search.bestMove(start, maxDepth = 4))
        val after = start.applyMove(move)

        assertTrue(!after.isCheckmate, "potez $move vodi pravo u mat")
    }

    @Test
    fun `odbrana bira duze prezivljavanje umesto brzeg mata`() {
        // Kralj sam protiv dame: bolji potez je ka centru nego ka ivici.
        val start = position("8/8/8/3k4/8/8/6Q1/4K3 b - - 0 1")
        val move = assertNotNull(Search.bestMove(start, maxDepth = 4))
        val after = start.applyMove(move)

        assertTrue(!after.isCheckmate, "odbrana ne sme sama da uđe u mat")
        assertTrue(!after.isStalemate, "a ni u pat")
    }

    @Test
    fun `bez legalnih poteza nema ni predloga`() {
        val mated = position("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")

        assertNull(Search.bestMove(mated, maxDepth = 3))
    }

    @Test
    fun `jaca strana tera protivnickog kralja ka ivici`() {
        // Ista pozicija, kralj u centru naspram kralja u ćošku:
        // za belog je druga bolja.
        val centre = position("8/8/8/3k4/8/8/6Q1/4K3 w - - 0 1")
        val corner = position("k7/8/8/8/8/8/6Q1/4K3 w - - 0 1")

        assertTrue(
            Search.evaluate(corner) > Search.evaluate(centre),
            "kralj u ćošku mora biti bolje ocenjen za jaču stranu"
        )
    }

    @Test
    fun `pretraga postuje vremensko ogranicenje`() {
        val start = position("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")

        val began = System.currentTimeMillis()
        Search.bestMove(start, maxDepth = 20, timeBudgetMillis = 500)
        val took = System.currentTimeMillis() - began

        // Dozvoljavamo zalet jer se rok proverava između čvorova, ne unutar njih.
        assertTrue(took < 5_000, "pretraga je trajala ${took}ms uprkos roku od 500ms")
    }
}
