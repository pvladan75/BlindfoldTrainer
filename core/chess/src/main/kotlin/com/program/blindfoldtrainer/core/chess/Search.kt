package com.program.blindfoldtrainer.core.chess

import kotlin.math.abs
import kotlin.math.max

/**
 * Mali šahovski motor: negamax sa alfa-beta odsecanjem.
 *
 * Namerno je skroman. Potreban nam je protivnik koji se **razumno brani** u
 * dobijenim završnicama (K+D protiv K, K+T protiv K, K+2L protiv K), a ne motor
 * koji igra celu partiju. Za to je ovoliko dovoljno, a zauzvrat nema ni native
 * prevođenja, ni mreže od sedamdeset megabajta, ni ograničenja na jedan ABI —
 * i sve se da testirati bez uređaja.
 */
object Search {

    const val MATE_SCORE = 30_000
    private const val INFINITY = 1_000_000

    /** Iznad ove razlike u materijalu jedna strana očigledno dobija. */
    private const val DECISIVE_MATERIAL = 300

    private val PIECE_VALUES = intArrayOf(
        100,  // PAWN
        320,  // KNIGHT
        330,  // BISHOP
        500,  // ROOK
        900,  // QUEEN
        0     // KING — kralj se ne broji, uvek je na tabli
    )

    /**
     * Najbolji potez u datoj poziciji, ili `null` ako legalnih poteza nema.
     *
     * Pretražuje se produbljivanjem, pa se pri isteku [timeBudgetMillis] koristi
     * rezultat poslednje završene dubine umesto nedovršene.
     */
    fun bestMove(
        position: Position,
        maxDepth: Int = 4,
        timeBudgetMillis: Long = 1_500
    ): Move? {
        val moves = orderMoves(position, position.legalMoves())
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        val deadline = System.currentTimeMillis() + timeBudgetMillis
        var best = moves.first()

        for (depth in 1..maxDepth) {
            var bestScoreAtDepth = -INFINITY
            var bestMoveAtDepth: Move? = null
            var alpha = -INFINITY

            for (move in moves) {
                if (System.currentTimeMillis() > deadline) break

                val score = -negamax(
                    position = position.applyMove(move),
                    depth = depth - 1,
                    alpha = -INFINITY,
                    beta = -alpha,
                    ply = 1,
                    deadline = deadline
                )

                if (score > bestScoreAtDepth) {
                    bestScoreAtDepth = score
                    bestMoveAtDepth = move
                }
                alpha = max(alpha, score)
            }

            // Nedovršena dubina daje nepouzdan poredak, pa se odbacuje.
            if (System.currentTimeMillis() > deadline) break
            bestMoveAtDepth?.let { best = it }
        }

        return best
    }

    private fun negamax(
        position: Position,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
        deadline: Long
    ): Int {
        val moves = position.legalMoves()

        if (moves.isEmpty()) {
            // Mat što je dalje, to je manje loš — otud [ply] u računici.
            // Bez toga motor ne bira odlaganje mata, a upravo to čini razliku
            // između protivnika koji se brani i onog koji se predaje.
            return if (position.isInCheck) -MATE_SCORE + ply else 0
        }
        if (position.isDrawByFiftyMoveRule) return 0
        if (depth <= 0) return evaluate(position)
        if (System.currentTimeMillis() > deadline) return evaluate(position)

        var best = -INFINITY
        var currentAlpha = alpha

        for (move in orderMoves(position, moves)) {
            val score = -negamax(
                position = position.applyMove(move),
                depth = depth - 1,
                alpha = -beta,
                beta = -currentAlpha,
                ply = ply + 1,
                deadline = deadline
            )
            if (score > best) best = score
            if (best > currentAlpha) currentAlpha = best
            if (currentAlpha >= beta) break
        }
        return best
    }

    /** Uzimanja prva — bez ikakvog poretka alfa-beta jedva da odseca. */
    private fun orderMoves(position: Position, moves: List<Move>): List<Move> =
        moves.sortedByDescending { move ->
            val captured = position.board[move.to]
            val captureValue = captured?.let { PIECE_VALUES[it.type.ordinal] } ?: 0
            val promotionValue = move.promotion?.let { PIECE_VALUES[it.ordinal] } ?: 0
            captureValue + promotionValue
        }

    /** Ocena iz ugla strane koja je na potezu. */
    fun evaluate(position: Position): Int {
        val fromWhite = evaluateFromWhite(position)
        return if (position.sideToMove == Color.WHITE) fromWhite else -fromWhite
    }

    private fun evaluateFromWhite(position: Position): Int {
        var material = 0
        var whiteKing: Square? = null
        var blackKing: Square? = null

        for ((square, piece) in position.board.occupied()) {
            when {
                piece.type == PieceType.KING && piece.color == Color.WHITE -> whiteKing = square
                piece.type == PieceType.KING && piece.color == Color.BLACK -> blackKing = square
            }
            val value = PIECE_VALUES[piece.type.ordinal]
            material += if (piece.color == Color.WHITE) value else -value
        }

        if (whiteKing == null || blackKing == null) return material
        if (abs(material) < DECISIVE_MATERIAL) return material

        // U dobijenoj završnici sam materijal ne razlikuje dobar potez od lošeg —
        // svi su jednaki. Ovo gura jaču stranu da protivničkog kralja tera ka
        // ivici i da mu prilazi svojim kraljem, što je i pravi plan matiranja.
        val whiteIsStronger = material > 0
        val strongKing = if (whiteIsStronger) whiteKing else blackKing
        val weakKing = if (whiteIsStronger) blackKing else whiteKing

        val mopUp = 16 * centreDistance(weakKing) + 5 * (14 - kingDistance(strongKing, weakKing))
        return material + if (whiteIsStronger) mopUp else -mopUp
    }

    /** Koliko je polje daleko od centra table. Ćošak daje 6, centar 0. */
    private fun centreDistance(square: Square): Int {
        val fileFromCentre = max(3 - square.fileIndex, square.fileIndex - 4)
        val rankFromCentre = max(3 - square.rankIndex, square.rankIndex - 4)
        return fileFromCentre + rankFromCentre
    }

    /** Rastojanje u potezima kralja. */
    private fun kingDistance(a: Square, b: Square): Int =
        max(abs(a.fileIndex - b.fileIndex), abs(a.rankIndex - b.rankIndex))
}
