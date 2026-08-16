package com.program.blindfoldtrainer.core.chess

import kotlin.random.Random

/**
 * Sastavljanje zadate pozicije na tabli — zajedničko za dva modula.
 *
 * „Zapamti poziciju" je vidi pa je sastavlja po sećanju, „Postavi po diktatu" je
 * čuje pa je sastavlja po zapisu. Vežbe su suprotne po tome **odakle** pozicija
 * stiže, ali je posao isti: složiti je, pa uporediti sa zadatom.
 *
 * Zato stoji ovde, u čistom Kotlinu, a ne u jednom od modula — dva modula sa
 * dve kopije istog pravila bi se pre ili kasnije razišla.
 */
data class ReconstructionGrade(
    /** Polja na koja je stavljena prava figura. */
    val correct: Set<Square>,
    /** Polja na kojima stoji nešto što tamo ne pripada. */
    val wrong: Set<Square>,
    /** Polja iz zadate pozicije koja su ostala nepokrivena. */
    val missed: Set<Square>
) {
    val isPerfect: Boolean get() = wrong.isEmpty() && missed.isEmpty()
}

/**
 * Poređenje je po polju **i** figuri: prava figura na pogrešnom polju ne vredi.
 *
 * Zbir nije uvek broj figura — jedna promašena figura daje i pogrešno i
 * propušteno polje, i tako i treba da izgleda na tabli.
 */
fun gradeReconstruction(target: Board, placed: Map<Square, Piece>): ReconstructionGrade {
    val correct = placed.filter { (square, piece) -> target[square] == piece }.keys
    val wrong = placed.keys - correct
    val missed = target.occupied().map { it.first }.toSet() - correct

    return ReconstructionGrade(correct = correct, wrong = wrong, missed = missed)
}

/**
 * Nasumičan raspored figura, za zadatak.
 *
 * Nije partija nego raspored, pa ne mora biti legalan — ali pešak na prvom ili
 * poslednjem redu ne postoji ni u jednoj partiji i samo bi zbunjivao, pa se na
 * krajnjim redovima bira neka druga figura.
 */
fun randomSparsePosition(pieceCount: Int, random: Random = Random): Board {
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
