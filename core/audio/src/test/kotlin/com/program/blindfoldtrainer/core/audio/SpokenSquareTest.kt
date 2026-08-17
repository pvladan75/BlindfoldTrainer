package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Language
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
        val square = Square.fromAlgebraic("e4")!!

        assertEquals("e four", square.spoken(SpeechLanguages.wordsFor(Language.ENGLISH)))
        assertEquals("e vier", square.spoken(SpeechLanguages.wordsFor(Language.GERMAN)))
    }

    @Test
    fun `ono sto aplikacija kaze, ona i razume`() {
        // Govor i prepoznavanje su dve tabele; ovo ih drži usaglašenim. Kad se
        // razmimoiđu, aplikacija govori polje koje sama ne bi prepoznala.
        Language.entries.forEach { language ->
            val speech = Language.entries.first { it.code == language.code }
            val spokenWords = SpeechLanguages.wordsFor(speech)
            val heardWords = VoiceLanguages.specFor(language).words

            Square.ALL.forEach { square ->
                assertEquals(
                    "${language.name}, $square",
                    square,
                    parseSpokenSquare(square.spoken(spokenWords), heardWords)
                )
            }
        }
    }
}
