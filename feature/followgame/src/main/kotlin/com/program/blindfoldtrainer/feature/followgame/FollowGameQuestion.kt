package com.program.blindfoldtrainer.feature.followgame

import com.program.blindfoldtrainer.core.audio.EnglishPhrases
import com.program.blindfoldtrainer.core.audio.SpokenLine
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.chess.attackersOf
import kotlin.random.Random

/**
 * Šta se pita o poziciji koja se prati.
 *
 * Dve vrste, i to je prvi put da jedan modul nosi dva zadatka — što je i bila
 * poenta razdvajanja: **isti ulaz i ista podrška, a mere različite stvari.**
 * „Gde stoji figura" meri ažuriranje slike; „ko je napada" meri kontrolu polja.
 *
 * Odgovor je u oba slučaja **polje**, ne ime figure. Polje je jednoznačno — dva
 * topa se po imenu ne razlikuju — unos za njega već postoji i dodirom i glasom,
 * a i pitanje se u partiji tako i postavlja: šta gađa e5 rešava se traženjem
 * linija do tog polja, a odgovor je odakle.
 */
sealed interface FollowGameQuestion {

    /** Sva polja koja se traže. Pitanje je rešeno kad su sva nađena. */
    val expected: Set<Square>

    /** Pitanje — isti spisak isečaka se čuje i crta na ekranu ([SpokenLine.text]). */
    val prompt: SpokenLine

    /** Istina koja se kaže posle promašaja — vežba pokazuje, test samo ocenjuje. */
    val correction: SpokenLine

    /** „Gde stoji crna dama?" */
    data class WhereIs(val piece: Piece, val square: Square) : FollowGameQuestion {
        override val expected: Set<Square> get() = setOf(square)

        override val prompt: SpokenLine get() = EnglishPhrases.whereIsPiece(piece)

        override val correction: SpokenLine
            get() = EnglishPhrases.notThereIsOn(piece, square)
    }

    /**
     * „Koje dve crne figure napadaju belog skakača na e5?"
     *
     * **Broj napadača se kaže unapred.** Bez toga se ne zna kad je odgovor
     * gotov, pa bi se merilo i pogađanje trenutka umesto same veštine; a težina
     * zadatka je u tome da se napadači **nađu**, ne da se pogodi koliko ih ima.
     */
    data class Attackers(
        val target: Piece,
        val targetSquare: Square,
        override val expected: Set<Square>
    ) : FollowGameQuestion {

        override val prompt: SpokenLine
            get() = EnglishPhrases.whoAttacks(target, targetSquare, expected.size)

        override val correction: SpokenLine
            get() = EnglishPhrases.attackersAre(expected.sortedBy { it.index })
    }
}

/**
 * Bira pitanje o tome **gde figura stoji**.
 *
 * Pita se samo o figuri koja je jedina te vrste i boje — inače odgovor ne bi bio
 * jedan, a korisnik bi gubio poen na dvosmislenom pitanju. Kraljevi su uvek
 * jedinstveni, ali su i najlakši, pa se biraju tek kad drugih nema.
 */
fun questionFor(position: Position, random: Random = Random): FollowGameQuestion.WhereIs? {
    val unique = position.board.occupied()
        .groupBy { (_, piece) -> piece }
        .filterValues { it.size == 1 }
        .map { (piece, occurrences) ->
            FollowGameQuestion.WhereIs(piece, occurrences.first().first)
        }

    if (unique.isEmpty()) return null

    val withoutKings = unique.filterNot { it.piece.type == PieceType.KING }
    return (withoutKings.ifEmpty { unique }).random(random)
}

/**
 * Bira pitanje o tome **ko napada figuru**.
 *
 * Meta se bira među figurama koje su **stvarno napadnute**: pitanje na koje je
 * odgovor „nijedna" nema čime da se odgovori kad se odgovara poljima, a i ne
 * meri isto — traženje praznine nije traženje napadača.
 *
 * Boja i vrsta mete se menjaju same od sebe, jer se bira iz zatečene pozicije;
 * kralj se izbegava dok ima drugih, pošto je napadnut kralj šah i time posebna
 * priča.
 */
fun attackersQuestionFor(
    position: Position,
    random: Random = Random
): FollowGameQuestion.Attackers? {
    val candidates = position.board.occupied().mapNotNull { (square, piece) ->
        val attackers = position.board.attackersOf(square, piece.color.opposite())
        if (attackers.isEmpty()) {
            null
        } else {
            FollowGameQuestion.Attackers(
                target = piece,
                targetSquare = square,
                expected = attackers
            )
        }
    }

    if (candidates.isEmpty()) return null

    val withoutKings = candidates.filterNot { it.target.type == PieceType.KING }
    return (withoutKings.ifEmpty { candidates }).random(random)
}

private fun Color.opposite(): Color = if (this == Color.WHITE) Color.BLACK else Color.WHITE

