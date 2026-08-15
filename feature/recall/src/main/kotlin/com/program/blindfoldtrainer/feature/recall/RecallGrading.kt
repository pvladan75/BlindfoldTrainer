package com.program.blindfoldtrainer.feature.recall

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import kotlin.random.Random

/**
 * Ocena rekonstrukcije.
 *
 * [correct] su polja na koja je stavljena prava figura, [wrong] polja na kojima
 * stoji nešto što tamo ne pripada, a [missed] polja iz zadate pozicije koja su
 * ostala nepokrivena. Zbir nije uvek broj figura — jedna promašena figura daje
 * i pogrešno i propušteno polje, i tako i treba da izgleda na tabli.
 */
data class RecallGrade(
    val correct: Set<Square>,
    val wrong: Set<Square>,
    val missed: Set<Square>
) {
    val isPerfect: Boolean get() = wrong.isEmpty() && missed.isEmpty()
}

/** Poređenje je po polju i figuri: prava figura na pogrešnom polju ne vredi. */
fun gradeRecall(target: Board, placed: Map<Square, Piece>): RecallGrade {
    val correct = placed.filter { (square, piece) -> target[square] == piece }.keys
    val wrong = placed.keys - correct
    val missed = target.occupied().map { it.first }.toSet() - correct

    return RecallGrade(correct = correct, wrong = wrong, missed = missed)
}

/**
 * Nasumična pozicija za pamćenje.
 *
 * Nije partija nego raspored, pa ne mora biti legalna — ali pešak na prvom ili
 * poslednjem redu ne postoji ni u jednoj partiji i samo bi zbunjivao, pa se na
 * krajnjim redovima bira neka druga figura.
 */
fun randomRecallPosition(pieceCount: Int, random: Random = Random): Board {
    val squares = Square.ALL.shuffled(random).take(pieceCount)

    return Board.of(
        squares.associateWith { square ->
            val types = if (square.rank == 1 || square.rank == 8) TYPES_OFF_BACK_RANK else ALL_TYPES
            Piece(types.random(random), COLORS.random(random))
        }
    )
}

private val ALL_TYPES = PieceType.entries
private val TYPES_OFF_BACK_RANK = ALL_TYPES.filterNot { it == PieceType.PAWN }
private val COLORS = Color.entries
