package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Square

/**
 * Izgovaranje teksta. Iza interfejsa je da bi se u testovima mogao zameniti
 * lažnjakom, i da moduli ne zavise od `android.speech.tts` direktno.
 */
interface Speaker {

    /** Prekida ono što se trenutno izgovara i kaže [text]. */
    fun say(text: String)

    /**
     * Izgovara polje na jeziku koji je izabran za govor.
     *
     * Formatiranje je ovde, a ne kod pozivaoca, jer zavisi od jezika — a moduli
     * za jezik ne znaju niti treba da znaju.
     */
    fun say(square: Square)

    fun say(move: Move)

    fun stop()

    /** 0.1 (vrlo sporo) do 2.0 (vrlo brzo). Normalno je 1.0. */
    fun setRate(rate: Float)
}

/**
 * Polje kao izgovorene reči na datom jeziku ("e four", „e vier", „е четыре").
 *
 * Koriste se **iste reči kojima se i sluša**, one iz [VoiceWords] — jedna tabela
 * služi oba smera. Slovo pa broj, jer TTS „e4" pročita kao jednu reč i teško se
 * razaznaje.
 */
fun Square.spoken(words: VoiceWords): String {
    val fileWord = words.files.entries.first { it.value == file }.key
    val rankWord = words.ranks.entries.first { it.value == rank.digitToChar() }.key
    return "$fileWord $rankWord"
}

/**
 * Potez kao dva polja. Umesto veznika stoji zarez: „to", „nach", „на" se razlikuju
 * po jezicima, a pauza radi isti posao svuda.
 */
fun Move.spoken(words: VoiceWords): String = "${from.spoken(words)}, ${to.spoken(words)}"
