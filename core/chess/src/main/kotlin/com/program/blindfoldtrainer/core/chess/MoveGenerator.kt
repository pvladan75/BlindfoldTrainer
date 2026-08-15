package com.program.blindfoldtrainer.core.chess

/**
 * Generisanje poteza po pravilima šaha.
 *
 * Radi u dva koraka: prvo pseudo-legalni potezi (kretanje figura, blokade,
 * uzimanja), pa se odbace oni posle kojih sopstveni kralj ostaje u šahu.
 * Rokada se generiše posebno jer ima uslove koje taj filter ne hvata —
 * ne sme se rokirati iz šaha ni preko napadnutog polja.
 */
object MoveGenerator {

    fun legalMoves(position: Position): List<Move> =
        pseudoLegalMoves(position).filter { move ->
            val after = position.applyMove(move)
            !after.board.isKingInCheck(position.sideToMove)
        }

    fun legalMovesFrom(position: Position, from: Square): List<Move> =
        legalMoves(position).filter { it.from == from }

    fun pseudoLegalMoves(position: Position): List<Move> {
        val moves = mutableListOf<Move>()
        for ((square, piece) in position.board.piecesOf(position.sideToMove)) {
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(position, square, piece, moves)
                PieceType.KNIGHT -> generateStepMoves(position, square, piece, KNIGHT_OFFSETS, moves)
                PieceType.KING -> {
                    generateStepMoves(position, square, piece, ALL_DIRECTIONS, moves)
                    generateCastlingMoves(position, square, piece, moves)
                }
                PieceType.BISHOP -> generateSlidingMoves(position, square, piece, BISHOP_DIRECTIONS, moves)
                PieceType.ROOK -> generateSlidingMoves(position, square, piece, ROOK_DIRECTIONS, moves)
                PieceType.QUEEN -> generateSlidingMoves(position, square, piece, ALL_DIRECTIONS, moves)
            }
        }
        return moves
    }

    private fun generatePawnMoves(
        position: Position,
        from: Square,
        piece: Piece,
        moves: MutableList<Move>
    ) {
        val board = position.board
        val direction = piece.color.pawnDirection

        // Kretanje unapred — samo na prazno polje.
        val oneStep = Square.of(from.fileIndex, from.rankIndex + direction)
        if (oneStep != null && board[oneStep] == null) {
            addPawnMove(from, oneStep, piece, moves)

            if (from.rankIndex == piece.color.pawnStartRank) {
                val twoStep = Square.of(from.fileIndex, from.rankIndex + 2 * direction)
                if (twoStep != null && board[twoStep] == null) {
                    moves.add(Move(from, twoStep))
                }
            }
        }

        // Uzimanje po dijagonali, uključujući en passant.
        for (fileOffset in intArrayOf(-1, 1)) {
            val target = Square.of(from.fileIndex + fileOffset, from.rankIndex + direction) ?: continue
            val occupant = board[target]
            val isNormalCapture = occupant != null && occupant.color != piece.color
            val isEnPassant = occupant == null && target == position.enPassantTarget
            if (isNormalCapture || isEnPassant) {
                addPawnMove(from, target, piece, moves)
            }
        }
    }

    /** Na poslednjem redu pešak mora da promoviše — dodaju se sve četiri opcije. */
    private fun addPawnMove(from: Square, to: Square, piece: Piece, moves: MutableList<Move>) {
        if (to.rankIndex == piece.color.promotionRank) {
            for (promotion in Move.PROMOTION_CHOICES) {
                moves.add(Move(from, to, promotion))
            }
        } else {
            moves.add(Move(from, to))
        }
    }

    private fun generateStepMoves(
        position: Position,
        from: Square,
        piece: Piece,
        offsets: List<Pair<Int, Int>>,
        moves: MutableList<Move>
    ) {
        for ((fileOffset, rankOffset) in offsets) {
            val target = Square.of(from.fileIndex + fileOffset, from.rankIndex + rankOffset) ?: continue
            val occupant = position.board[target]
            if (occupant == null || occupant.color != piece.color) {
                moves.add(Move(from, target))
            }
        }
    }

    private fun generateSlidingMoves(
        position: Position,
        from: Square,
        piece: Piece,
        directions: List<Pair<Int, Int>>,
        moves: MutableList<Move>
    ) {
        for ((fileStep, rankStep) in directions) {
            var fileIndex = from.fileIndex + fileStep
            var rankIndex = from.rankIndex + rankStep

            while (true) {
                val target = Square.of(fileIndex, rankIndex) ?: break
                val occupant = position.board[target]
                if (occupant == null) {
                    moves.add(Move(from, target))
                } else {
                    if (occupant.color != piece.color) moves.add(Move(from, target))
                    break
                }
                fileIndex += fileStep
                rankIndex += rankStep
            }
        }
    }

    /**
     * Rokada. Uslovi koje stara aplikacija nije proveravala: da top uopšte
     * stoji na svom polju, da kralj nije u šahu i da ne prelazi preko
     * napadnutog polja.
     */
    private fun generateCastlingMoves(
        position: Position,
        from: Square,
        piece: Piece,
        moves: MutableList<Move>
    ) {
        val backRank = piece.color.backRank
        val kingHome = Square.of(4, backRank) ?: return
        if (from != kingHome) return

        // Rokada iz šaha nije dozvoljena.
        if (position.board.isKingInCheck(piece.color)) return

        for (side in CastlingSide.entries) {
            if (!position.castlingRights.has(piece.color, side)) continue

            val isKingSide = side == CastlingSide.KING
            val rookHome = Square.of(if (isKingSide) 7 else 0, backRank) ?: continue
            if (position.board[rookHome] != Piece(PieceType.ROOK, piece.color)) continue

            // Sva polja između kralja i topa moraju biti prazna.
            val emptyFiles = if (isKingSide) listOf(5, 6) else listOf(1, 2, 3)
            val allEmpty = emptyFiles.all { fileIndex ->
                val square = Square.of(fileIndex, backRank)
                square != null && position.board[square] == null
            }
            if (!allEmpty) continue

            // Kralj ne sme da pređe preko napadnutog polja. Odredišno polje
            // pokriva opšti filter legalnosti, ali polje između — ne.
            val crossingFile = if (isKingSide) 5 else 3
            val crossing = Square.of(crossingFile, backRank) ?: continue
            if (position.board.isAttackedBy(crossing, piece.color.opposite)) continue

            val destination = Square.of(if (isKingSide) 6 else 2, backRank) ?: continue
            moves.add(Move(from, destination))
        }
    }
}
