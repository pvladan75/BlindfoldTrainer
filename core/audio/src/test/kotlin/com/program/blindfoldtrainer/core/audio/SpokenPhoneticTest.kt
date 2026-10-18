package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Fonetski oblik polja — onaj kojim se **ponavlja**.
 *
 * „B" i „D" se preko zvučnika razlikuju tek toliko koliko dozvoli soba, a ko je
 * pritisnuo „ponovi" je već jednom pogrešno čuo.
 */
class SpokenPhoneticTest {

    private fun phonetic(name: String) =
        SpokenLine(SpeechVocabulary.squarePhonetic(requireNotNull(Square.fromAlgebraic(name)))).text

    @Test
    fun `kolona se izgovara imenom, ne slovom`() {
        assertEquals("bravo five", phonetic("b5"))
        assertEquals("delta five", phonetic("d5"))
    }

    /** Par zbog kog ovo i postoji: „b" i „d" se preko zvučnika mešaju. */
    @Test
    fun `b i d se vise ne mogu pomesati`() {
        assertNotEquals(phonetic("b5"), phonetic("d5"))
        assertEquals("bravo", phonetic("b5").substringBefore(' '))
        assertEquals("delta", phonetic("d5").substringBefore(' '))
    }

    /**
     * **Ista azbuka na oba kraja.** Ono što aplikacija izgovori pri ponavljanju
     * mora biti i ono što ume da primi natrag — inače bi se korisniku nudio
     * način izgovora koji sam program ne razume.
     */
    @Test
    fun `sve sto se fonetski izgovori i prepoznaje se natrag`() {
        Square.ALL.forEach { square ->
            assertEquals(
                "fonetski oblik polja $square se ne prepoznaje natrag",
                square,
                parseSpokenSquare(SpokenLine(SpeechVocabulary.squarePhonetic(square)).text)
            )
        }
    }

    /** Nijedno polje ne deli fonetski oblik sa drugim. */
    @Test
    fun `fonetski oblik je jedinstven za svako polje`() {
        val spoken = Square.ALL.map { SpokenLine(SpeechVocabulary.squarePhonetic(it)).text }

        assertEquals(Square.ALL.size, spoken.toSet().size)
    }
}
