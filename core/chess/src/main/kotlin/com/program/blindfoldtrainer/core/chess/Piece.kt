package com.program.blindfoldtrainer.core.chess

enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE

    /** Smer kretanja pešaka ove boje, izražen u redovima. */
    val pawnDirection: Int get() = if (this == WHITE) 1 else -1

    /** Red (0-indeksiran) sa kog pešak ove boje sme dva polja. */
    val pawnStartRank: Int get() = if (this == WHITE) 1 else 6

    /** Red (0-indeksiran) na kom pešak ove boje promoviše. */
    val promotionRank: Int get() = if (this == WHITE) 7 else 0

    /** Osnovni red kralja ove boje (0-indeksiran). */
    val backRank: Int get() = if (this == WHITE) 0 else 7
}

enum class PieceType(val letter: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K');

    companion object {
        fun fromLetter(letter: Char): PieceType? =
            entries.find { it.letter == letter.uppercaseChar() }
    }
}

data class Piece(val type: PieceType, val color: Color) {

    /** FEN karakter — veliko slovo za bele, malo za crne. */
    val fenChar: Char
        get() = if (color == Color.WHITE) type.letter else type.letter.lowercaseChar()

    override fun toString(): String = fenChar.toString()

    companion object {
        fun fromFenChar(char: Char): Piece? {
            val type = PieceType.fromLetter(char) ?: return null
            return Piece(type, if (char.isUpperCase()) Color.WHITE else Color.BLACK)
        }
    }
}
