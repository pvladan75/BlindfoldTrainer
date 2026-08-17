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

    @Test
    fun `fonetska abeceda postoji samo uz engleski`() {
        assertTrue(Settings.DEFAULT.isPhoneticAlphabetAvailable)

        Language.entries
            .filterNot { it == Language.ENGLISH }
            .forEach { language ->
                assertFalse(
                    Settings.DEFAULT.copy(language = language).isPhoneticAlphabetAvailable,
                    language.name
                )
            }
    }

    @Test
    fun `promena jezika gasi fonetske reci ali ne brise izbor`() {
        val onEnglish = Settings.DEFAULT.copy(phoneticAlphabet = true)
        assertTrue(onEnglish.usesPhoneticAlphabet)

        val onGerman = onEnglish.copy(language = Language.GERMAN)
        assertFalse(onGerman.usesPhoneticAlphabet, "reči se ne smeju slušati")
        assertTrue(onGerman.phoneticAlphabet, "izbor mora ostati upamćen")

        // Povratak na engleski vraća i izbor, bez ponovnog uključivanja.
        assertTrue(onGerman.copy(language = Language.ENGLISH).usesPhoneticAlphabet)
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
