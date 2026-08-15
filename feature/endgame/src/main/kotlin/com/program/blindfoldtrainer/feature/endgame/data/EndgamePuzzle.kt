package com.program.blindfoldtrainer.feature.endgame.data

import android.content.Context
import com.program.blindfoldtrainer.core.model.Difficulty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val MATE_PATTERN = Regex("""Mate in (\d+)""", RegexOption.IGNORE_CASE)

/**
 * Dobijena pozicija koju treba privesti kraju.
 *
 * [evaluation] je opisna oznaka iz sadržaja ("Mate in 6") — služi za prikaz i
 * za procenu koliko poteza korisniku treba dozvoliti, ne za proveru rešenja.
 *
 * Regularni izraz stoji van klase namerno. Dok je bio u `private companion
 * object`, plugin za serijalizaciju je svoj `serializer()` stavljao baš u taj
 * privatni companion, pa je poziv iz [EndgameCatalog] prolazio prevođenje, a na
 * uređaju padao na `IllegalAccessError` — i modul je ostajao bez ijedne pozicije.
 */
@Serializable
data class EndgamePuzzle(
    val id: Int,
    val fen: String,
    val evaluation: String = ""
) {
    /** Broj poteza do mata iz oznake, ako se da pročitati. */
    val movesToMate: Int?
        get() = MATE_PATTERN.find(evaluation)?.groupValues?.get(1)?.toIntOrNull()
}

@Singleton
class EndgameCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<Difficulty, List<EndgamePuzzle>>()

    /**
     * Baca ako se sadržaj ne može pročitati. Greška se namerno ne guta ovde:
     * ranije je neuspelo učitavanje davalo prazan spisak, pa je na ekranu
     * izgledalo kao da zagonetki naprosto nema, a razlog je ostajao u logu.
     */
    suspend fun puzzles(difficulty: Difficulty): List<EndgamePuzzle> =
        withContext(Dispatchers.IO) {
            cache.getOrPut(difficulty) {
                val fileName = "${difficulty.name.lowercase()}_puzzles.json"
                context.assets.open(fileName).bufferedReader().use { reader ->
                    json.decodeFromString<List<EndgamePuzzle>>(reader.readText())
                }
            }
        }
}
