package com.program.blindfoldtrainer.core.chess

import kotlin.random.Random

/**
 * Zadatak u kom skakač mora da **da šah** — i da stigne dotle živ.
 *
 * Cilj **proizlazi iz pozicije**, a ne iz koordinate koju neko saopšti: gledaš
 * gde je kralj i tražiš polje sa kog ga napadaš, a da sam ne staneš pod udar.
 * Time se pamti jedan podatak manje, a traži jedan uvid više.
 *
 * Razlika u odnosu na `KnightPath` nije u pretrazi nego u tome šta se traži:
 * tamo se pita kuda skakač može, ovde šta protivnik **drži**. U pravoj partiji
 * naslepo se figure ne gube zato što se zaboravi gde stoje, nego zato što se
 * zaboravi šta drže.
 *
 * **Napad se računa statično:** crne figure se ne pomeraju. Drugačije se ni ne
 * može, jer bi svaki skok menjao poziciju i zadatak bi postao partija. Zato to i
 * mora da piše korisniku, da protivnik ne bi izgledao kao da spava.
 */
data class CheckPuzzle(
    val board: Board,
    val start: Square,
    val king: Square,
    /** Sva polja sa kojih skakač daje šah, a bezbedna su. */
    val checkingSquares: Set<Square>,
    /** Da li se izbegavaju i **napadnuta** polja, ili samo zauzeta. */
    val avoidAttacked: Boolean,
    /** Najkraći ispravan put do nekog od polja sa kojih se daje šah. */
    val solution: List<Square>
) {
    val optimalMoves: Int get() = (solution.size - 1).coerceAtLeast(0)

    fun isSafe(square: Square): Boolean = board.isSafeForKnight(square, avoidAttacked)

    /** Da li skakač sa ovog polja daje šah. */
    fun isCheck(square: Square): Boolean = square in checkingSquares
}

/**
 * Polje na koje beli skakač sme da stane.
 *
 * Zauzeto polje otpada uvek — na njemu bi skakač uzeo figuru, a zadatak traži da
 * prođe neprimećen. Uz [avoidAttacked] otpada i sve što crni drži.
 */
fun Board.isSafeForKnight(square: Square, avoidAttacked: Boolean): Boolean {
    if (this[square] != null) return false
    return !avoidAttacked || !isAttackedBy(square, Color.BLACK)
}

/**
 * Najkraći put skakača kroz polja na koja sme da stane, do **bilo kog** cilja.
 *
 * Ista pretraga u širinu kao u `KnightPath`, ali kroz **prorešetanu** tablu — pa
 * put ume i da ne postoji, za razliku od prazne table gde je svako polje
 * dostupno iz svakog. Skakač se ume zatvoriti sopstvenim skokovima.
 */
fun Board.safeKnightPath(
    from: Square,
    targets: Set<Square>,
    avoidAttacked: Boolean
): List<Square> {
    if (targets.isEmpty()) return emptyList()
    if (from in targets) return listOf(from)

    val cameFrom = HashMap<Square, Square>()
    var frontier = listOf(from)
    cameFrom[from] = from

    while (frontier.isNotEmpty()) {
        val next = mutableListOf<Square>()
        for (current in frontier) {
            for (neighbour in KnightPath.movesFrom(current)) {
                if (neighbour in cameFrom) continue
                if (!isSafeForKnight(neighbour, avoidAttacked)) continue

                cameFrom[neighbour] = current
                if (neighbour in targets) return rebuildPath(cameFrom, from, neighbour)
                next += neighbour
            }
        }
        frontier = next
    }

    return emptyList()
}

private fun rebuildPath(cameFrom: Map<Square, Square>, from: Square, to: Square): List<Square> {
    val path = mutableListOf(to)
    var current = to
    while (current != from) {
        current = cameFrom.getValue(current)
        path += current
    }
    return path.reversed()
}

/**
 * Polja sa kojih skakač daje šah kralju na [king], a sme da stane na njih.
 *
 * Skakačev skok je uzajaman: sa kojih polja on gađa kralja, na ta ista polja
 * kralj „gađa" njega — pa je spisak prosto njegov krug oko kralja, prorešetan
 * onim što je zauzeto ili držano.
 */
fun Board.checkingSquaresFor(king: Square, avoidAttacked: Boolean): Set<Square> =
    KnightPath.movesFrom(king)
        .filterTo(mutableSetOf()) { isSafeForKnight(it, avoidAttacked) }

/**
 * Pravi zadatak koji **sigurno ima rešenje**.
 *
 * Postavlja se nasumično pa proverava; nerešiv raspored se odbacuje i pokušava
 * ponovo. Provera je jeftinija od pametnog postavljanja, a i poštenija — zadaci
 * ostaju raznoliki umesto da svi liče na obrazac po kom su građeni.
 *
 * Vraća `null` ako se u zadatom broju pokušaja ne nađe rešiv raspored; pozivalac
 * tada sme da olakša uslove.
 */
fun randomCheckPuzzle(
    pieceCount: Int,
    avoidAttacked: Boolean,
    minMoves: Int = 2,
    random: Random = Random,
    attempts: Int = 300
): CheckPuzzle? {
    repeat(attempts) {
        val squares = Square.ALL.shuffled(random)
        val start = squares[0]
        val king = squares[1]

        val occupied = squares.drop(2).take(pieceCount)
        val pieces = occupied.associateWith { square ->
            val types = if (square.rank == 1 || square.rank == 8) HEAVY else ALL_TYPES
            Piece(types.random(random), Color.BLACK)
        } + (king to Piece(PieceType.KING, Color.BLACK))

        val board = Board.of(pieces)

        if (!board.isSafeForKnight(start, avoidAttacked)) return@repeat

        // Zadatak u kom je skakač već stigao nije zadatak.
        val checking = board.checkingSquaresFor(king, avoidAttacked)
        if (checking.isEmpty() || start in checking) return@repeat

        val path = board.safeKnightPath(start, checking, avoidAttacked)
        if (path.size - 1 < minMoves) return@repeat

        return CheckPuzzle(
            board = board,
            start = start,
            king = king,
            checkingSquares = checking,
            avoidAttacked = avoidAttacked,
            solution = path
        )
    }

    return null
}

private val ALL_TYPES = listOf(
    PieceType.PAWN,
    PieceType.KNIGHT,
    PieceType.BISHOP,
    PieceType.ROOK,
    PieceType.QUEEN
)

/** Na prvom i osmom redu pešaka nema — tamo bi već bio promovisan. */
private val HEAVY = ALL_TYPES - PieceType.PAWN
