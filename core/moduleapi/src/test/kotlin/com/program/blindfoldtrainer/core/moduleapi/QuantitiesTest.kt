package com.program.blindfoldtrainer.core.moduleapi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Brojevi u rečenici. Postoji zato što bi se inače u svakom modulu pisalo svoje
 * „3 figure / 5 figura", a negde bi stajalo „5 figure".
 */
class QuantitiesTest {

    private fun figures(count: Int) = quantity(count, "figura", "figure", "figura")

    @Test
    fun `jednina, mnozina i ono izmedju`() {
        assertEquals("1 figura", figures(1))
        assertEquals("2 figure", figures(2))
        assertEquals("4 figure", figures(4))
        assertEquals("5 figura", figures(5))
        assertEquals("8 figura", figures(8))
    }

    /**
     * Izuzetak koji se lako previdi: **11 do 14 idu uz množinu** iako se
     * završavaju na 1 do 4. Bez ovoga bi „12 poteza" ispalo „12 potez".
     */
    @Test
    fun `jedanaest do cetrnaest idu uz mnozinu`() {
        assertEquals("11 figura", figures(11))
        assertEquals("12 figura", figures(12))
        assertEquals("14 figura", figures(14))
        assertEquals("12 poteza", quantity(12, "potez", "poteza"))
    }

    /** Iza dvadeset se pravilo vraća, po poslednjoj cifri. */
    @Test
    fun `posle dvadeset odlucuje poslednja cifra`() {
        assertEquals("21 figura", figures(21))
        assertEquals("22 figure", figures(22))
        assertEquals("25 figura", figures(25))
    }

    /** Nula ide uz množinu, kao i sve što se ne završava na 1 do 4. */
    @Test
    fun `nula ide uz mnozinu`() {
        assertEquals("0 figura", figures(0))
    }

    /** Gde su drugi i treći oblik isti — potez, polje, pitanje — dovoljna su dva. */
    @Test
    fun `dva oblika su dovoljna kad se mnozina ne razlikuje`() {
        assertEquals("1 polje", quantity(1, "polje", "polja"))
        assertEquals("3 polja", quantity(3, "polje", "polja"))
        assertEquals("20 polja", quantity(20, "polje", "polja"))
    }

    @Test
    fun `sekunde bez suvisne nule`() {
        assertEquals("6", secondsLabel(6_000))
        assertEquals("3,5", secondsLabel(3_500))
        assertEquals("4", secondsLabel(4_000))
    }
}
