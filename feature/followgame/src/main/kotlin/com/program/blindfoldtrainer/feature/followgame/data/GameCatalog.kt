package com.program.blindfoldtrainer.feature.followgame.data

import android.content.Context
import com.program.blindfoldtrainer.core.chess.Pgn
import com.program.blindfoldtrainer.core.chess.PgnGame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Majstorske partije iz `assets`.
 *
 * Učitavaju se jednom po pokretanju i drže u memoriji — 60 partija je oko 40 KB
 * teksta, pa ponovno čitanje pri svakom ulasku u modul ne bi ništa donelo.
 *
 * Partija u kojoj se neki potez ne da odigrati **ne nastaje** (vidi [Pgn]), pa
 * modul nikad ne dobije zapis koji bi pukao usred praćenja.
 */
@Singleton
class GameCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cached: List<PgnGame>? = null

    /** Baca ako se sadržaj ne može pročitati — greška ide na ekran, ne u prazan spisak. */
    suspend fun games(): List<PgnGame> = withContext(Dispatchers.IO) {
        cached ?: context.assets.open(ASSET_NAME).bufferedReader()
            .use { Pgn.parseAll(it.readText()) }
            .also { cached = it }
    }

    private companion object {
        const val ASSET_NAME = "games.pgn"
    }
}
