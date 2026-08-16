package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings.DEFAULT)

    fun onTheme(theme: ThemeChoice) = update { it.copy(theme = theme) }

    fun onSpeechRate(rate: Float) = update {
        // Klizač ume da vrati vrednost tik izvan opsega; Settings to inače odbija.
        it.copy(speechRate = rate.coerceIn(Settings.MIN_SPEECH_RATE, Settings.MAX_SPEECH_RATE))
    }

    fun onNatoAlphabet(enabled: Boolean) = update { it.copy(natoAlphabet = enabled) }

    fun onListenWholeMove(enabled: Boolean) = update { it.copy(listenWholeMove = enabled) }

    fun onSeparateLetterAndNumber(enabled: Boolean) =
        update { it.copy(separateLetterAndNumber = enabled) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
