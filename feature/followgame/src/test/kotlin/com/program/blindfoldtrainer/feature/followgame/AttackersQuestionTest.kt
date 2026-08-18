package com.program.blindfoldtrainer.feature.followgame

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.chess.attackersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pitanje o napadačima mora da bude rešivo i tačno postavljeno. */
class AttackersQuestionTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private fun position(vararg pieces: Pair<String, Piece>) = Position(
        board = Board.of(pieces.associate { (notation, piece) -> square(notation) to piece }),
        sideToMove = Color.WHITE
    )

    /** Pitanje na koje je odgovor „nijedna" se ne postavlja: nema čime da se odgovori. */
    @Test
    fun `bez napadnute figure nema pitanja`() {
        val quiet = position(
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK)
        )

        assertNull(attackersQuestionFor(quiet, Random(1)))
    }

    @Test
    fun `trazi se ono sto zaista gadja metu`() {
        val position = position(
            "e5" to Piece(PieceType.KNIGHT, Color.WHITE),
            "d6" to Piece(PieceType.PAWN, Color.BLACK),
            "e8" to Piece(PieceType.ROOK, Color.BLACK),
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK)
        )

        val question = requireNotNull(attackersQuestionFor(position, Random(1)))

        assertEquals(
            position.board.attackersOf(question.targetSquare, question.target.color.other()),
            question.expected
        )
        assertTrue("meta mora biti napadnuta", question.expected.isNotEmpty())
    }

    /** Broj napadača stoji u pitanju — bez njega se ne zna kad je odgovor gotov. */
    @Test
    fun `pitanje kaze koliko se napadaca trazi`() {
        val position = position(
            "e5" to Piece(PieceType.KNIGHT, Color.WHITE),
            "d6" to Piece(PieceType.PAWN, Color.BLACK),
            "e8" to Piece(PieceType.ROOK, Color.BLACK),
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK)
        )

        val question = requireNotNull(attackersQuestionFor(position, Random(1)))

        assertTrue(question.prompt, question.prompt.contains("(${question.expected.size})"))
        assertNotNull(question.correction)
    }
}

private fun Color.other(): Color = if (this == Color.WHITE) Color.BLACK else Color.WHITE
