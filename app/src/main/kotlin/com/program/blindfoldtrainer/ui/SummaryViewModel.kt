package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.progress.SessionReward
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Govor i podešavanje koje treba sažetku sesije.
 *
 * Postoji odvojeno od [ProgressViewModel] jer sažetak ne računa napredak nego
 * ga samo saopštava — a bez ekrana je saopštavanje jedini način da se do njega
 * uopšte dođe.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    settings: SettingsRepository,
    private val speaker: Speaker
) : ViewModel() {

    /**
     * Zašto `Eagerly`: sažetak se pojavi tek na kraju sesije, ali se ovo čita
     * od podizanja ekrana. Moduli su već jednom platili to što su podešavanje
     * čitali iz kolektora koji tek treba da emituje — prvi kadar bi tada dobio
     * zatečenu vrednost, pa bi bez ekrana bljesnuo dijalog koji se ne vidi.
     */
    val eyesFree: StateFlow<Boolean> = settings.settings
        .map { it.eyesFree }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.DEFAULT.eyesFree)

    /**
     * Šta se sad može. Čeka svoj red: modul je na kraju sesije već izgovorio
     * rezultat, pa bi ovo inače preseklo baš ono zbog čega se sluša.
     */
    fun announceZones() = speaker.say(interrupt = false) { summaryZones }

    /**
     * Rezultat na zahtev — preseca, jer ono što se izričito traži ne treba
     * čekati.
     *
     * Rang i dostignuće se javljaju **da su osvojeni, ne koji**: imena su danas
     * resursi ekrana, a drugi spisak za govor bi značio dva izvora istine za
     * isto ime. Čuje se da se nešto dogodilo, na ekranu piše šta.
     */
    fun sayResult(result: SessionResult, reward: SessionReward?) = speaker.say {
        buildList {
            add(summaryResult(result.solved, result.attempted, result.mistakes))

            // Šta je sesija pomerila — bez ekrana je ovo jedini način da se to
            // sazna, a bez njega sažetak kaže koliko si radio ali ne i na čemu.
            result.bySkill.forEach { (skill, tally) ->
                add("${skillName(skill)} ${tally.solved} / ${tally.attempted}")
            }

            reward?.let {
                add(summaryXp(it.xp))
                if (it.isRankUp) add(summaryRankUp)
                if (it.newAchievements.isNotEmpty()) add(summaryAchievement)
            }
        }.joinToString(" ")
    }
}
