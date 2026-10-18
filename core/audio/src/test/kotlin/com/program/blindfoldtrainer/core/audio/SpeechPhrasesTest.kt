package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Language
import org.junit.Assert.assertEquals
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
    fun `nijedna recenica nije prazna`() {
        for (language in Language.entries) {
            val voice = voiceFor(language)

            for (member in SpeechPhrases::class.java.methods) {
                val arguments = sampleArguments(member.parameterTypes)
                val spoken = member.invoke(voice, *arguments) as SpokenLine
                assertTrue("${language.name}.${member.name}", !spoken.isBlank)
                assertTrue("${language.name}.${member.name}", spoken.text.isNotBlank())
            }
        }
    }

    @Test
    fun `engleski dobija engleske recenice`() {
        assertSame(EnglishPhrases, phrasesFor(Language.ENGLISH))
    }

    /** Množina se ne lomi na jedinici — „in 1 move", ne „in 1 moves". */
    @Test
    fun `engleski razlikuje jedninu od mnozine`() {
        assertEquals("in 1 move.", EnglishPhrases.inMoves(1).text)
        assertEquals("in 3 moves.", EnglishPhrases.inMoves(3).text)
        assertEquals("Correct, in 1 move.", EnglishPhrases.correctInMoves(1).text)
    }

    /** Brojevi stoje gde ih modul da, a tekst je ono što bi stajalo na ekranu. */
    @Test
    fun `recenice sa brojevima`() {
        assertEquals("End of session. Solved 1 out of 4", EnglishPhrases.sessionEndSolved(1, 4).text)
        assertEquals("Solved 4 out of 6 Mistakes 2", EnglishPhrases.summaryResult(4, 6, 2).text)
        assertEquals("You earned 35 points.", EnglishPhrases.summaryXp(35).text)
        assertEquals("You earned four hundred 20 points.", EnglishPhrases.summaryXp(420).text)
    }

    /** Pitanje i ispravka iz „Prati partiju" nose boju figure. */
    @Test
    fun `pitanja prati partiju`() {
        val queen = Piece(PieceType.QUEEN, Color.BLACK)
        val d8 = requireNotNull(Square.fromAlgebraic("d8"))
        assertEquals("Where is the black queen?", EnglishPhrases.whereIsPiece(queen).text)
        assertEquals("Not there. The black queen is on d8", EnglishPhrases.notThereIsOn(queen, d8).text)
        assertEquals(
            "Who attacks the black queen on d8 Find 2",
            EnglishPhrases.whoAttacks(queen, d8, 2).text
        )
    }

    companion object {
        /**
         * Brojevi, enumi, figure, polja — sve što se u rečenicu može umetnuti.
         * Kad se pojavi nova vrsta, test padne ovde a ne na uređaju.
         */
        fun sampleArguments(types: Array<Class<*>>): Array<Any> = Array(types.size) { index ->
            when (val type = types[index]) {
                Int::class.javaPrimitiveType -> 2
                Boolean::class.javaPrimitiveType -> true
                Char::class.javaPrimitiveType -> 'e'
                Piece::class.java -> Piece(PieceType.KNIGHT, Color.WHITE)
                Square::class.java -> requireNotNull(Square.fromAlgebraic("e4"))
                List::class.java -> listOf(requireNotNull(Square.fromAlgebraic("a1")))
                else -> if (type.isEnum) requireNotNull(type.enumConstants).first() else error("no sample for $type")
            }
        }
    }
}
