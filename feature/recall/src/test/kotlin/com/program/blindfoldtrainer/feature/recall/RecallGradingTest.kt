package com.program.blindfoldtrainer.feature.recall

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecallGradingTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    private val whiteRook = Piece(PieceType.ROOK, Color.WHITE)
    private val blackKnight = Piece(PieceType.KNIGHT, Color.BLACK)

    private val target = Board.of(
        mapOf(square("a1") to whiteRook, square("e5") to blackKnight)
    )

    @Test
    fun `tacna rekonstrukcija je besprekorna`() {
        val grade = gradeRecall(
            target,
            mapOf(square("a1") to whiteRook, square("e5") to blackKnight)
        )

        assertTrue(grade.isPerfect)
        assertEquals(setOf(square("a1"), square("e5")), grade.correct)
        assertTrue(grade.wrong.isEmpty())
        assertTrue(grade.missed.isEmpty())
    }

    @Test
    fun `prava figura na pogresnom polju je i greska i propust`() {
        val grade = gradeRecall(
            target,
            mapOf(square("a1") to whiteRook, square("e4") to blackKnight)
        )

        assertFalse(grade.isPerfect)
        assertEquals(setOf(square("a1")), grade.correct)
        assertEquals(setOf(square("e4")), grade.wrong)
        assertEquals(setOf(square("e5")), grade.missed)
    }

    @Test
    fun `pogresna figura na pravom polju se ne priznaje`() {
        val grade = gradeRecall(
            target,
            mapOf(square("a1") to whiteRook, square("e5") to whiteRook)
        )

        assertEquals(setOf(square("a1")), grade.correct)
        assertEquals(setOf(square("e5")), grade.wrong)
        assertEquals(setOf(square("e5")), grade.missed)
    }

    @Test
    fun `prazna rekonstrukcija propusta sve`() {
        val grade = gradeRecall(target, emptyMap())

        assertTrue(grade.correct.isEmpty())
        assertTrue(grade.wrong.isEmpty())
        assertEquals(setOf(square("a1"), square("e5")), grade.missed)
    }

    @Test
    fun `pozicija ima tacno trazeni broj figura`() {
        val random = Random(42)
        repeat(50) {
            val board = randomRecallPosition(pieceCount = 4, random = random)
            assertEquals(4, board.occupied().size)
        }
    }

    @Test
    fun `pesak nikad ne stoji na prvom ni na poslednjem redu`() {
        val random = Random(7)
        repeat(200) {
            randomRecallPosition(pieceCount = 5, random = random)
                .occupied()
                .forEach { (square, piece) ->
                    if (square.rank == 1 || square.rank == 8) {
                        assertFalse(
                            "pešak na ${square}",
                            piece.type == PieceType.PAWN
                        )
                    }
                }
        }
    }
}
