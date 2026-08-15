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

    fun stop()

    /** 0.1 (vrlo sporo) do 2.0 (vrlo brzo). Normalno je 1.0. */
    fun setRate(rate: Float)
}

/**
 * Polje se izgovara slovo pa broj ("e four"), jer TTS "e4" pročita kao jednu
 * reč i teško se razaznaje.
 */
fun Square.spoken(): String = "$file ${rank}"

fun Move.spoken(): String = "${from.spoken()} to ${to.spoken()}"

fun Speaker.say(move: Move) = say(move.spoken())

fun Speaker.say(square: Square) = say(square.spoken())
