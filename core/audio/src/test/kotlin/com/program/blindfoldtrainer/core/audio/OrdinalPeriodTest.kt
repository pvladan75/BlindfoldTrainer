package com.program.blindfoldtrainer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tačka iza cifre pretvara broj u redni broj — „4." se čita „četvrti".
 *
 * Prijavljeno sa uređaja: kraj sesije je izgovoren kao „Rešeno jedan od
 * četvrti". Isto je stajalo u pet modula, jer se svaka od tih rečenica završava
 * brojem.
 */
class OrdinalPeriodTest {

    @Test
    fun `tacka na kraju izgovora otpada`() {
        assertEquals(
            "Kraj sesije. Rešeno 1 od 4",
            withoutOrdinalPeriod("Kraj sesije. Rešeno 1 od 4.")
        )
    }

    @Test
    fun `tacka usred recenice postaje zarez, da pauza ostane`() {
        assertEquals(
            "Rešeno 1 od 4, Grešaka 3",
            withoutOrdinalPeriod("Rešeno 1 od 4. Grešaka 3.")
        )
    }

    @Test
    fun `tacka iza reci se ne dira`() {
        assertEquals("Tačno.", withoutOrdinalPeriod("Tačno."))
        assertEquals(
            "Poništeno. Prelazim na sledeću.",
            withoutOrdinalPeriod("Poništeno. Prelazim na sledeću.")
        )
    }

    @Test
    fun `decimala nije kraj recenice`() {
        assertEquals("Brzina 0.85", withoutOrdinalPeriod("Brzina 0.85"))
        assertEquals("Brzina 0.85", withoutOrdinalPeriod("Brzina 0.85."))
    }

    @Test
    fun `izgovor bez brojeva prolazi nepromenjen`() {
        val text = "Dodirni ponovo da odustaneš."
        assertEquals(text, withoutOrdinalPeriod(text))
    }
}
