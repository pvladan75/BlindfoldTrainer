package com.program.blindfoldtrainer.core.chess

enum class CastlingSide { KING, QUEEN }

data class CastlingRights(
    val whiteKingSide: Boolean = false,
    val whiteQueenSide: Boolean = false,
    val blackKingSide: Boolean = false,
    val blackQueenSide: Boolean = false
) {
    fun has(color: Color, side: CastlingSide): Boolean = when {
        color == Color.WHITE && side == CastlingSide.KING -> whiteKingSide
        color == Color.WHITE -> whiteQueenSide
        side == CastlingSide.KING -> blackKingSide
        else -> blackQueenSide
    }

    fun without(color: Color, side: CastlingSide): CastlingRights = when {
        color == Color.WHITE && side == CastlingSide.KING -> copy(whiteKingSide = false)
        color == Color.WHITE -> copy(whiteQueenSide = false)
        side == CastlingSide.KING -> copy(blackKingSide = false)
        else -> copy(blackQueenSide = false)
    }

    fun withoutAll(color: Color): CastlingRights =
        if (color == Color.WHITE) copy(whiteKingSide = false, whiteQueenSide = false)
        else copy(blackKingSide = false, blackQueenSide = false)

    fun toFen(): String = buildString {
        if (whiteKingSide) append('K')
        if (whiteQueenSide) append('Q')
        if (blackKingSide) append('k')
        if (blackQueenSide) append('q')
        if (isEmpty()) append('-')
    }

    companion object {
        val ALL = CastlingRights(true, true, true, true)
        val NONE = CastlingRights()

        fun fromFen(field: String): CastlingRights =
            if (field == "-") NONE
            else CastlingRights(
                whiteKingSide = field.contains('K'),
                whiteQueenSide = field.contains('Q'),
                blackKingSide = field.contains('k'),
                blackQueenSide = field.contains('q')
            )
    }
}

/**
 * Potpuno stanje partije: raspored figura plus sve što FEN nosi pored njega.
 *
 * Nepromenljiva je — [applyMove] vraća novu poziciju. Istorija partije je zato
 * obična lista pozicija, a „vrati potez" je uzimanje prethodnog elementa.
 */
data class Position(
    val board: Board,
    val sideToMove: Color = Color.WHITE,
    val castlingRights: CastlingRights = CastlingRights.ALL,
    val enPassantTarget: Square? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1
) {

    val isInCheck: Boolean get() = board.isKingInCheck(sideToMove)

    /**
     * Primenjuje potez. Rokada, en passant i promocija se prepoznaju iz same
     * pozicije, pa pozivalac šalje samo „sa polja, na polje".
     *
     * Ne proverava legalnost — za to služi [MoveGenerator.legalMoves] ili
     * [isLegal]. Ako polje [Move.from] nema figuru, vraća poziciju nepromenjenu.
     */
    fun applyMove(move: Move): Position {
        val moving = board[move.from] ?: return this
        val captured = board[move.to]

        val isPawnMove = moving.type == PieceType.PAWN
        val isEnPassant = isPawnMove &&
            move.to == enPassantTarget &&
            move.from.fileIndex != move.to.fileIndex &&
            captured == null
        val isCapture = captured != null || isEnPassant
        val isCastling = moving.type == PieceType.KING &&
            kotlin.math.abs(move.to.fileIndex - move.from.fileIndex) == 2

        // Poluhodovi se broje na osnovu table *pre* poteza — u staroj aplikaciji
        // se gledalo posle, pa je uzimanje uvek izgledalo kao da se dogodilo.
        val nextHalfmoveClock = if (isPawnMove || isCapture) 0 else halfmoveClock + 1

        val placedPiece = if (move.promotion != null && isPawnMove) {
            Piece(move.promotion, moving.color)
        } else {
            moving
        }

        var nextBoard = board.withPieces(move.from to null, move.to to placedPiece)

        if (isEnPassant) {
            // Pojedeni pešak ne stoji na odredišnom polju nego pored njega.
            val capturedPawnSquare = Square.of(move.to.fileIndex, move.from.rankIndex)
            if (capturedPawnSquare != null) {
                nextBoard = nextBoard.withPiece(capturedPawnSquare, null)
            }
        }

        if (isCastling) {
            val backRank = moving.color.backRank
            val isKingSide = move.to.fileIndex > move.from.fileIndex
            val rookFrom = Square.of(if (isKingSide) 7 else 0, backRank)
            val rookTo = Square.of(if (isKingSide) 5 else 3, backRank)
            if (rookFrom != null && rookTo != null) {
                val rook = nextBoard[rookFrom]
                nextBoard = nextBoard.withPieces(rookFrom to null, rookTo to rook)
            }
        }

        return Position(
            board = nextBoard,
            sideToMove = sideToMove.opposite,
            castlingRights = nextCastlingRights(move, moving),
            enPassantTarget = nextEnPassantTarget(move, moving),
            halfmoveClock = nextHalfmoveClock,
            fullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber
        )
    }

    /**
     * Pravo na rokadu se gubi kad se kralj pomeri, kad se top pomeri sa svog
     * početnog polja, i kad protivnik **pojede** top na njegovom početnom polju.
     */
    private fun nextCastlingRights(move: Move, moving: Piece): CastlingRights {
        var rights = castlingRights

        if (moving.type == PieceType.KING) {
            rights = rights.withoutAll(moving.color)
        }

        // Top koji napušta svoje početno polje, i top koji na njemu bude pojeden.
        for (square in listOf(move.from, move.to)) {
            rights = when (square.index) {
                0 -> rights.without(Color.WHITE, CastlingSide.QUEEN)   // a1
                7 -> rights.without(Color.WHITE, CastlingSide.KING)    // h1
                56 -> rights.without(Color.BLACK, CastlingSide.QUEEN)  // a8
                63 -> rights.without(Color.BLACK, CastlingSide.KING)   // h8
                else -> rights
            }
        }
        return rights
    }

    /** En passant polje postoji samo neposredno posle pešakovog skoka od dva polja. */
    private fun nextEnPassantTarget(move: Move, moving: Piece): Square? {
        if (moving.type != PieceType.PAWN) return null
        if (kotlin.math.abs(move.to.rankIndex - move.from.rankIndex) != 2) return null
        return Square.of(move.from.fileIndex, move.from.rankIndex + moving.color.pawnDirection)
    }

    fun legalMoves(): List<Move> = MoveGenerator.legalMoves(this)

    fun isLegal(move: Move): Boolean = legalMoves().contains(move)

    val isCheckmate: Boolean get() = legalMoves().isEmpty() && isInCheck

    val isStalemate: Boolean get() = legalMoves().isEmpty() && !isInCheck

    /** Pravilo 50 poteza: sto poluhodova bez poteza pešakom i bez uzimanja. */
    val isDrawByFiftyMoveRule: Boolean get() = halfmoveClock >= 100

    fun toFen(): String = Fen.format(this)

    override fun toString(): String = toFen()

    companion object {
        val STARTING = Position(
            board = Board.STARTING,
            sideToMove = Color.WHITE,
            castlingRights = CastlingRights.ALL,
            enPassantTarget = null,
            halfmoveClock = 0,
            fullmoveNumber = 1
        )

        /** Parsira FEN. Vraća `null` ako je neispravan. */
        fun fromFen(fen: String): Position? = Fen.parse(fen)
    }
}
