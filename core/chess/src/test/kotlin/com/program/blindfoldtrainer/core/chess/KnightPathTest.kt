package com.program.blindfoldtrainer.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnightPathTest {

    private fun square(notation: String) = requireNotNull(Square.fromAlgebraic(notation))

    @Test
    fun `skakac iz ugla ima dva poteza, iz centra osam`() {
        assertEquals(2, KnightPath.movesFrom(square("a1")).size)
        assertEquals(8, KnightPath.movesFrom(square("d4")).size)
    }

    @Test
    fun `poznata rastojanja`() {
        assertEquals(0, KnightPath.distance(square("a1"), square("a1")))
        assertEquals(1, KnightPath.distance(square("b1"), square("c3")))
        // Susedno polje po dijagonali je skakaču najskuplje u uglu.
        assertEquals(4, KnightPath.distance(square("a1"), square("b2")))
        assertEquals(6, KnightPath.distance(square("a1"), square("h8")))
    }

    @Test
    fun `nijedno polje nije dalje od sest poteza`() {
        for (from in Square.ALL) {
            for (to in Square.ALL) {
                val distance = KnightPath.distance(from, to)
                assertTrue(distance in 0..6, "$from -> $to = $distance")
            }
        }
    }

    @Test
    fun `rastojanje je simetricno`() {
        for (from in Square.ALL) {
            for (to in Square.ALL) {
                assertEquals(
                    KnightPath.distance(from, to),
                    KnightPath.distance(to, from),
                    "$from -> $to"
                )
            }
        }
    }

    @Test
    fun `putanja je legalna, najkraca i spaja zadata polja`() {
        for (from in Square.ALL) {
            for (to in Square.ALL) {
                val path = KnightPath.shortestPath(from, to)

                assertEquals(from, path.first(), "$from -> $to")
                assertEquals(to, path.last(), "$from -> $to")
                assertEquals(
                    KnightPath.distance(from, to),
                    path.size - 1,
                    "putanja $from -> $to nije najkraća: $path"
                )
                path.zipWithNext { a, b ->
                    assertTrue(KnightPath.isKnightMove(a, b), "$a -> $b nije potez skakača")
                }
            }
        }
    }

    @Test
    fun `putanja do istog polja je samo to polje`() {
        assertEquals(listOf(square("e4")), KnightPath.shortestPath(square("e4"), square("e4")))
    }

    @Test
    fun `polja na zadatom rastojanju su tacno ona koja se tako i mere`() {
        val from = square("d4")
        for (distance in 0..6) {
            val squares = KnightPath.squaresAtDistance(from, distance)
            squares.forEach {
                assertEquals(distance, KnightPath.distance(from, it), "$from -> $it")
            }
        }

        // Zbir po svim rastojanjima mora dati celu tablu.
        assertEquals(64, (0..6).sumOf { KnightPath.squaresAtDistance(from, it).size })
    }

    @Test
    fun `potez skakaca se prepoznaje`() {
        assertTrue(KnightPath.isKnightMove(square("g1"), square("f3")))
        assertFalse(KnightPath.isKnightMove(square("g1"), square("g3")))
        assertFalse(KnightPath.isKnightMove(square("e4"), square("e4")))
    }
}
