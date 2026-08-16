package com.program.blindfoldtrainer.core.chess

/**
 * Jedna partija pročitana iz PGN-a.
 *
 * [moves] su već provereni kroz pravila igre: partija koja sadrži potez koji se
 * ne da odigrati **uopšte ne nastaje**, umesto da pukne usred praćenja.
 */
data class PgnGame(
    val white: String,
    val black: String,
    val event: String,
    val date: String,
    val result: String,
    val moves: List<Move>,
    /** Isti potezi u SAN zapisu, onako kako se prikazuju korisniku. */
    val sanMoves: List<String>
) {
    val plyCount: Int get() = moves.size

    /** Pozicija posle [ply] poluhodova. Nula je početna pozicija. */
    fun positionAfter(ply: Int): Position =
        moves.take(ply.coerceIn(0, moves.size))
            .fold(Position.STARTING) { position, move -> position.applyMove(move) }
}

/**
 * Čitanje PGN-a.
 *
 * Podržava ono što stoji u bazama partija: zaglavlja u uglastim zagradama,
 * brojeve poteza, komentare i varijante. Ne podržava partije koje ne počinju iz
 * početne pozicije (`[FEN ...]`) — takve se preskaču.
 */
object Pgn {

    fun parseAll(text: String): List<PgnGame> = splitGames(text).mapNotNull { parse(it) }

    fun parse(text: String): PgnGame? {
        val headers = HEADER.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        if (headers.containsKey("FEN") || headers.containsKey("SetUp")) return null

        val movetext = text.lineSequence()
            .filterNot { it.trimStart().startsWith("[") }
            .joinToString(" ")

        val tokens = tokenize(movetext)
        if (tokens.isEmpty()) return null

        val moves = mutableListOf<Move>()
        val sanMoves = mutableListOf<String>()
        var position = Position.STARTING

        for (token in tokens) {
            val move = San.parse(position, token) ?: return null
            moves += move
            sanMoves += token
            position = position.applyMove(move)
        }

        return PgnGame(
            white = headers["White"].orEmpty(),
            black = headers["Black"].orEmpty(),
            event = headers["Event"].orEmpty(),
            date = headers["Date"].orEmpty(),
            result = headers["Result"] ?: "*",
            moves = moves,
            sanMoves = sanMoves
        )
    }

    /**
     * Deli fajl na partije. Prazan red pre novog `[Event` je granica; oslanjati
     * se samo na prazne redove ne valja, jer prazan red stoji i između zaglavlja
     * i poteza.
     */
    private fun splitGames(text: String): List<String> {
        val games = mutableListOf<StringBuilder>()
        var current: StringBuilder? = null
        var inMovetext = false

        for (line in text.lineSequence()) {
            val isHeader = line.trimStart().startsWith("[")
            if (isHeader && (current == null || inMovetext)) {
                current = StringBuilder().also { games += it }
                inMovetext = false
            }
            if (!isHeader && line.isNotBlank()) inMovetext = true
            current?.appendLine(line)
        }
        return games.map { it.toString() }
    }

    private fun tokenize(movetext: String): List<String> {
        val withoutComments = movetext
            .replace(COMMENT, " ")
            .replace(VARIATION, " ")

        return withoutComments.split(' ', '\t', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it in RESULTS }
            .filterNot { it.startsWith('$') }
            .mapNotNull { token ->
                // "12." i "12...e5" — broj poteza se odbacuje, potez uz njega ostaje.
                val afterNumber = token.substringAfterLast('.')
                afterNumber.takeIf { it.isNotEmpty() && it.first().isLetter() }
            }
    }

    private val HEADER = Regex("""\[\s*(\w+)\s+"([^"]*)"\s*\]""")
    private val COMMENT = Regex("""\{[^}]*}""")
    private val VARIATION = Regex("""\([^()]*\)""")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
}
