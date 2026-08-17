package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.SpeechLanguage
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
        for (language in SpeechLanguage.entries) {
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

    @Test
    fun `srpski ima svoje recenice, ostali dobijaju engleske`() {
        assertSame(SerbianPhrases, phrasesFor(SpeechLanguage.SERBIAN))
        assertSame(EnglishPhrases, phrasesFor(SpeechLanguage.ENGLISH))

        for (language in SpeechLanguage.entries - SpeechLanguage.SERBIAN) {
            assertSame(language.name, EnglishPhrases, phrasesFor(language))
        }
    }

    @Test
    fun `srpski i engleski se zaista razlikuju`() {
        assertNotEquals(SerbianPhrases.correct, EnglishPhrases.correct)
        assertNotEquals(
            SerbianPhrases.sessionEndSolved(1, 4),
            EnglishPhrases.sessionEndSolved(1, 4)
        )
    }

    /**
     * Imena figura prate **jezik**, a rečenice zamenu — pa poljski dobija
     * poljska imena u engleskim rečenicama. To je namerno i vredi da padne ako
     * se ikad promeni.
     */
    @Test
    fun `ime figure prati jezik i kad recenice ne prate`() {
        val polish = voiceFor(SpeechLanguage.POLISH)

        assertEquals(
            SpeechLanguages.wordsFor(SpeechLanguage.POLISH).pieces.getValue(PieceType.ROOK),
            polish.nameOf(PieceType.ROOK)
        )
        assertEquals(EnglishPhrases.correct, polish.correct)
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
        for (language in SpeechLanguage.entries) {
            val phrases = phrasesFor(language)
            assertTrue(language.name, !phrases.sessionEndSolved(1, 4).endsWith("."))
            assertTrue(language.name, !phrases.sessionEndCorrect(1, 4).endsWith("."))
            assertTrue(language.name, !phrases.summaryResult(1, 4, 2).endsWith("."))
        }
    }
}
