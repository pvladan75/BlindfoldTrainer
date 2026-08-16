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

// Čitanje izgovorenog stoji u SpokenInput.kt. Preselilo se odande kad je unos
// prestao da bude prosto „tekst u polje": uz podešavanja treba da razume i NATO
// abecedu i kolonu bez reda.
