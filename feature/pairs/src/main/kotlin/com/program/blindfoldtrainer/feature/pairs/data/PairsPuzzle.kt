package com.program.blindfoldtrainer.feature.pairs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jedna zagonetka: početni raspored i niz koraka.
 *
 * Format je preuzet iz stare aplikacije da bi se sav postojeći sadržaj
 * (37 fajlova) mogao koristiti bez ponovnog generisanja.
 */
@Serializable
data class PairsPuzzle(
    val id: Long,
    /** Broj figura po vrsti, npr. `{"N":1,"B":1,"Q":1}`. */
    val pieces: Map<String, Int> = emptyMap(),
    /** Samo raspored figura — bez strane na potezu i ostalih FEN polja. */
    @SerialName("initial_fen") val initialFen: String,
    val solution: List<PairsStep> = emptyList()
)

@Serializable
data class PairsStep(
    /** Potez u obliku "a6-b4". */
    @SerialName("move") val moveNotation: String,
    /** Polje koje korisnik treba da pokaže posle ovog poteza. */
    @SerialName("interacting_square") val interactingSquare: String
)
