package com.program.blindfoldtrainer.core.chess

/**
 * Polje na tabli, kodirano kao indeks 0..63 gde je `index = rank * 8 + file`,
 * `file` 0 = 'a', `rank` 0 = red 1. Time je nepostojeće polje nemoguće
 * predstaviti — nema provera opsega razasutih po kodu.
 */
@JvmInline
value class Square(val index: Int) {

    init {
        require(index in 0..63) { "Indeks polja mora biti 0..63, dobijeno $index" }
    }

    /** 0..7, gde je 0 = kolona 'a'. */
    val fileIndex: Int get() = index % 8

    /** 0..7, gde je 0 = red 1. */
    val rankIndex: Int get() = index / 8

    /** 'a'..'h' */
    val file: Char get() = 'a' + fileIndex

    /** 1..8 */
    val rank: Int get() = rankIndex + 1

    /** Da li je polje svetlo. a1 je tamno. */
    val isLight: Boolean get() = (fileIndex + rankIndex) % 2 == 1

    override fun toString(): String = "$file$rank"

    companion object {
        /** Vraća polje ili `null` ako su koordinate van table. */
        fun of(fileIndex: Int, rankIndex: Int): Square? =
            if (fileIndex in 0..7 && rankIndex in 0..7) Square(rankIndex * 8 + fileIndex) else null

        fun of(file: Char, rank: Int): Square? = of(file - 'a', rank - 1)

        /** Parsira algebarsku notaciju ("e4"). Vraća `null` ako je neispravna. */
        fun fromAlgebraic(notation: String): Square? {
            val trimmed = notation.trim().lowercase()
            if (trimmed.length != 2) return null
            val rank = trimmed[1].digitToIntOrNull() ?: return null
            return of(trimmed[0], rank)
        }

        val ALL: List<Square> = (0..63).map { Square(it) }
    }
}
