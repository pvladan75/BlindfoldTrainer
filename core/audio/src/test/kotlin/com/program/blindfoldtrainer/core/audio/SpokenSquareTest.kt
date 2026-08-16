package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenSquareTest {

    @Test
    fun `prepoznaje polje zapisano ciframa`() {
        assertEquals(Square.fromAlgebraic("e4"), parseSpokenSquare("e4"))
    }

    @Test
    fun `prepoznaje broj izgovoren recju`() {
        assertEquals(Square.fromAlgebraic("e4"), parseSpokenSquare("e four"))
        assertEquals(Square.fromAlgebraic("h8"), parseSpokenSquare("h eight"))
        assertEquals(Square.fromAlgebraic("a1"), parseSpokenSquare("A One"))
    }

    @Test
    fun `podnosi visak razmaka`() {
        assertEquals(Square.fromAlgebraic("c3"), parseSpokenSquare("  c   three  "))
    }

    @Test
    fun `odbija ono sto nije polje`() {
        assertNull(parseSpokenSquare("zdravo"))
        assertNull(parseSpokenSquare("j9"))
        assertNull(parseSpokenSquare(""))
    }

    @Test
    fun `polje se izgovara slovo pa broj, na jeziku govora`() {
        val english = VoiceLanguages.specFor(VoiceLanguage.ENGLISH).words
        val german = VoiceLanguages.specFor(VoiceLanguage.GERMAN).words

        assertEquals("e four", Square.fromAlgebraic("e4")!!.spoken(english))
        assertEquals("e vier", Square.fromAlgebraic("e4")!!.spoken(german))
    }

    @Test
    fun `izgovoreno se moze procitati nazad, na svakom jeziku`() {
        // Ista tabela služi oba smera, pa ovo mora da važi za svako polje i
        // svaki jezik — inače bi aplikacija govorila ono što sama ne razume.
        VoiceLanguage.entries.forEach { language ->
            val words = VoiceLanguages.specFor(language).words

            Square.ALL.forEach { square ->
                assertEquals(
                    "${language.name}, $square",
                    square,
                    parseSpokenSquare(square.spoken(words), words)
                )
            }
        }
    }
}
