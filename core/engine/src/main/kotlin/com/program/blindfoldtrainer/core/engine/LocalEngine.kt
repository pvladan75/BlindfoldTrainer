package com.program.blindfoldtrainer.core.engine

import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor zasnovan na ugrađenoj pretrazi iz `:core:chess`.
 *
 * Ranije je ovde bio Stockfish preko JNI-ja. Ispao je jer je za jedini modul
 * koji motor koristi — odbranu u dobijenoj završnici — bio ogroman: Stockfish 17
 * ne radi bez NNUE mreže (klasična evaluacija je izbačena u verziji 16), pa je
 * nosio 78 MB mreža, native prevođenje i ograničenje na jedan ABI.
 *
 * [ChessEngine] interfejs je ostao, tako da povratak na spoljni motor ne dira
 * nijedan modul.
 */
@Singleton
class LocalEngine @Inject constructor() : ChessEngine {

    /** Ugrađena pretraga radi svuda — nema native biblioteke koja može da zataji. */
    override val isAvailable: Boolean = true

    override suspend fun start() = Unit

    override suspend fun bestMove(position: Position, depth: Int): Move? =
        withContext(Dispatchers.Default) {
            Search.bestMove(
                position = position,
                maxDepth = depth,
                timeBudgetMillis = TIME_BUDGET_MILLIS
            )
        }

    override suspend fun evaluate(position: Position, depth: Int): Evaluation =
        withContext(Dispatchers.Default) {
            val score = Search.evaluate(position)
            val distanceToMate = Search.MATE_SCORE - kotlin.math.abs(score)

            // Ocene blizu matne vrednosti znače da je mat pronađen, a razlika
            // do nje govori za koliko poluhodova.
            if (distanceToMate < MATE_HORIZON) {
                Evaluation.Mate(if (score > 0) distanceToMate else -distanceToMate)
            } else {
                Evaluation.Centipawns(score)
            }
        }

    override fun stopSearch() {
        // Pretraga se sama zaustavlja po isteku vremena; nema procesa da se prekine.
    }

    private companion object {
        const val TIME_BUDGET_MILLIS = 1_500L
        const val MATE_HORIZON = 1_000
    }
}
