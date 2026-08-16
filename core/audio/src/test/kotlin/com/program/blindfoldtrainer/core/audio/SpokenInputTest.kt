package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenInputTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    @Test
    fun `polje u jednom dahu`() {
        assertEquals(SpokenInput.Full(square("e4")), parseSpokenInput("e four"))
        assertEquals(SpokenInput.Full(square("h8")), parseSpokenInput("h eight"))
        assertEquals(SpokenInput.Full(square("a1")), parseSpokenInput("a one"))
    }

    @Test
    fun `veliko slovo i visak razmaka ne smetaju`() {
        assertEquals(SpokenInput.Full(square("e4")), parseSpokenInput("  E   Four "))
    }

    @Test
    fun `fonetska abeceda daje istu kolonu`() {
        assertEquals(SpokenInput.Full(square("b2")), parseSpokenInput("bravo two"))
        assertEquals(SpokenInput.Full(square("d4")), parseSpokenInput("delta four"))
        assertEquals(SpokenInput.Full(square("e5")), parseSpokenInput("echo five"))
    }

    @Test
    fun `sve fonetske reci pokrivaju kolone a do h`() {
        assertEquals(('a'..'h').toSet(), PHONETIC_FILES.values.toSet())

        PHONETIC_FILES.forEach { (word, file) ->
            assertEquals(
                "reč $word",
                SpokenInput.Full(square("${file}3")),
                parseSpokenInput("$word three")
            )
        }
    }

    @Test
    fun `sama kolona je delimican unos`() {
        assertEquals(SpokenInput.File('e'), parseSpokenInput("e"))
        assertEquals(SpokenInput.File('b'), parseSpokenInput("bravo"))
    }

    @Test
    fun `sam broj je delimican unos`() {
        assertEquals(SpokenInput.Rank(4), parseSpokenInput("four"))
        assertEquals(SpokenInput.Rank(8), parseSpokenInput("eight"))
    }

    @Test
    fun `sve ostalo je neprepoznato`() {
        assertEquals(SpokenInput.Unknown, parseSpokenInput("konj na e pet"))
        assertEquals(SpokenInput.Unknown, parseSpokenInput("j nine"))
        assertEquals(SpokenInput.Unknown, parseSpokenInput(""))
    }

    @Test
    fun `citanje samo celog polja i dalje radi`() {
        assertEquals(square("g1"), parseSpokenSquare("g one"))
        assertEquals(null, parseSpokenSquare("g"))
    }
}
