package com.program.blindfoldtrainer.core.chess

import kotlin.random.Random

/**
 * Zadatak u kom skakač mora da stigne do cilja **živ**.
 *
 * Razlika u odnosu na `KnightPath` nije u pretrazi nego u tome šta se traži:
 * tamo se pita kuda skakač može, ovde šta protivnik **drži**. U pravoj partiji
 * naslepo se figure ne gube zato što se zaboravi gde stoje, nego zato što se
 * zaboravi šta drže — a to je jedina veština koju do sada nijedan zadatak nije
 * ni dodirnuo.
 *
 * **Napad se računa statično:** crne figure se ne pomeraju. Drugačije se ni ne
 * može, jer bi svaki skok menjao poziciju i zadatak bi postao partija. Zato to i
 * mora da piše korisniku, da protivnik ne bi izgledao kao da spava.
 */
data class Minefield(
    val board: Board,
    val start: Square,
    val target: Square,
    /** Da li se izbegavaju i **napadnuta** polja, ili samo zauzeta. */
    val avoidAttacked: Boolean,
    /** Najkraći ispravan put, uključujući polazno i ciljno polje. */
    val solution: List<Square>
) {
    /** Koliko poteza traži najkraće rešenje. */
    val optimalMoves: Int get() = (solution.size - 1).coerceAtLeast(0)

    /** Da li se na ovo polje sme stati. */
    fun isSafe(square: Square): Boolean = board.isSafeForKnight(square, avoidAttacked)
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
 * Najkraći put skakača kroz polja na koja sme da stane.
 *
 * Ista pretraga u širinu kao u `KnightPath`, ali kroz **prorešetanu** tablu —
 * pa put ume i da ne postoji, za razliku od prazne table gde je svako polje
 * dostupno iz svakog.
 */
fun Board.safeKnightPath(
    from: Square,
    to: Square,
    avoidAttacked: Boolean
): List<Square> {
    if (from == to) return listOf(from)
    if (!isSafeForKnight(to, avoidAttacked)) return emptyList()

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
                if (neighbour == to) return rebuildPath(cameFrom, from, to)
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
 * Pravi zadatak koji **sigurno ima rešenje**.
 *
 * Postavlja se nasumično pa proverava; nerešiv raspored se odbacuje i pokušava
 * ponovo. Provera je jeftinija od pametnog postavljanja, a i poštenija — zadaci
 * ostaju raznoliki umesto da svi liče na obrazac po kom su građeni.
 *
 * Vraća `null` ako se u zadatom broju pokušaja ne nađe rešiv raspored; pozivalac
 * tada sme da olakša uslove.
 */
fun randomMinefield(
    pieceCount: Int,
    avoidAttacked: Boolean,
    minMoves: Int = 2,
    random: Random = Random,
    attempts: Int = 200
): Minefield? {
    repeat(attempts) {
        val squares = Square.ALL.shuffled(random)
        val start = squares[0]
        val target = squares[1]

        val occupied = squares.drop(2).take(pieceCount)
        val board = Board.of(
            occupied.associateWith { square ->
                val types = if (square.rank == 1 || square.rank == 8) HEAVY else ALL_TYPES
                Piece(types.random(random), Color.BLACK)
            }
        )

        if (!board.isSafeForKnight(start, avoidAttacked)) return@repeat
        if (!board.isSafeForKnight(target, avoidAttacked)) return@repeat

        val path = board.safeKnightPath(start, target, avoidAttacked)
        if (path.size - 1 < minMoves) return@repeat

        return Minefield(
            board = board,
            start = start,
            target = target,
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
