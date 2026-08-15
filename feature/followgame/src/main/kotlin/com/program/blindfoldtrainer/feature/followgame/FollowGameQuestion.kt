package com.program.blindfoldtrainer.feature.followgame

import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import kotlin.random.Random

/** „Gde stoji crna dama?" — pitanje i tačan odgovor. */
data class FollowGameQuestion(val piece: Piece, val square: Square) {

    /** Tekst pitanja, sa slaganjem roda: dama je ženskog roda, ostale figure muškog. */
    val prompt: String get() = "Gde stoji ${piece.spokenName()}?"
}

/**
 * Bira figuru o kojoj se pita.
 *
 * Pita se **samo o figuri koja je jedina te vrste i boje** — inače odgovor ne bi
 * bio jedan, a korisnik bi gubio poen na dvosmislenom pitanju. Kraljevi su uvek
 * jedinstveni, ali su i najlakši, pa se biraju tek kad drugih nema.
 */
fun questionFor(position: Position, random: Random = Random): FollowGameQuestion? {
    val unique = position.board.occupied()
        .groupBy { (_, piece) -> piece }
        .filterValues { it.size == 1 }
        .map { (piece, occurrences) -> FollowGameQuestion(piece, occurrences.first().first) }

    if (unique.isEmpty()) return null

    val withoutKings = unique.filterNot { it.piece.type == PieceType.KING }
    return (withoutKings.ifEmpty { unique }).random(random)
}

internal fun Piece.spokenName(): String {
    val isFeminine = type == PieceType.QUEEN
    val color = when {
        this.color == Color.WHITE && isFeminine -> "bela"
        this.color == Color.WHITE -> "beli"
        isFeminine -> "crna"
        else -> "crni"
    }
    val name = when (type) {
        PieceType.PAWN -> "pešak"
        PieceType.KNIGHT -> "skakač"
        PieceType.BISHOP -> "lovac"
        PieceType.ROOK -> "top"
        PieceType.QUEEN -> "dama"
        PieceType.KING -> "kralj"
    }
    return "$color $name"
}
