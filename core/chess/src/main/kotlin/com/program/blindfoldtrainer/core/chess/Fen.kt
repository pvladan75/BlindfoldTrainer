package com.program.blindfoldtrainer.core.chess

/**
 * Čitanje i pisanje FEN notacije.
 *
 * Podržava skraćene FEN-ove (bez brojača poluhodova i poteza) jer ih baze
 * zagonetki često tako zapisuju — nedostajuća polja dobijaju razumne vrednosti.
 */
object Fen {

    const val STARTING = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(fen: String): Position? {
        val fields = fen.trim().split(Regex("\\s+"))
        if (fields.size < 2) return null

        val board = Board.fromPlacementFen(fields[0]) ?: return null

        val sideToMove = when (fields[1].lowercase()) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> return null
        }

        val castlingRights = fields.getOrNull(2)
            ?.let { CastlingRights.fromFen(it) }
            ?: CastlingRights.NONE

        val enPassantTarget = fields.getOrNull(3)
            ?.takeIf { it != "-" }
            ?.let { Square.fromAlgebraic(it) ?: return null }

        val halfmoveClock = fields.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmoveNumber = fields.getOrNull(5)?.toIntOrNull() ?: 1

        return Position(
            board = board,
            sideToMove = sideToMove,
            castlingRights = castlingRights,
            enPassantTarget = enPassantTarget,
            halfmoveClock = halfmoveClock.coerceAtLeast(0),
            fullmoveNumber = fullmoveNumber.coerceAtLeast(1)
        )
    }

    fun format(position: Position): String = listOf(
        position.board.toPlacementFen(),
        if (position.sideToMove == Color.WHITE) "w" else "b",
        position.castlingRights.toFen(),
        position.enPassantTarget?.toString() ?: "-",
        position.halfmoveClock.toString(),
        position.fullmoveNumber.toString()
    ).joinToString(" ")
}
