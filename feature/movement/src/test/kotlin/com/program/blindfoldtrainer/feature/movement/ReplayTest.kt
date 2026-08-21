package com.program.blindfoldtrainer.feature.movement

import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prikaz odrađene šetnje: dokle je stigao i šta je iza njega ostalo obojeno. */
class ReplayTest {

    private fun squares(vararg names: String) =
        names.map { requireNotNull(Square.fromAlgebraic(it)) }

    private fun replay(step: Int) = Replay(
        piece = PieceType.KNIGHT,
        path = squares("d3", "c5", "d7", "f8", "g6"),
        step = step
    )

    @Test
    fun `na pocetku je figura na polaznom polju i iza nje nema niceg`() {
        val start = replay(0)

        assertEquals(Square.fromAlgebraic("d3"), start.current)
        assertTrue(start.behind.isEmpty())
    }

    /**
     * Iza figure ostaje **sve kroz šta se prošlo, bez polja na kom sad stoji**:
     * ono se boji drugačije, pa bi u oba skupa bilo dvaput obojeno.
     */
    @Test
    fun `iza figure stoje sva predjena polja osim tekuceg`() {
        val middle = replay(2)

        assertEquals(Square.fromAlgebraic("d7"), middle.current)
        assertEquals(squares("d3", "c5"), middle.behind)
    }

    @Test
    fun `na kraju je obojena cela putanja`() {
        val end = replay(4)

        assertEquals(Square.fromAlgebraic("g6"), end.current)
        assertEquals(squares("d3", "c5", "d7", "f8"), end.behind)
        assertEquals(end.path.size, end.behind.size + 1)
    }

    /** Korak van putanje ne sme da obori prikaz; svodi se na najbliži postojeći. */
    @Test
    fun `korak van putanje se svodi na krajeve`() {
        assertEquals(Square.fromAlgebraic("d3"), replay(-3).current)
        assertEquals(Square.fromAlgebraic("g6"), replay(99).current)
    }

    /**
     * Trag je **prečka prevedena u sliku**: uz punu podršku ostaje ceo, pa se
     * putanja pročita sa table i ništa se ne pamti.
     */
    @Test
    fun `pun trag ostavlja sva predjena polja`() {
        assertEquals(squares("d3", "c5", "d7"), replay(3).behind)
    }

    /** Dva polja: vidi se odakle si došao, ali se oblik ne moze procitati. */
    @Test
    fun `deo slike ostavlja samo poslednja dva polja`() {
        val partial = replay(4).copy(trail = 2)

        assertEquals(squares("d7", "f8"), partial.behind)
    }

    /** Bez traga se pamti sve; vidi se samo polje na kom figura stoji. */
    @Test
    fun `bez traga iza figure nema niceg`() {
        val trace = replay(4).copy(trail = 0)

        assertTrue(trace.behind.isEmpty())
        assertEquals(Square.fromAlgebraic("g6"), trace.current)
    }

    /** Šetnja koja se zaglavila odmah ima samo polazno polje — i to je valjan prikaz. */
    @Test
    fun `putanja od jednog polja se prikazuje bez greske`() {
        val single = Replay(PieceType.ROOK, squares("a1"))

        assertEquals(Square.fromAlgebraic("a1"), single.current)
        assertTrue(single.behind.isEmpty())
    }
}
