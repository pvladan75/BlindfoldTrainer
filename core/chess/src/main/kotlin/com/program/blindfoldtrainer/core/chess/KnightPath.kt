package com.program.blindfoldtrainer.core.chess

/**
 * Putanje skakača po praznoj tabli.
 *
 * Odvojeno od `MoveGenerator`-a jer se ovde ne igra partija: nema figura, nema
 * uzimanja ni šaha, pa se pretraga vrti po čistoj tabli 8×8. Najveće rastojanje
 * na praznoj tabli je šest poteza.
 */
object KnightPath {

    /** Polja do kojih skakač stiže jednim potezom. */
    fun movesFrom(square: Square): List<Square> = NEIGHBOURS[square.index].map { Square(it) }

    fun isKnightMove(from: Square, to: Square): Boolean = to.index in NEIGHBOURS[from.index]

    /** Najmanji broj poteza od [from] do [to]. Nula ako su ista polja. */
    fun distance(from: Square, to: Square): Int = distancesFrom(from)[to.index]

    /**
     * Jedna od najkraćih putanja, uključujući i polazno i odredišno polje.
     * Za ista polja vraća listu od jednog elementa.
     */
    fun shortestPath(from: Square, to: Square): List<Square> {
        if (from == to) return listOf(from)

        val cameFrom = IntArray(64) { UNVISITED }
        cameFrom[from.index] = from.index
        var frontier = intArrayOf(from.index)

        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Int>()
            for (current in frontier) {
                for (neighbour in NEIGHBOURS[current]) {
                    if (cameFrom[neighbour] != UNVISITED) continue
                    cameFrom[neighbour] = current
                    if (neighbour == to.index) return rebuild(cameFrom, from.index, to.index)
                    next += neighbour
                }
            }
            frontier = next.toIntArray()
        }

        // Nedostižno na 8×8 — svako polje je dostupno iz svakog.
        return emptyList()
    }

    /** Sva polja tačno [distance] poteza daleko. Prazna lista ako takvih nema. */
    fun squaresAtDistance(from: Square, distance: Int): List<Square> {
        if (distance < 0) return emptyList()
        val distances = distancesFrom(from)
        return Square.ALL.filter { distances[it.index] == distance }
    }

    private fun distancesFrom(from: Square): IntArray {
        val distances = IntArray(64) { UNVISITED }
        distances[from.index] = 0
        var frontier = intArrayOf(from.index)
        var step = 0

        while (frontier.isNotEmpty()) {
            step++
            val next = mutableListOf<Int>()
            for (current in frontier) {
                for (neighbour in NEIGHBOURS[current]) {
                    if (distances[neighbour] != UNVISITED) continue
                    distances[neighbour] = step
                    next += neighbour
                }
            }
            frontier = next.toIntArray()
        }
        return distances
    }

    private fun rebuild(cameFrom: IntArray, start: Int, target: Int): List<Square> {
        val path = mutableListOf<Square>()
        var current = target
        while (current != start) {
            path += Square(current)
            current = cameFrom[current]
        }
        path += Square(start)
        return path.reversed()
    }

    private const val UNVISITED = -1

    private val OFFSETS = listOf(
        1 to 2, 2 to 1, 2 to -1, 1 to -2,
        -1 to -2, -2 to -1, -2 to 1, -1 to 2
    )

    /** Susedi svakog polja, izračunati jednom. */
    private val NEIGHBOURS: Array<IntArray> = Array(64) { index ->
        val square = Square(index)
        OFFSETS.mapNotNull { (fileStep, rankStep) ->
            Square.of(square.fileIndex + fileStep, square.rankIndex + rankStep)?.index
        }.toIntArray()
    }
}
