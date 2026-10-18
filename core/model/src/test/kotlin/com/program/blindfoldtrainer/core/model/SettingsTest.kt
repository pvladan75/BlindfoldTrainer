package com.program.blindfoldtrainer.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun `podrazumevano je zateceno ponasanje`() {
        val settings = Settings.DEFAULT

        assertEquals(ThemeChoice.SYSTEM, settings.theme)
        assertEquals(Language.ENGLISH, settings.language)
        assertFalse(settings.phoneticAlphabet)
        assertFalse(settings.listenWholeMove)
        assertFalse(settings.separateLetterAndNumber)
    }

    /**
     * Odluka od 18. oktobra 2026: aplikacija je samo na engleskom. Test drži tu
     * odluku vidljivom — ko doda jezik, mora prvo ovde da kaže da je to namerno,
     * pa onda da mu da rečenice, reči i paket.
     */
    @Test
    fun `jezik je jedan, engleski`() {
        assertEquals(listOf(Language.ENGLISH), Language.entries.toList())
    }

    /** Fonetske reči su engleske; uz jedini jezik su zato uvek dostupne. */
    @Test
    fun `fonetska abeceda je dostupna uz engleski`() {
        assertTrue(Settings.DEFAULT.isPhoneticAlphabetAvailable)

        val on = Settings.DEFAULT.copy(phoneticAlphabet = true)
        assertTrue(on.usesPhoneticAlphabet)
        assertFalse(Settings.DEFAULT.usesPhoneticAlphabet)
    }

    @Test
    fun `brzina govora van opsega se odbija`() {
        assertFailsWith<IllegalArgumentException> { Settings.DEFAULT.copy(speechRate = 3f) }
        assertFailsWith<IllegalArgumentException> { Settings.DEFAULT.copy(speechRate = 0f) }
    }

    @Test
    fun `svaki jezik ima svoj kod`() {
        val codes = Language.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

}
