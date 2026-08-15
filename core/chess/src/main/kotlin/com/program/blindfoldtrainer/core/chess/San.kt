package com.program.blindfoldtrainer.core.chess

/**
 * Standardna algebarska notacija ("Nf3", "exd5", "O-O", "e8=Q+").
 *
 * SAN se ne da pročitati bez pozicije: „Nf3" znači „onaj skakač koji sme na f3",
 * a koji je to zavisi od table. Zato i čitanje i pisanje traže [Position] i
 * oslanjaju se na generator poteza — isti onaj koji čuva pravila igre, pa
 * notacija ne može da se raziđe sa onim što je u partiji legalno.
 */
object San {

    /** Čita jedan potez u kontekstu date pozicije. `null` ako ga tu nema. */
    fun parse(position: Position, san: String): Move? {
        val text = san.trim().trimEnd(*DECORATIONS)
        if (text.isEmpty()) return null

        val legal = position.legalMoves()

        castlingMove(position, text, legal)?.let { return it }

        var body = text
        var promotion: PieceType? = null

        val equals = body.indexOf('=')
        if (equals != -1) {
            promotion = PieceType.fromLetter(body.getOrNull(equals + 1) ?: return null)
                ?.takeIf { it in Move.PROMOTION_CHOICES }
                ?: return null
            body = body.substring(0, equals)
        }

        val pieceType = PieceType.fromLetter(body.first())
            ?.takeIf { body.first().isUpperCase() }
            ?: PieceType.PAWN
        if (pieceType != PieceType.PAWN) body = body.drop(1)

        body = body.replace("x", "")

        if (body.length < 2) return null
        val target = Square.fromAlgebraic(body.takeLast(2)) ?: return null

        // Ono što ostane ispred odredišta je razlikovanje: kolona, red, ili oboje.
        val hint = body.dropLast(2)
        val hintFile = hint.firstOrNull { it in 'a'..'h' }?.let { it - 'a' }
        val hintRank = hint.firstOrNull { it in '1'..'8' }?.let { it - '1' }

        val candidates = legal.filter { move ->
            move.to == target &&
                move.promotion == promotion &&
                position.board[move.from]?.type == pieceType &&
                (hintFile == null || move.from.fileIndex == hintFile) &&
                (hintRank == null || move.from.rankIndex == hintRank)
        }

        // Dvosmislen zapis je greška u zapisu, a ne potez koji treba pogoditi.
        return candidates.singleOrNull()
    }

    /** Piše potez u SAN, sa razlikovanjem tačno onoliko koliko je potrebno. */
    fun format(position: Position, move: Move): String {
        val moving = position.board[move.from] ?: return move.toUci()
        val suffix = checkSuffix(position, move)

        if (moving.type == PieceType.KING && kotlin.math.abs(move.to.fileIndex - move.from.fileIndex) == 2) {
            return (if (move.to.fileIndex > move.from.fileIndex) "O-O" else "O-O-O") + suffix
        }

        val isCapture = position.board[move.to] != null ||
            (moving.type == PieceType.PAWN && move.to == position.enPassantTarget)
        val promotion = move.promotion?.let { "=${it.letter}" }.orEmpty()

        if (moving.type == PieceType.PAWN) {
            val prefix = if (isCapture) "${move.from.file}x" else ""
            return "$prefix${move.to}$promotion$suffix"
        }

        return "${moving.type.letter}${disambiguation(position, move, moving)}" +
            (if (isCapture) "x" else "") + "${move.to}$promotion$suffix"
    }

    private fun castlingMove(position: Position, text: String, legal: List<Move>): Move? {
        val normalized = text.replace('0', 'O')
        if (normalized != "O-O" && normalized != "O-O-O") return null

        val kingSquare = position.board.kingSquare(position.sideToMove) ?: return null
        val targetFile = if (normalized == "O-O") 6 else 2

        return legal.firstOrNull { move ->
            move.from == kingSquare &&
                move.to.rankIndex == kingSquare.rankIndex &&
                move.to.fileIndex == targetFile
        }
    }

    private fun disambiguation(position: Position, move: Move, moving: Piece): String {
        val rivals = position.legalMoves().filter { other ->
            other.to == move.to &&
                other.from != move.from &&
                position.board[other.from] == moving
        }
        if (rivals.isEmpty()) return ""

        val fileIsEnough = rivals.none { it.from.fileIndex == move.from.fileIndex }
        if (fileIsEnough) return move.from.file.toString()

        val rankIsEnough = rivals.none { it.from.rankIndex == move.from.rankIndex }
        if (rankIsEnough) return move.from.rank.toString()

        return move.from.toString()
    }

    private fun checkSuffix(position: Position, move: Move): String {
        val next = position.applyMove(move)
        return when {
            next.isCheckmate -> "#"
            next.isInCheck -> "+"
            else -> ""
        }
    }

    private val DECORATIONS = charArrayOf('+', '#', '!', '?')
}
