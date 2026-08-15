package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
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
    fun `polje se izgovara slovo pa broj`() {
        assertEquals("e 4", Square.fromAlgebraic("e4")!!.spoken())
    }
}
