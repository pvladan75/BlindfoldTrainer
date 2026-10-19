package com.program.blindfoldtrainer.core.audio

/**
 * Ublažene ivice isečka: kratak uspon na početku i kratak pad na kraju.
 *
 * Isečena reč je isečena **po vremenima reči**, na sredini između dve susedne
 * — a glas između „king" i „on" ne staje, pa rez padne usred zvuka, na punoj
 * amplitudi. Tako isečen uzorak skače sa nule na nekoliko hiljada i nazad, i
 * to se čuje kao pucanj; „on" od sto milisekundi sa pucnjem na obe strane je
 * **šum**, ne reč — prijavljeno sa uređaja 19. oktobra 2026. pri čitanju
 * pozicije.
 *
 * Uspon od 5 ms i pad od 15 ms su kraći od bilo kog glasa, pa isečku koji već
 * počinje i završava tišinom ne menjaju ništa, a onom isečenom usred zvuka
 * skidaju skok. Rade na učitavanju, jednom po isečku; čist Kotlin, da test
 * vidi uzorke.
 */
object ClipEdges {

    const val FADE_IN_MILLIS = 5
    const val FADE_OUT_MILLIS = 15

    /** [pcm] je 16-bitni mono, little-endian; vraća nov niz, ulaz ne dira. */
    fun soften(
        pcm: ByteArray,
        sampleRate: Int,
        fadeInMillis: Int = FADE_IN_MILLIS,
        fadeOutMillis: Int = FADE_OUT_MILLIS
    ): ByteArray {
        val frames = pcm.size / 2
        if (frames == 0) return pcm.copyOf()
        // Kraći isečak od obe rampe zajedno: svaka dobije svoju polovinu.
        var fadeIn = (sampleRate.toLong() * fadeInMillis / 1000).toInt()
        var fadeOut = (sampleRate.toLong() * fadeOutMillis / 1000).toInt()
        if (fadeIn + fadeOut > frames) {
            fadeIn = frames / 2
            fadeOut = frames - fadeIn
        }

        val out = pcm.copyOf()
        for (i in 0 until fadeIn) out.scale(i, i.toFloat() / fadeIn)
        for (i in 0 until fadeOut) out.scale(frames - 1 - i, i.toFloat() / fadeOut)
        return out
    }

    private fun ByteArray.scale(frame: Int, gain: Float) {
        val at = frame * 2
        val sample = ((this[at].toInt() and 0xFF) or (this[at + 1].toInt() shl 8)).toShort().toInt()
        val scaled = (sample * gain).toInt()
        this[at] = (scaled and 0xFF).toByte()
        this[at + 1] = ((scaled shr 8) and 0xFF).toByte()
    }
}
