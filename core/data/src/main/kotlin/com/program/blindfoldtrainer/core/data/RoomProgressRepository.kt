package com.program.blindfoldtrainer.core.data

import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.progress.ProgressRepository
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SessionReward
import com.program.blindfoldtrainer.core.progress.Xp
import com.program.blindfoldtrainer.core.progress.toProgressSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Napredak nad Room istorijom.
 *
 * Sabiranje se radi u Kotlinu, a ne SQL-om, namerno: pravilo bodovanja tako
 * ostaje na jednom mestu u `:core:progress` i pokriveno je testovima bez baze.
 * Istorija je nekoliko stotina redova u najgorem slučaju, pa cena ne postoji.
 */
@Singleton
class RoomProgressRepository @Inject constructor(
    private val dao: SessionDao
) : ProgressRepository {

    override val snapshot: Flow<ProgressSnapshot> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toResult() }.toProgressSnapshot() }

    override suspend fun record(result: SessionResult): SessionReward {
        val before = dao.all().mapNotNull { it.toResult() }.toProgressSnapshot()
        dao.insert(result.toEntity(System.currentTimeMillis()))

        // Rang posle se računa iz snimka, ne iz baze: upis je već obavljen, a
        // ponovno čitanje bi samo dalo isti zbir.
        val after = before + result
        return SessionReward(
            xp = Xp.forSession(result),
            rankBefore = before.rank,
            rankAfter = after.rank,
            newAchievements = after.achievements - before.achievements
        )
    }
}
