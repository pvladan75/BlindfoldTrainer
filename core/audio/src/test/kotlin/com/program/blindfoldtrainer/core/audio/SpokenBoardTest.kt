package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenBoardTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    // Nemački, otkad je srpski izašao iz ponude: i on slaže rod uz figuru
    // („weiße Dame" naspram „weißer König"), pa test i dalje meri isto.
    private val german = SpeechLanguages.wordsFor(Language.GERMAN)

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
            "weißer König auf e zwei, weiße Dame auf e fünf. schwarzer König auf ha sechs",
            position.spoken(german)
        )
    }

    @Test
    fun `rod se slaze uz figuru`() {
        // Dame je ženskog roda — otud „weiße", a ne „weißer".
        val text = position.spoken(german)
        assertTrue(text, "weiße Dame" in text)
        assertTrue(text, "weißer König" in text)
    }

    @Test
    fun `figura i polje su odvojeni delovi, zbog pauze`() {
        assertEquals(
            listOf(
                "weißer König auf", "e zwei,",
                "weiße Dame auf", "e fünf.",
                "schwarzer König auf", "ha sechs"
            ),
            position.spokenParts(german)
        )
    }

    @Test
    fun `spojeno i po delovima daju isti tekst`() {
        assertEquals(position.spoken(german), position.spokenParts(german).joinToString(" "))
    }

    @Test
    fun `prazna tabla se ne izgovara`() {
        assertEquals("", Board.EMPTY.spoken(german))
    }

    @Test
    fun `strana bez figura se preskace`() {
        val onlyWhite = Board.of(mapOf(square("a1") to Piece(PieceType.KING, Color.WHITE)))
        assertEquals("weißer König auf a eins", onlyWhite.spoken(german))
    }

    @Test
    fun `svaki jezik ume da izgovori svaku figuru i boju`() {
        Language.entries.forEach { language ->
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

        assertEquals(position.spoken(german), shuffled.spoken(german))
    }
}
