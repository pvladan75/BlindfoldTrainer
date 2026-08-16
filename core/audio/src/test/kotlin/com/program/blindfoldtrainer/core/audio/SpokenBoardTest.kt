package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.SpeechLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenBoardTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private val serbian = SpeechLanguages.wordsFor(SpeechLanguage.SERBIAN)

    /** Pozicija iz korisnikovog primera. */
    private val position = Board.of(
        mapOf(
            square("e5") to Piece(PieceType.QUEEN, Color.WHITE),
            square("e2") to Piece(PieceType.KING, Color.WHITE),
            square("h6") to Piece(PieceType.KING, Color.BLACK)
        )
    )

    @Test
    fun `pozicija se cita beli pa crni, kralj pa dama`() {
        assertEquals(
            "beli kralj na e dva, bela dama na e pet. crni kralj na ha šest",
            position.spoken(serbian)
        )
    }

    @Test
    fun `rod se slaze uz figuru`() {
        // Dama je jedina ženskog roda u srpskom — otud „bela", a ne „beli".
        val text = position.spoken(serbian)
        assertTrue(text, "bela dama" in text)
        assertTrue(text, "beli kralj" in text)
    }

    @Test
    fun `figura i polje su odvojeni delovi, zbog pauze`() {
        assertEquals(
            listOf(
                "beli kralj na", "e dva,",
                "bela dama na", "e pet.",
                "crni kralj na", "ha šest"
            ),
            position.spokenParts(serbian)
        )
    }

    @Test
    fun `spojeno i po delovima daju isti tekst`() {
        assertEquals(position.spoken(serbian), position.spokenParts(serbian).joinToString(" "))
    }

    @Test
    fun `prazna tabla se ne izgovara`() {
        assertEquals("", Board.EMPTY.spoken(serbian))
    }

    @Test
    fun `strana bez figura se preskace`() {
        val onlyWhite = Board.of(mapOf(square("a1") to Piece(PieceType.KING, Color.WHITE)))
        assertEquals("beli kralj na a jedan", onlyWhite.spoken(serbian))
    }

    @Test
    fun `svaki jezik ume da izgovori svaku figuru i boju`() {
        SpeechLanguage.entries.forEach { language ->
            val words = SpeechLanguages.wordsFor(language)

            PieceType.entries.forEach { type ->
                Color.entries.forEach { color ->
                    val text = words.describe(Piece(type, color), "x")
                    assertTrue("${language.name} $type $color: \"$text\"", text.isNotBlank())
                    assertTrue("${language.name} $type $color: \"$text\"", "x" in text)
                }
            }
        }
    }

    @Test
    fun `redosled je uvek isti, da se pozicija pamti kao niz`() {
        val shuffled = Board.of(
            mapOf(
                square("h6") to Piece(PieceType.KING, Color.BLACK),
                square("e5") to Piece(PieceType.QUEEN, Color.WHITE),
                square("e2") to Piece(PieceType.KING, Color.WHITE)
            )
        )

        assertEquals(position.spoken(serbian), shuffled.spoken(serbian))
    }
}
