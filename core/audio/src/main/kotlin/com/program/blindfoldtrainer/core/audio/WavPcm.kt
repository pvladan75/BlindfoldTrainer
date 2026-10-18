package com.program.blindfoldtrainer.core.audio

/**
 * Čita PCM iz RIFF/WAVE fajla po njegovom sopstvenom zaglavlju.
 *
 * Hoda po spisku odeljaka umesto da pretpostavi zaglavlje od 44 bajta: alat
 * koji isečke pravi piše baš takvo, ali fajl koji bi došao iz drugog alata ume
 * da ima `LIST` ispred podataka, i čitač koji veruje pomaku bi tada pustio
 * metapodatke kao zvuk. Čist Kotlin, da ga i test proveri na istim fajlovima.
 */
object WavPcm {

    class Format(val sampleRate: Int, val bitsPerSample: Int, val channels: Int)

    class Clip(val format: Format, val pcm: ByteArray) {
        val frames: Int get() = pcm.size / (format.bitsPerSample / 8 * format.channels)
        val millis: Int get() = (frames * 1000L / format.sampleRate).toInt()
    }

    /** Format i podaci, ili izuzetak ako ovo nije PCM WAVE kakav rečnik traži. */
    fun read(bytes: ByteArray): Clip {
        require(bytes.size >= 12 && bytes.ascii(0) == "RIFF" && bytes.ascii(8) == "WAVE") { "not a RIFF/WAVE file" }

        var format: Format? = null
        var data: ByteArray? = null
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = bytes.ascii(at)
            val size = bytes.int32(at + 4)
            val start = at + 8
            val end = minOf(bytes.size, start + size)
            when (id) {
                "fmt " -> {
                    val audioFormat = bytes.int16(start)
                    require(audioFormat == 1) { "not PCM (format $audioFormat)" }
                    format = Format(
                        channels = bytes.int16(start + 2),
                        sampleRate = bytes.int32(start + 4),
                        bitsPerSample = bytes.int16(start + 14)
                    )
                }

                "data" -> data = bytes.copyOfRange(start, end)
            }
            // Odeljci su poravnati na paran broj bajtova.
            at = start + size + (size and 1)
        }

        val found = requireNotNull(format) { "no fmt chunk" }
        return Clip(found, requireNotNull(data) { "no data chunk" })
    }

    private fun ByteArray.ascii(at: Int): String = String(this, at, 4, Charsets.US_ASCII)

    private fun ByteArray.int16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.int32(at: Int): Int =
        (this[at].toInt() and 0xFF) or
            ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or
            ((this[at + 3].toInt() and 0xFF) shl 24)
}
