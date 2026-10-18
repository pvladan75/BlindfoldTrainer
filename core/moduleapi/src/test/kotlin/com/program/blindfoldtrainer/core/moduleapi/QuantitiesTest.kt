package com.program.blindfoldtrainer.core.moduleapi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Brojevi u rečenici. Postoji zato što bi se inače u svakom modulu pisalo svoje
 * „3 pieces / 1 piece", a negde bi stajalo „1 pieces".
 */
class QuantitiesTest {

    private fun pieces(count: Int) = quantity(count, "piece", "pieces")

    @Test
    fun `jednina samo uz jedan`() {
        assertEquals("1 piece", pieces(1))
        assertEquals("2 pieces", pieces(2))
        assertEquals("5 pieces", pieces(5))
        assertEquals("12 pieces", pieces(12))
        assertEquals("21 pieces", pieces(21))
    }

    /** Engleski: nula ide uz množinu — „0 moves", ne „0 move". */
    @Test
    fun `nula ide uz mnozinu`() {
        assertEquals("0 pieces", pieces(0))
        assertEquals("0 moves", quantity(0, "move", "moves"))
    }

    /** Pravilna množina se ne mora pisati — podrazumeva se nastavak „s". */
    @Test
    fun `mnozina se podrazumeva kad je pravilna`() {
        assertEquals("1 square", quantity(1, "square"))
        assertEquals("3 squares", quantity(3, "square"))
        assertEquals("1 jump", quantity(1, "jump"))
        assertEquals("4 jumps", quantity(4, "jump"))
    }

    @Test
    fun `sekunde bez suvisne nule`() {
        assertEquals("6", secondsLabel(6_000))
        assertEquals("3.5", secondsLabel(3_500))
        assertEquals("4", secondsLabel(4_000))
    }
}
