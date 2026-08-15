package com.program.blindfoldtrainer.core.chess

/**
 * Raspored figura na tabli. **Nepromenljiv** — svaka izmena vraća novu tablu.
 *
 * Zahvaljujući tome se stara stanja mogu slobodno držati (undo, replay,
 * „kako je izgledalo pre tri poteza", blindfold animacija) bez kopiranja i bez
 * opasnosti da neko iz daljine promeni tablu koju već prikazuješ.
 */
class Board private constructor(private val squares: List<Piece?>) {

    operator fun get(square: Square): Piece? = squares[square.index]

    fun withPiece(square: Square, piece: Piece?): Board =
        Board(squares.toMutableList().also { it[square.index] = piece })

    /** Primenjuje više izmena odjednom — jedna kopija umesto jedne po izmeni. */
    fun withPieces(vararg changes: Pair<Square, Piece?>): Board =
        Board(squares.toMutableList().also { list ->
            for ((square, piece) in changes) list[square.index] = piece
        })

    /** Sva zauzeta polja sa figurama koje na njima stoje. */
    fun occupied(): List<Pair<Square, Piece>> =
        squares.mapIndexedNotNull { index, piece -> piece?.let { Square(index) to it } }

    fun piecesOf(color: Color): List<Pair<Square, Piece>> =
        occupied().filter { (_, piece) -> piece.color == color }

    fun kingSquare(color: Color): Square? =
        occupied().firstOrNull { (_, piece) ->
            piece.type == PieceType.KING && piece.color == color
        }?.first

    fun countOf(type: PieceType, color: Color): Int =
        occupied().count { (_, piece) -> piece.type == type && piece.color == color }

    val isEmpty: Boolean get() = squares.all { it == null }

    /** Deo FEN-a koji opisuje raspored figura (bez strane na potezu i ostalog). */
    fun toPlacementFen(): String = buildString {
        for (rankIndex in 7 downTo 0) {
            var emptyRun = 0
            for (fileIndex in 0..7) {
                val piece = squares[rankIndex * 8 + fileIndex]
                if (piece == null) {
                    emptyRun++
                } else {
                    if (emptyRun > 0) { append(emptyRun); emptyRun = 0 }
                    append(piece.fenChar)
                }
            }
            if (emptyRun > 0) append(emptyRun)
            if (rankIndex > 0) append('/')
        }
    }

    override fun toString(): String = toPlacementFen()

    override fun equals(other: Any?): Boolean =
        this === other || (other is Board && squares == other.squares)

    override fun hashCode(): Int = squares.hashCode()

    companion object {
        val EMPTY = Board(List(64) { null })

        fun of(pieces: Map<Square, Piece>): Board =
            Board(MutableList<Piece?>(64) { null }.also { list ->
                for ((square, piece) in pieces) list[square.index] = piece
            })

        /**
         * Parsira raspored iz FEN-a (samo prvo polje FEN stringa).
         * Vraća `null` ako raspored nije ispravan.
         */
        fun fromPlacementFen(placement: String): Board? {
            val list = MutableList<Piece?>(64) { null }
            val rows = placement.split('/')
            if (rows.size != 8) return null

            for ((rowIndex, row) in rows.withIndex()) {
                val rankIndex = 7 - rowIndex
                var fileIndex = 0
                for (char in row) {
                    when {
                        char.isDigit() -> fileIndex += char.digitToInt()
                        else -> {
                            if (fileIndex > 7) return null
                            val piece = Piece.fromFenChar(char) ?: return null
                            list[rankIndex * 8 + fileIndex] = piece
                            fileIndex++
                        }
                    }
                }
                if (fileIndex != 8) return null
            }
            return Board(list)
        }

        val STARTING: Board = requireNotNull(
            fromPlacementFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
        )
    }
}
