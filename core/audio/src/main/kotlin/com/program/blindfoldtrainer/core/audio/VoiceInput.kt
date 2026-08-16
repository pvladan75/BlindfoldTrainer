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

    /**
     * Sluša dok [onSpoken] vraća `true`.
     *
     * Predaje se **ono što je rečeno**, neprotumačeno: polje, ceo potez, ili
     * figura i odredište. Modul odlučuje šta od toga ume — Završnica prima
     * potez, ostali samo polje.
     *
     * Nastavak ide **bez gašenja mikrofona**. Ranije se za drugo polje slušanje
     * gasilo pa paljelo posle kratke pauze, a to ume tiho da ne uspe: prethodni
     * snimač se još zatvara kad se traži novi, pa mikrofon deluje mrtav.
     * Pogađati dužinu te pauze je uzaludno — jednostavnije je ne prekidati.
     */
    fun listen(onSpoken: (SpokenInput) -> Boolean)

    fun stop()
}

/**
 * Sluša do prvog prepoznatog polja, pa se sama zaustavlja.
 *
 * Za module u kojima je odgovor jedno polje. Ako se izgovori ceo potez, uzima se
 * **polazno** polje: bolje uzeti prvo rečeno nego ćutati.
 */
fun VoiceInput.listenForSquare(onSquare: (Square) -> Unit) =
    listen { spoken ->
        spoken.firstSquare()?.let(onSquare)
        false
    }

/** Prvo polje koje je izgovor pomenuo, ako ga ima. */
fun SpokenInput.firstSquare(): Square? = when (this) {
    is SpokenInput.Full -> square
    is SpokenInput.Move -> from
    is SpokenInput.PieceMove -> to
    else -> null
}

// Čitanje izgovorenog stoji u SpokenInput.kt. Preselilo se odande kad je unos
// prestao da bude prosto „tekst u polje": uz podešavanja treba da razume i fonetsku
// abecedu i kolonu bez reda.
