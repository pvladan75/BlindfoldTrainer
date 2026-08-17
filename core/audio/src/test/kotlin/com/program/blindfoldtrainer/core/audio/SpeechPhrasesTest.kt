package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPhrasesTest {

    /**
     * Prazna rečenica je **tišina na uređaju** — najgori mogući otkaz u režimu
     * bez ekrana, jer izgleda kao da aplikacija ništa nije ni pokušala.
     *
     * Prolazi se kroz sve članove sučelja, a ne kroz spisak koji se piše ručno:
     * spisak bi zaostao za prvom sledećom rečenicom, a baš nju niko ne bi
     * proverio.
     */
    @Test
    fun `nijedna recenica nije prazna ni na jednom jeziku`() {
        for (language in Language.entries) {
            val voice = voiceFor(language)

            for (member in SpeechPhrases::class.java.methods) {
                val arguments = Array<Any>(member.parameterTypes.size) { index ->
                    if (member.parameterTypes[index] == Int::class.javaPrimitiveType) 2 else "reč"
                }

                val spoken = member.invoke(voice, *arguments) as String
                assertTrue("${language.name}.${member.name}", spoken.isNotBlank())
            }
        }
    }

    /**
     * Jezik bez rečenica dobija engleske. Danas su svi takvi osim engleskog —
     * i to je stanje koje test drži vidljivim, da se ne zaboravi da osam
     * jezika čeka prevod.
     */
    @Test
    fun `jezik bez recenica dobija engleske`() {
        assertSame(EnglishPhrases, phrasesFor(Language.ENGLISH))

        for (language in Language.entries) {
            assertSame(language.name, EnglishPhrases, phrasesFor(language))
        }
    }

    /**
     * **Jedan izabran jezik, jedan jezik u ušima.**
     *
     * Prvo je bilo obrnuto — imena po jeziku, rečenice na zameni — i sa uređaja
     * je stiglo „pola na engleskom, pola na nemačkom". Jezik bez rečenica se
     * zato ceo prebacuje na engleski, i imena sa njim.
     */
    @Test
    fun `jezik bez recenica se ceo prebacuje na engleski`() {
        val german = voiceFor(Language.GERMAN)

        assertEquals(EnglishPhrases.correct, german.correct)
        assertEquals(
            SpeechLanguages.wordsFor(Language.ENGLISH).pieces.getValue(PieceType.ROOK),
            german.nameOf(PieceType.ROOK)
        )
    }

    /** Prevedeni jezik dobija i svoje rečenice i svoja imena. */
    @Test
    fun `preveden jezik govori sam sebe`() {
        for (language in TRANSLATED_LANGUAGES) {
            val voice = voiceFor(language)
            assertEquals(language.name, phrasesFor(language).correct, voice.correct)
            assertEquals(
                language.name,
                SpeechLanguages.wordsFor(language).pieces.getValue(PieceType.ROOK),
                voice.nameOf(PieceType.ROOK)
            )
        }
    }

    /** Množina se ne lomi na jedinici — „in 1 move", ne „in 1 moves". */
    @Test
    fun `engleski razlikuje jedninu od mnozine`() {
        assertEquals("in 1 move", EnglishPhrases.inMoves(1))
        assertEquals("in 3 moves", EnglishPhrases.inMoves(3))
    }

    /**
     * Kraj sesije se ne sme završiti tačkom iza broja — „4." se čita kao
     * „četvrti". Ovde se čuva namera; [OrdinalPeriodTest] čuva i mrežu ispod
     * nje, u [AndroidSpeaker].
     */
    @Test
    fun `recenica sa brojem na kraju nema tacku`() {
        for (language in Language.entries) {
            val phrases = phrasesFor(language)
            assertTrue(language.name, !phrases.sessionEndSolved(1, 4).endsWith("."))
            assertTrue(language.name, !phrases.sessionEndCorrect(1, 4).endsWith("."))
            assertTrue(language.name, !phrases.summaryResult(1, 4, 2).endsWith("."))
        }
    }
}
