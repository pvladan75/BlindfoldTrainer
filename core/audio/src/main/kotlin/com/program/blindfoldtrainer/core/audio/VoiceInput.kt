package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import kotlinx.coroutines.flow.StateFlow

/** Stanje glasovnog unosa, onako kako ga UI treba da prikaže. */
sealed interface VoiceState {
    /** Model se još raspakuje ili učitava. */
    data object Preparing : VoiceState

    /** Spreman, ali ne sluša. */
    data object Idle : VoiceState

    data object Listening : VoiceState

    /**
     * Glasovni unos nije upotrebljiv na ovom uređaju — najčešće zato što
     * jezički model nije preuzet. UI treba da sakrije dugme za mikrofon.
     */
    data class Unavailable(val reason: String) : VoiceState
}

/**
 * Prepoznavanje izgovorenog polja.
 *
 * Rečnik je ograničen na šahovsku gramatiku (a1..h8 plus brojevi rečima), pa je
 * prepoznavanje znatno pouzdanije nego sa opštim modelom.
 */
interface VoiceInput {

    val state: StateFlow<VoiceState>

    /** Sluša do prvog prepoznatog polja, pa se sama zaustavlja. */
    fun listenForSquare(onSquare: (Square) -> Unit)

    fun stop()
}

/**
 * Pretvara ono što je prepoznato u polje.
 *
 * Vosk brojeve vraća rečima ("e four"), a razmaci padaju kako padnu, pa se
 * tekst prvo normalizuje.
 */
fun parseSpokenSquare(text: String): Square? {
    val normalized = text.lowercase()
        .replace("one", "1")
        .replace("two", "2")
        .replace("three", "3")
        .replace("four", "4")
        .replace("five", "5")
        .replace("six", "6")
        .replace("seven", "7")
        .replace("eight", "8")
        .filterNot { it.isWhitespace() }

    return Square.fromAlgebraic(normalized)
}
