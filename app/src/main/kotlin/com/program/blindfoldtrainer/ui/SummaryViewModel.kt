package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
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
     * Čeka svoj red. Modul je na kraju sesije već izgovorio rezultat, pa bi ovo
     * inače preseklo baš ono zbog čega se sluša.
     */
    fun announce(text: String) = speaker.say(text, interrupt = false)

    /** Preseca — jer je zatraženo dodirom, a ono što se traži ne treba čekati. */
    fun sayNow(text: String) = speaker.say(text, interrupt = true)
}
