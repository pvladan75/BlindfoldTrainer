package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.ModelState
import com.program.blindfoldtrainer.core.audio.AndroidSpeaker
import com.program.blindfoldtrainer.core.audio.VoskModelStore
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val modelStore: VoskModelStore,
    speaker: AndroidSpeaker
) : ViewModel() {

    /** Jezici za koje uređaj ima TTS glas. Prazno dok se TTS ne podigne. */
    val speakableLanguages: StateFlow<Set<Language>> = speaker.availableLanguages

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings.DEFAULT)

    /** Šta se upravo preuzima ili raspakuje. */
    val modelState: StateFlow<ModelState> = modelStore.state

    /** Jezici čiji je paket na uređaju. */
    val installedLanguages: StateFlow<Set<Language>> = modelStore.installed

    fun onTheme(theme: ThemeChoice) = update { it.copy(theme = theme) }

    /**
     * Jedan jezik za oba smera. Paket za slušanje se ovim ne dira — on se
     * preuzima, a jezik se bira; ko izabere jezik bez paketa i dalje sve čuje,
     * samo mu mikrofon ne radi dok ga ne preuzme.
     */
    fun onLanguage(language: Language) = update { it.copy(language = language) }

    fun onSpeechRate(rate: Float) = update {
        // Klizač ume da vrati vrednost tik izvan opsega; Settings to inače odbija.
        it.copy(speechRate = rate.coerceIn(Settings.MIN_SPEECH_RATE, Settings.MAX_SPEECH_RATE))
    }

    fun onInstall(language: Language) = modelStore.download(language)

    fun onCancelInstall() = modelStore.cancel()

    fun onDelete(language: Language) = modelStore.delete(language)

    fun onEyesFree(enabled: Boolean) = update { it.copy(eyesFree = enabled) }

    fun onPhoneticAlphabet(enabled: Boolean) = update { it.copy(phoneticAlphabet = enabled) }

    fun onListenWholeMove(enabled: Boolean) = update { it.copy(listenWholeMove = enabled) }

    fun onSeparateLetterAndNumber(enabled: Boolean) =
        update { it.copy(separateLetterAndNumber = enabled) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
