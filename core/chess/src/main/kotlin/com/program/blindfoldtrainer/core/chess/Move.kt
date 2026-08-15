package com.program.blindfoldtrainer.core.chess

/**
 * Potez u obliku „sa polja, na polje, eventualna promocija".
 *
 * Namerno **ne** nosi oznaku rokade / en passant-a: te se izvode iz pozicije u
 * [Position.applyMove]. Time pozivalac (UI, engine, glasovni unos) ne mora da
 * zna ništa osim dva polja koja je korisnik dodirnuo, a nemoguće je napraviti
 * potez čija oznaka protivreči tabli.
 */
data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null
) {
    init {
        require(promotion == null || promotion in PROMOTION_CHOICES) {
            "Promocija u ${promotion?.name} nije dozvoljena"
        }
    }

    /** Duga algebarska notacija ("e2e4", "e7e8q") — format koji koristi UCI. */
    fun toUci(): String = "$from$to" + (promotion?.letter?.lowercaseChar() ?: "")

    override fun toString(): String = toUci()

    companion object {
        val PROMOTION_CHOICES = listOf(
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
        )

        /** Parsira UCI notaciju ("e2e4", "e7e8q"). Vraća `null` ako je neispravna. */
        fun fromUci(uci: String): Move? {
            val text = uci.trim().lowercase()
            if (text.length !in 4..5) return null
            val from = Square.fromAlgebraic(text.substring(0, 2)) ?: return null
            val to = Square.fromAlgebraic(text.substring(2, 4)) ?: return null
            val promotion = if (text.length == 5) {
                PieceType.fromLetter(text[4])?.takeIf { it in PROMOTION_CHOICES } ?: return null
            } else null
            return Move(from, to, promotion)
        }
    }
}
