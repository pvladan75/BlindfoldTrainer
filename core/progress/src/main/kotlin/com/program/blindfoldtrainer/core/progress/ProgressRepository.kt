package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.SessionResult
import kotlinx.coroutines.flow.Flow

/**
 * Šta je jedna sesija donela. Vraća se odmah po upisu da bi sažetak mogao da
 * kaže koliko je poena stiglo i da li je rang preskočen — bez toga bi ekran
 * morao sam da poredi stanje pre i posle.
 */
data class SessionReward(
    val xp: Int,
    val rankBefore: Rank,
    val rankAfter: Rank,
    /** Dostignuća osvojena baš ovom sesijom. */
    val newAchievements: Set<Achievement> = emptySet()
) {
    val isRankUp: Boolean get() = rankAfter.ordinal > rankBefore.ordinal
}

/**
 * Jedini put kojim rezultat sesije ulazi u napredak.
 *
 * Interfejs stoji u čistom Kotlinu da bi se moduli i pravila bodovanja mogli
 * testirati bez baze i bez Androida; Room implementacija je u `:core:data`.
 */
interface ProgressRepository {

    /** Tekući napredak; emituje ponovo posle svakog upisa. */
    val snapshot: Flow<ProgressSnapshot>

    /** Upisuje sesiju i vraća šta je donela. */
    suspend fun record(result: SessionResult): SessionReward
}
