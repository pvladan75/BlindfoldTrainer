package com.program.blindfoldtrainer.core.engine

import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Position

/** Ocena pozicije iz ugla strane koja je na potezu. */
sealed interface Evaluation {
    /** Prednost u stotinkama pešaka. Pozitivno znači bolje za onoga ko je na potezu. */
    data class Centipawns(val value: Int) : Evaluation

    /** Mat za [movesToMate] poteza. Negativno znači da matiraju protivnika. */
    data class Mate(val movesToMate: Int) : Evaluation
}

/**
 * Šahovski motor. Iza interfejsa je da bi moduli mogli da se testiraju bez
 * pravog Stockfish-a, i da zamena motora ne dira nijedan modul.
 */
interface ChessEngine {

    /** Da li je motor uopšte upotrebljiv na ovom uređaju. */
    val isAvailable: Boolean

    /** Podiže motor ako već nije podignut. Bezbedno je zvati više puta. */
    suspend fun start()

    /** Najbolji potez u datoj poziciji, ili `null` ako ga nema (mat ili pat). */
    suspend fun bestMove(position: Position, depth: Int = DEFAULT_DEPTH): Move?

    suspend fun evaluate(position: Position, depth: Int = DEFAULT_DEPTH): Evaluation?

    /** Prekida tekuću pretragu. Motor ostaje podignut. */
    fun stopSearch()

    companion object {
        const val DEFAULT_DEPTH = 12
    }
}
