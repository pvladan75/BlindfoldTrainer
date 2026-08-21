package com.program.blindfoldtrainer.feature.movement

import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pravila šetnje: zabrana ponavljanja, naizmenična dama, zaglavljivanje. */
class WalkTest {

    private fun square(name: String) = requireNotNull(Square.fromAlgebraic(name))

    private fun walk(piece: PieceType, from: String, moves: Int = 8) =
        Walk(piece = piece, start = square(from), targetMoves = moves)

    /** Više izgovorenih polja zaredom, uz ishod svakog. */
    private fun Walk.announceAll(vararg names: String): Pair<Walk, List<Step>> {
        var current = this
        val steps = names.map { name ->
            val (next, step) = current.announce(square(name))
            current = next
            step
        }
        return current to steps
    }

    @Test
    fun `legalan potez na novo polje se prima`() {
        val (after, step) = walk(PieceType.ROOK, "e4").announce(square("e7"))

        assertEquals(Step.ACCEPTED, step)
        assertEquals(square("e7"), after.current)
        assertEquals(1, after.movesMade)
    }

    @Test
    fun `figura koja tako ne ide se odbija i ostaje gde je bila`() {
        val (after, step) = walk(PieceType.ROOK, "e4").announce(square("d5"))

        assertEquals(Step.ILLEGAL, step)
        assertEquals(square("e4"), after.current)
        assertEquals(0, after.movesMade)
    }

    /**
     * Ovo je pravilo zbog kog vežba uopšte nešto traži: bez njega se topom sa e4
     * može reći e5, e4, e5 unedogled — sve legalno, nula napora.
     */
    @Test
    fun `na potroseno polje se ne moze nazad`() {
        val (after, steps) = walk(PieceType.ROOK, "e4").announceAll("e7", "e4")

        assertEquals(listOf(Step.ACCEPTED, Step.VISITED), steps)
        assertEquals(square("e7"), after.current)
    }

    /** „Tako se ne ide" i „tu si već bio" nisu isti promašaj. */
    @Test
    fun `nedohvatljivo potroseno polje je pogresan potez, ne ponavljanje`() {
        // Lovac ide a1–e5–h2; sa h2 se a1 više ne dohvata, pa je to geometrija.
        val (after, _) = walk(PieceType.BISHOP, "a1").announceAll("e5", "h2")
        val (_, step) = after.announce(square("a1"))

        assertEquals(Step.ILLEGAL, step)
    }

    /** Greška ne prekida šetnju — jedanaest dobrih poteza se ne poništava jednim. */
    @Test
    fun `posle greske se nastavlja sa istog polja`() {
        val (after, steps) = walk(PieceType.ROOK, "e4").announceAll("e7", "d5", "a7")

        assertEquals(listOf(Step.ACCEPTED, Step.ILLEGAL, Step.ACCEPTED), steps)
        assertEquals(square("a7"), after.current)
        assertEquals(2, after.movesMade)
        assertEquals(3, after.announced)
    }

    @Test
    fun `dubina broji poteze pre prve greske, a kasnije je ne diraju`() {
        val start = walk(PieceType.ROOK, "e4")
        assertNull(start.heldUntil)

        val (after, _) = start.announceAll("e7", "a7", "d5", "a1", "h1")

        // Dva dobra poteza pa promašaj; druga greška ne pomera broj.
        assertEquals(2, after.heldUntil)
    }

    @Test
    fun `dubina je nula kad se promasi odmah`() {
        val (after, _) = walk(PieceType.ROOK, "e4").announce(square("d5"))

        assertEquals(0, after.heldUntil)
    }

    /**
     * Dama se smenjuje: prvi potez kao top, drugi po dijagonali. Time se uz polje
     * drži i **čime si stigao** — dve veze umesto jedne.
     */
    @Test
    fun `dama ide naizmenicno top pa lovac`() {
        val start = walk(PieceType.QUEEN, "e4")
        assertEquals(PieceType.ROOK, start.mover)

        // Prvi potez mora biti pravolinijski; dijagonala se sad ne prima.
        assertEquals(Step.ILLEGAL, start.announce(square("g6")).second)

        val (afterFirst, step) = start.announce(square("e7"))
        assertEquals(Step.ACCEPTED, step)
        assertEquals(PieceType.BISHOP, afterFirst.mover)

        // Sad je red na dijagonalu, a pravolinijski potez se odbija.
        assertEquals(Step.ILLEGAL, afterFirst.announce(square("e2")).second)
        assertEquals(Step.ACCEPTED, afterFirst.announce(square("h4")).second)
    }

    /** Odbijen potez ne pomera red — dama i dalje čeka isti način kretanja. */
    @Test
    fun `promasaj ne menja to cime dama ide`() {
        val start = walk(PieceType.QUEEN, "e4")
        val (after, _) = start.announce(square("g6"))

        assertEquals(PieceType.ROOK, after.mover)
    }

    /**
     * Zaglavljivanje **nije greška nego kraj**: dužina je rezultat.
     *
     * Skakač u uglu ima samo dva polja. Kad se oba potroše a on se na kraju nađe
     * baš tamo, nema više kuda — i to je ishod vežbe, a ne pad.
     */
    @Test
    fun `zaglavljena setnja se zavrsava bez greske`() {
        val cornered = Walk(
            piece = PieceType.KNIGHT,
            start = square("a1"),
            targetMoves = 30,
            visited = listOf(square("b3"), square("c2"), square("a1"))
        )

        assertTrue(cornered.options.isEmpty())
        assertTrue(cornered.isStuck)
        assertTrue(cornered.isDone)
        // Zaglavljivanje ne upisuje grešku.
        assertNull(cornered.heldUntil)
    }

    @Test
    fun `setnja se zavrsava kad se odigra trazeni broj poteza`() {
        val (after, _) = walk(PieceType.ROOK, "a1", moves = 2).announceAll("a8", "h8")

        assertTrue(after.isDone)
        assertEquals(0, after.movesLeft)
    }

    @Test
    fun `sve dok ima nepotrosenih polja setnja nije zaglavljena`() {
        assertFalse(walk(PieceType.KNIGHT, "a1").isStuck)
        assertEquals(2, walk(PieceType.KNIGHT, "a1").options.size)
    }
}

/** Putanju koju aplikacija nacrta pravi isti mehanizam kao i šetnju. */
class RandomWalkPathTest {

    private val random = kotlin.random.Random(20260821)

    private fun path(piece: PieceType, moves: Int) = randomWalkPath(piece, moves, random)

    @Test
    fun `putanja ima trazenu duzinu, u poljima jedno vise od poteza`() {
        listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.QUEEN)
            .forEach { piece ->
                val squares = path(piece, moves = 6)
                assertEquals("$piece", 7, squares.size)
            }
    }

    /** Ako se putanja vrati na polje, prestala bi da bude ono što vezba trazi. */
    @Test
    fun `nijedno polje se ne ponavlja`() {
        repeat(50) {
            val squares = path(PieceType.KNIGHT, moves = 8)
            assertEquals(squares.size, squares.toSet().size)
        }
    }

    /** Svaki potez mora biti legalan za figuru, i to po pravilima same setnje. */
    @Test
    fun `svaki korak je legalan potez te figure`() {
        repeat(50) {
            val squares = path(PieceType.KNIGHT, moves = 7)
            var walk = Walk(PieceType.KNIGHT, squares.first(), targetMoves = squares.size)
            squares.drop(1).forEach { square ->
                val (next, step) = walk.announce(square)
                assertEquals(Step.ACCEPTED, step)
                walk = next
            }
        }
    }

    /** Dama i u nacrtanoj putanji ide naizmenicno — inace bi vezba ucila drugo pravilo. */
    @Test
    fun `dama se i u nacrtanoj putanji smenjuje`() {
        val squares = path(PieceType.QUEEN, moves = 6)
        var walk = Walk(PieceType.QUEEN, squares.first(), targetMoves = squares.size)
        squares.drop(1).forEach { square ->
            val (next, step) = walk.announce(square)
            assertEquals(Step.ACCEPTED, step)
            walk = next
        }
    }
}
