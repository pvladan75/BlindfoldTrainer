package com.program.blindfoldtrainer.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ivice isečka: skok sa nule usred zvuka postaje rampa, sredina ostaje kakva je bila. */
class ClipEdgesTest {

    private val rate = 22050

    private fun constant(frames: Int, value: Int): ByteArray {
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            out[i * 2] = (value and 0xFF).toByte()
            out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun ByteArray.sample(frame: Int): Int =
        ((this[frame * 2].toInt() and 0xFF) or (this[frame * 2 + 1].toInt() shl 8)).toShort().toInt()

    @Test
    fun `isecak presecen usred zvuka pocinje i zavrsava blizu nule`() {
        val frames = rate / 10 // 100 ms
        val loud = ClipEdges.soften(constant(frames, 12000), rate)

        assertEquals(0, loud.sample(0))
        assertTrue("poslednji uzorak: ${loud.sample(frames - 1)}", loud.sample(frames - 1) < 100)
        // Rampa raste, pa stoji.
        assertTrue(loud.sample(10) < loud.sample(50))
        assertEquals(12000, loud.sample(rate * ClipEdges.FADE_IN_MILLIS / 1000))
        assertEquals(12000, loud.sample(frames / 2))
        assertEquals(12000, loud.sample(frames - 1 - rate * ClipEdges.FADE_OUT_MILLIS / 1000))
    }

    @Test
    fun `negativni uzorci se smanjuju ka nuli, ne ka minus jedan`() {
        val frames = rate / 10
        val soft = ClipEdges.soften(constant(frames, -12000), rate)
        assertEquals(0, soft.sample(0))
        assertTrue(soft.sample(frames - 1) > -100)
        assertEquals(-12000, soft.sample(frames / 2))
    }

    @Test
    fun `isecak kraci od rampi dobija po polovinu, bez izuzetka`() {
        val frames = 40 // manje od 2 ms
        val short = ClipEdges.soften(constant(frames, 8000), rate)
        assertEquals(frames * 2, short.size)
        assertEquals(0, short.sample(0))
        assertTrue(short.sample(frames - 1) < 1000)
        assertTrue(short.sample(frames / 2) > 6000)
        assertEquals(0, ClipEdges.soften(ByteArray(0), rate).size)
    }

    @Test
    fun `ulaz se ne menja`() {
        val original = constant(rate / 10, 5000)
        val copy = original.copyOf()
        ClipEdges.soften(original, rate)
        assertArrayEquals(copy, original)
    }
}
