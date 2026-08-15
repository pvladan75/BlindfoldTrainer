package com.program.blindfoldtrainer.feature.pairs.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pristup zagonetkama iz `puzzles.zip`.
 *
 * Raspakuje se jednom u internu memoriju; učitani fajlovi se keširaju. Singleton
 * je da bi se to desilo jednom po pokretanju, a ne po ulasku u modul.
 */
@Singleton
class PuzzleCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, List<PairsPuzzle>>()
    private val mutex = Mutex()

    /**
     * Nasumična zagonetka sa tačno [pieceCount] figura i najmanje [minSteps]
     * koraka u rešenju. Vraća `null` ako takve nema.
     */
    suspend fun randomPuzzle(pieceCount: Int, minSteps: Int): PairsPuzzle? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureUnpacked()

                val candidates = puzzleFiles()
                    .filter { totalPiecesIn(it) == pieceCount }
                    .shuffled()

                for (fileName in candidates) {
                    val puzzle = load(fileName)
                        .filter { it.solution.size >= minSteps }
                        .randomOrNull()
                    if (puzzle != null) return@withLock puzzle
                }
                null
            }
        }

    /**
     * Ime fajla nosi sastav figura, npr. `puzzles_B1N2Q1.json` znači
     * jedan lovac, dva skakača i dama — ukupno četiri.
     */
    private fun totalPiecesIn(fileName: String): Int =
        PIECE_COUNT_PATTERN.findAll(fileName).sumOf { it.groupValues[2].toInt() }

    private fun puzzleFiles(): List<String> =
        puzzleDir().listFiles()?.map { it.name }?.filter { it.endsWith(".json") } ?: emptyList()

    /**
     * Baca ako se fajl ne može pročitati ili razumeti. Greška se namerno ne
     * guta u prazan spisak: modul bi tada prijavio da zagonetki nema, a pravi
     * razlog bi ostao samo u logu.
     */
    private fun load(fileName: String): List<PairsPuzzle> = cache.getOrPut(fileName) {
        val text = File(puzzleDir(), fileName).readText()
        json.decodeFromString<List<PairsPuzzle>>(text)
    }

    private fun puzzleDir() = File(context.filesDir, PUZZLE_DIR)

    private fun ensureUnpacked() {
        val dir = puzzleDir()
        // Prazan folder znači da je prošlo raspakivanje puklo na pola —
        // u tom slučaju pokušavamo ponovo umesto da zauvek ostanemo bez sadržaja.
        if (dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)) return

        dir.mkdirs()
        try {
            context.assets.open(ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Ime unosa iz arhive se ne koristi kao putanja —
                            // inače bi unos tipa "../.." pisao van foldera.
                            val safeName = File(entry.name).name
                            File(dir, safeName).outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (error: Throwable) {
            // Pola raspakovanog sadržaja je gore od nijednog: folder tada nije
            // prazan, pa gornja provera nikad ne bi dala novi pokušaj.
            dir.deleteRecursively()
            throw error
        }
    }

    private companion object {
        const val ASSET_NAME = "puzzles.zip"
        const val PUZZLE_DIR = "pairs-puzzles"
        val PIECE_COUNT_PATTERN = Regex("([BNQR])(\\d)")
    }
}
