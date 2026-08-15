package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgnTest {

    private val scholarsMate = """
        [Event "Proba"]
        [Site "Beograd"]
        [Date "2026.08.16"]
        [White "Beli"]
        [Black "Crni"]
        [Result "1-0"]

        1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
    """.trimIndent()

    @Test
    fun `zaglavlja i potezi`() {
        val game = requireNotNull(Pgn.parse(scholarsMate))

        assertEquals("Beli", game.white)
        assertEquals("Crni", game.black)
        assertEquals("Proba", game.event)
        assertEquals("1-0", game.result)
        assertEquals(7, game.plyCount)
        assertEquals(listOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7#"), game.sanMoves)
    }

    @Test
    fun `pozicija posle zadatog broja poluhodova`() {
        val game = requireNotNull(Pgn.parse(scholarsMate))

        assertEquals(Position.STARTING, game.positionAfter(0))
        assertTrue(game.positionAfter(game.plyCount).isCheckmate)
        // Van opsega se odseca umesto da puca.
        assertEquals(game.positionAfter(game.plyCount), game.positionAfter(999))
    }

    @Test
    fun `komentari, varijante i oznake se preskacu`() {
        val text = """
            [Event "Proba"]
            [Result "*"]

            1. e4 {najčešći prvi potez} e5 2. Nf3 ${'$'}1 (2. Bc4 Nc6) 2... Nc6 *
        """.trimIndent()

        val game = requireNotNull(Pgn.parse(text))
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), game.sanMoves)
    }

    @Test
    fun `partija sa nemogucim potezom se odbacuje`() {
        val text = """
            [Event "Proba"]
            [Result "*"]

            1. e4 e5 2. Nf3 Nf6 3. Qh9 *
        """.trimIndent()

        assertNull(Pgn.parse(text))
    }

    @Test
    fun `partija koja ne krece iz pocetne pozicije se preskace`() {
        val text = """
            [Event "Proba"]
            [SetUp "1"]
            [FEN "4k3/8/8/8/8/8/8/4K2R w K - 0 1"]

            1. O-O *
        """.trimIndent()

        assertNull(Pgn.parse(text))
    }

    @Test
    fun `vise partija iz jednog fajla`() {
        val text = scholarsMate + "\n\n" + scholarsMate.replace("Beli", "Drugi")

        val games = Pgn.parseAll(text)
        assertEquals(2, games.size)
        assertEquals("Beli", games[0].white)
        assertEquals("Drugi", games[1].white)
    }

    @Test
    fun `neispravna partija ne ruse ostale`() {
        val broken = """
            [Event "Pokvarena"]
            [Result "*"]

            1. e4 Qh9 *
        """.trimIndent()

        val games = Pgn.parseAll(broken + "\n\n" + scholarsMate)
        assertEquals(1, games.size)
        assertEquals("Beli", games[0].white)
    }
}
