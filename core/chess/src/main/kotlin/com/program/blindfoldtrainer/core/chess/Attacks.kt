package com.program.blindfoldtrainer.core.chess

/**
 * Da li boja [by] napada polje [target].
 *
 * Namerno je odvojeno od generisanja poteza. U staroj aplikaciji se napad
 * proveravao tako što bi se generisali potezi i gledalo da li neki vodi na
 * polje — a pešak dijagonalu generiše kao potez **samo ako tamo već stoji
 * protivnička figura**, pa prazno polje koje pešak brani nije izgledalo
 * napadnuto i kralj je smeo da stane na njega. Ovde se napad računa direktno.
 *
 * Rokada se ovde ne uzima u obzir: rokadom se ne napada nijedno polje.
 */
fun Board.isAttackedBy(target: Square, by: Color): Boolean =
    isAttackedByPawn(target, by) ||
        isAttackedByKnight(target, by) ||
        isAttackedByKing(target, by) ||
        isAttackedBySlider(target, by)

/**
 * Pešak boje [by] napada [target] ako stoji jedan red *iza* mete (gledano u
 * smeru svog kretanja) i jednu kolonu levo ili desno.
 */
private fun Board.isAttackedByPawn(target: Square, by: Color): Boolean {
    val originRank = target.rankIndex - by.pawnDirection
    for (fileOffset in intArrayOf(-1, 1)) {
        val origin = Square.of(target.fileIndex + fileOffset, originRank) ?: continue
        val piece = this[origin]
        if (piece != null && piece.color == by && piece.type == PieceType.PAWN) return true
    }
    return false
}

private fun Board.isAttackedByKnight(target: Square, by: Color): Boolean =
    KNIGHT_OFFSETS.any { (fileOffset, rankOffset) ->
        val origin = Square.of(target.fileIndex + fileOffset, target.rankIndex + rankOffset)
        origin != null && this[origin] == Piece(PieceType.KNIGHT, by)
    }

private fun Board.isAttackedByKing(target: Square, by: Color): Boolean =
    ALL_DIRECTIONS.any { (fileOffset, rankOffset) ->
        val origin = Square.of(target.fileIndex + fileOffset, target.rankIndex + rankOffset)
        origin != null && this[origin] == Piece(PieceType.KING, by)
    }

/** Top, lovac i dama — klizeće figure, zaustavlja ih prva figura na putu. */
private fun Board.isAttackedBySlider(target: Square, by: Color): Boolean {
    for ((fileStep, rankStep) in ALL_DIRECTIONS) {
        val isDiagonal = fileStep != 0 && rankStep != 0
        var fileIndex = target.fileIndex + fileStep
        var rankIndex = target.rankIndex + rankStep

        while (true) {
            val square = Square.of(fileIndex, rankIndex) ?: break
            val piece = this[square]
            if (piece != null) {
                if (piece.color == by) {
                    val matches = when (piece.type) {
                        PieceType.QUEEN -> true
                        PieceType.BISHOP -> isDiagonal
                        PieceType.ROOK -> !isDiagonal
                        else -> false
                    }
                    if (matches) return true
                }
                break // Prva figura u ovom smeru blokira dalji pogled.
            }
            fileIndex += fileStep
            rankIndex += rankStep
        }
    }
    return false
}

/** Da li je kralj date boje u šahu. */
/**
 * **Odakle** je polje napadnuto — sva polja sa kojih boja [by] gađa [target].
 *
 * Postoji uz [isAttackedBy] jer odgovara na drugo pitanje. Za pravila je
 * dovoljno znati **da li** je polje napadnuto; za vežbu je potrebno znati
 * **odakle**, jer se baš to u partiji naslepo zaboravlja: ne gde figure stoje,
 * nego šta drže.
 *
 * Računa se iz ugla svake protivničke figure, a ne iz ugla mete, jer je odgovor
 * spisak polja sa kojih se gađa.
 */
fun Board.attackersOf(target: Square, by: Color): Set<Square> =
    occupied()
        .filter { (square, piece) -> piece.color == by && attacks(square, target) }
        .mapTo(mutableSetOf()) { (square, _) -> square }

/** Da li figura sa [from] gađa [target]. Prazno polje ne gađa ništa. */
private fun Board.attacks(from: Square, target: Square): Boolean {
    val piece = this[from] ?: return false
    if (from == target) return false

    val fileStep = target.fileIndex - from.fileIndex
    val rankStep = target.rankIndex - from.rankIndex

    return when (piece.type) {
        // Pešak gađa dijagonalu ispred sebe — i kad je polje prazno. Upravo se
        // na toj razlici u staroj aplikaciji izgubio ceo bag sa kraljem.
        PieceType.PAWN ->
            rankStep == piece.color.pawnDirection && (fileStep == 1 || fileStep == -1)

        PieceType.KNIGHT ->
            KNIGHT_OFFSETS.any { (file, rank) -> file == fileStep && rank == rankStep }

        PieceType.KING -> fileStep in -1..1 && rankStep in -1..1

        PieceType.ROOK -> (fileStep == 0 || rankStep == 0) && isPathClear(from, target)

        PieceType.BISHOP ->
            kotlin.math.abs(fileStep) == kotlin.math.abs(rankStep) && isPathClear(from, target)

        PieceType.QUEEN ->
            (fileStep == 0 || rankStep == 0 ||
                kotlin.math.abs(fileStep) == kotlin.math.abs(rankStep)) &&
                isPathClear(from, target)
    }
}

/** Ima li ijedne figure između dva polja na istoj liniji ili dijagonali. */
private fun Board.isPathClear(from: Square, target: Square): Boolean {
    val fileStep = (target.fileIndex - from.fileIndex).coerceIn(-1, 1)
    val rankStep = (target.rankIndex - from.rankIndex).coerceIn(-1, 1)

    var fileIndex = from.fileIndex + fileStep
    var rankIndex = from.rankIndex + rankStep

    while (true) {
        val square = Square.of(fileIndex, rankIndex) ?: return false
        if (square == target) return true
        if (this[square] != null) return false

        fileIndex += fileStep
        rankIndex += rankStep
    }
}

fun Board.isKingInCheck(color: Color): Boolean {
    val kingSquare = kingSquare(color) ?: return false
    return isAttackedBy(kingSquare, color.opposite)
}

internal val KNIGHT_OFFSETS = listOf(
    1 to 2, 2 to 1, 2 to -1, 1 to -2,
    -1 to -2, -2 to -1, -2 to 1, -1 to 2
)

internal val ROOK_DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
internal val BISHOP_DIRECTIONS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
internal val ALL_DIRECTIONS = ROOK_DIRECTIONS + BISHOP_DIRECTIONS
