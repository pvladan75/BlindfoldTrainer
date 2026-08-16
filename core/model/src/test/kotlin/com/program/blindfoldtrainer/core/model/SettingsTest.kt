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
        assertEquals(VoiceLanguage.ENGLISH, settings.voiceLanguage)
        assertFalse(settings.phoneticAlphabet)
        assertFalse(settings.listenWholeMove)
        assertFalse(settings.separateLetterAndNumber)
    }

    @Test
    fun `fonetska abeceda postoji samo uz engleski`() {
        assertTrue(Settings.DEFAULT.isPhoneticAlphabetAvailable)

        VoiceLanguage.entries
            .filterNot { it == VoiceLanguage.ENGLISH }
            .forEach { language ->
                assertFalse(
                    Settings.DEFAULT.copy(voiceLanguage = language).isPhoneticAlphabetAvailable,
                    language.name
                )
            }
    }

    @Test
    fun `promena jezika gasi fonetske reci ali ne brise izbor`() {
        val onEnglish = Settings.DEFAULT.copy(phoneticAlphabet = true)
        assertTrue(onEnglish.usesPhoneticAlphabet)

        val onGerman = onEnglish.copy(voiceLanguage = VoiceLanguage.GERMAN)
        assertFalse(onGerman.usesPhoneticAlphabet, "reči se ne smeju slušati")
        assertTrue(onGerman.phoneticAlphabet, "izbor mora ostati upamćen")

        // Povratak na engleski vraća i izbor, bez ponovnog uključivanja.
        assertTrue(onGerman.copy(voiceLanguage = VoiceLanguage.ENGLISH).usesPhoneticAlphabet)
    }

    @Test
    fun `brzina govora van opsega se odbija`() {
        assertFailsWith<IllegalArgumentException> { Settings.DEFAULT.copy(speechRate = 3f) }
        assertFailsWith<IllegalArgumentException> { Settings.DEFAULT.copy(speechRate = 0f) }
    }

    @Test
    fun `svaki jezik ima svoj kod`() {
        val codes = VoiceLanguage.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}
