package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.audio.SpokenToken.Clip
import com.program.blindfoldtrainer.core.audio.SpokenToken.Pause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rečenica kao niz isečaka: dva oblika iz jednog spiska. */
class SpokenLineTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    @Test
    fun `tekst su reci spojene razmakom, tisina ne ostavlja trag`() {
        val line = SpokenLine(Clip("a", "No,"), Pause(100), Clip("b", "the square is"), Clip("c", "light."))
        assertEquals("No, the square is light.", line.text)
        assertEquals(listOf("a", "b", "c"), line.clipIds)
    }

    @Test
    fun `spajanje cuva redosled`() {
        val a = SpokenLine(Clip("a", "A"))
        val b = SpokenLine(Clip("b", "B"))
        assertEquals("A B", (a + b).text)
        assertEquals("A B A", listOf(a, b, a).joined().text)
        assertTrue(SpokenLine.EMPTY.isBlank)
        assertTrue(SpokenLine(Pause(10)).isBlank)
    }

    /** Preko sto se broj slaže: hiljade, stotine, ostatak — kao što se i kaže. */
    @Test
    fun `brojevi preko sto se slazu iz delova`() {
        fun mid(n: Int) = SpokenLine(SpeechVocabulary.numberMid(n)).text
        assertEquals("35", mid(35))
        assertEquals("100", mid(100))
        assertEquals("four hundred 20", mid(420))
        assertEquals("four hundred", mid(400))
        assertEquals("one thousand", mid(1000))
        assertEquals("one thousand two hundred 5", mid(1205))
        assertEquals("one thousand 5", mid(1005))
    }

    @Test
    fun `polje nosi tisinu ispred sebe, osim na pocetku`() {
        val e4 = SpeechVocabulary.square(square("e4"))
        assertEquals(SpeechVocabulary.PAUSE_BEFORE_SQUARE_MILLIS, e4.pauseBeforeMillis)
        assertEquals("sq_e4", e4.id)
        assertEquals("e4", e4.text)
    }

    /**
     * Pozicija se čita beli pa crni, kralj pa dama; redosled je uvek isti da bi
     * se pamtila kao niz, a ne kao skup.
     */
    @Test
    fun `pozicija se cita beli pa crni, kralj pa dama`() {
        val position = Board.of(
            mapOf(
                square("h6") to Piece(PieceType.KING, Color.BLACK),
                square("e5") to Piece(PieceType.QUEEN, Color.WHITE),
                square("e2") to Piece(PieceType.KING, Color.WHITE)
            )
        )
        val line = SpokenLine(SpeechVocabulary.board(position))
        assertEquals("white king on e2 white queen on e5 black king on h6", line.text)

        // Između figura tišina, između strana duža.
        val pauses = line.tokens.filterIsInstance<Pause>().map { it.millis }
        assertEquals(
            listOf(SpeechVocabulary.PAUSE_BETWEEN_PIECES_MILLIS, SpeechVocabulary.PAUSE_BETWEEN_SIDES_MILLIS),
            pauses
        )
    }

    /** Na zahtev se polja čitaju fonetski — ko pita ponovo, nije razlikovao „b" od „d". */
    @Test
    fun `pozicija na zahtev ide fonetski`() {
        val position = Board.of(
            mapOf(
                square("b5") to Piece(PieceType.KING, Color.WHITE),
                square("d8") to Piece(PieceType.QUEEN, Color.BLACK)
            )
        )
        val line = SpokenLine(SpeechVocabulary.board(position, phonetic = true))
        assertEquals("white king on bravo five black queen on delta eight", line.text)
        assertEquals("white king on b5 black queen on d8", SpokenLine(SpeechVocabulary.board(position)).text)
    }

    @Test
    fun `prazna tabla se ne izgovara`() {
        assertTrue(SpokenLine(SpeechVocabulary.board(Board.EMPTY)).isBlank)
    }
}
