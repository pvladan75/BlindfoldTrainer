package com.program.blindfoldtrainer.feature.followgame

import com.program.blindfoldtrainer.core.chess.Pgn
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Provera sadržaja pre pakovanja: partija koja se ne da odigrati do kraja ne sme
 * da stigne na uređaj. Čita se isti fajl koji ide u `assets`.
 */
class GameContentTest {

    private val pgnFile = File("src/main/assets/games.pgn")

    private val games by lazy {
        assertTrue("nema fajla: ${pgnFile.absolutePath}", pgnFile.exists())
        Pgn.parseAll(pgnFile.readText())
    }

    @Test
    fun `sve partije iz sadrzaja se citaju`() {
        // Broj zapisa u fajlu mora biti i broj partija koje su prošle pravila —
        // Pgn.parseAll ćutke izbacuje one sa nemogućim potezom.
        val recorded = pgnFile.readLines().count { it.startsWith("[Event ") }

        assertEquals("neke partije nisu prošle kroz pravila", recorded, games.size)
        assertTrue("sadržaj je prazan", games.isNotEmpty())
    }

    @Test
    fun `partije imaju igrace, ishod i dovoljno poteza`() {
        games.forEach { game ->
            assertTrue("partija bez belog", game.white.isNotBlank())
            assertTrue("partija bez crnog", game.black.isNotBlank())
            assertTrue("čudan ishod: ${game.result}", game.result in setOf("1-0", "0-1"))
            // Najteža težina traži osam pitanja na svakih osam poluhodova.
            assertTrue("prekratka partija: ${game.plyCount}", game.plyCount >= 50)
        }
    }

    @Test
    fun `svaka pozicija u svakoj partiji ume da ponudi pitanje`() {
        val random = Random(1)
        games.forEach { game ->
            for (ply in 0..game.plyCount step 8) {
                val position = game.positionAfter(ply)
                val question = questionFor(position, random)
                assertNotNull("nema pitanja posle $ply poluhodova", question)
                assertEquals(
                    "odgovor ne stoji na svom polju",
                    question!!.piece,
                    position.board[question.square]
                )
            }
        }
    }

    @Test
    fun `pitanje bira figuru koja je jedina te vrste i boje`() {
        val random = Random(2)
        games.take(10).forEach { game ->
            for (ply in 0..game.plyCount step 5) {
                val position = game.positionAfter(ply)
                val question = questionFor(position, random) ?: continue
                val sameKind = position.board.occupied().count { (_, piece) -> piece == question.piece }
                assertEquals("pitanje ima više od jednog odgovora", 1, sameKind)
            }
        }
    }

    @Test
    fun `kralj se bira tek kad druge jedinstvene figure nema`() {
        // Na početnoj poziciji su jedinstveni samo kraljevi i dame.
        val question = questionFor(Position.STARTING, Random(3))
        assertNotNull(question)
        assertEquals(PieceType.QUEEN, question!!.piece.type)
    }
}
