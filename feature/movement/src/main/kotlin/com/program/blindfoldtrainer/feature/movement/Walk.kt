package com.program.blindfoldtrainer.feature.movement

import com.program.blindfoldtrainer.core.chess.EmptyBoard
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square

/** Kako je primljeno jedno izgovoreno polje. */
enum class Step {
    /** Potez je legalan i polje je novo — figura je otišla tamo. */
    ACCEPTED,

    /** Figura tako ne ide. */
    ILLEGAL,

    /** Potez je legalan, ali je polje već potrošeno. */
    VISITED
}

/**
 * Šetnja jednom figurom po praznoj tabli, **bez vraćanja na polje**.
 *
 * Zabrana ponavljanja je jedino što ovu vežbu čini vežbom. Bez nje bi se topom
 * sa e4 moglo reći e5, e4, e5 unedogled — sve legalno, nula napora. Sa njom se
 * uz trenutno polje mora držati i **rastući spisak potrošenih**, pa se greška
 * gomila kroz niz umesto da se svaki potez rešava iznova.
 *
 * **Greška ne prekida šetnju.** Figura ostaje gde je bila i pokušava se ponovo
 * sa istog polja. Jedan promašaj u dvanaestom potezu ne sme da poništi jedanaest
 * dobrih; tačnost i dubina se mere odvojeno i oba podatka ostaju čitljiva.
 *
 * Ovo je čist Kotlin bez Androida i bez `ViewModel`-a, da se pravila šetnje mogu
 * testirati bez emulatora — isto pravilo po kom `:core:chess` ne zna za ekran.
 */
data class Walk(
    /** Figura kojom se šeta. Dama znači **naizmenično**, vidi [mover]. */
    val piece: PieceType,
    val start: Square,
    /** Koliko poteza se traži. Šetnja se može završiti i ranije, zaglavljivanjem. */
    val targetMoves: Int,
    /** Potrošena polja, počev od polaznog. */
    val visited: List<Square> = listOf(start),
    /** Koliko je polja izgovoreno — i tačnih i pogrešnih. */
    val announced: Int = 0,
    /** Koliko je poteza izdržano pre **prve** greške; `null` dok greške nema. */
    val heldUntil: Int? = null
) {
    init {
        require(targetMoves > 0) { "šetnja bez poteza ne bi bila šetnja" }
    }

    val current: Square get() = visited.last()

    val movesMade: Int get() = visited.size - 1

    val movesLeft: Int get() = (targetMoves - movesMade).coerceAtLeast(0)

    /**
     * Čime se ide **sledeći** potez.
     *
     * Dama se smenjuje: prvi potez kao top, drugi po dijagonali kao lovac, pa
     * opet. Tako se uz polje mora držati i **čime si stigao**, što je dve veze
     * umesto jedne — a to je i cela razlika između dame i topa u ovoj vežbi.
     *
     * Nije šahovsko pravilo nego pravilo vežbe, pa i stoji ovde a ne u
     * `:core:chess`, gde dama i dalje znači top plus lovac.
     */
    val mover: PieceType
        get() = when (piece) {
            PieceType.QUEEN -> if (movesMade % 2 == 0) PieceType.ROOK else PieceType.BISHOP
            else -> piece
        }

    /** Polja na koja se sme sada: dohvatljiva, a još nepotrošena. */
    val options: List<Square>
        get() = EmptyBoard.reach(current, mover).filterNot { it in visited }

    /**
     * Nema više kuda.
     *
     * **Nije greška nego kraj.** Skakač uz zabranu ponavljanja ume da se zatvori
     * u ugao, i to je ishod vežbe a ne pad — dužina je rezultat.
     */
    val isStuck: Boolean get() = options.isEmpty()

    val isDone: Boolean get() = movesMade >= targetMoves || isStuck

    /**
     * Prima jedno izgovoreno polje i vraća šetnju posle njega.
     *
     * Geometrija se proverava **pre** potrošenosti: „tako se ne ide" i „tu si
     * već bio" nisu isti promašaj, a kad su oba tačna, pogrešan je potez.
     */
    fun announce(square: Square): Pair<Walk, Step> {
        val step = when {
            square !in EmptyBoard.reach(current, mover) -> Step.ILLEGAL
            square in visited -> Step.VISITED
            else -> Step.ACCEPTED
        }

        val next = copy(
            visited = if (step == Step.ACCEPTED) visited + square else visited,
            announced = announced + 1,
            // Zapisuje se dokle je slika izdržala **pre prve** greške; kasnije
            // greške taj broj ne diraju.
            heldUntil = heldUntil ?: movesMade.takeIf { step != Step.ACCEPTED }
        )

        return next to step
    }
}
