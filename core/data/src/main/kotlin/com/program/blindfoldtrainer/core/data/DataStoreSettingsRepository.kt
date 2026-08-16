package com.program.blindfoldtrainer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.SpeechLanguage
import com.program.blindfoldtrainer.core.model.ThemeChoice
import com.program.blindfoldtrainer.core.model.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Podešavanja u DataStore-u.
 *
 * Nepoznata ili oštećena vrednost se ne prenosi dalje nego se **vraća na
 * podrazumevanu**: podešavanje je udobnost, i ne sme da obori modul zato što je
 * u zapisu ostalo nešto iz starije verzije.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    override val settings: Flow<Settings> =
        context.settingsStore.data.map { preferences -> preferences.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences[THEME] = updated.theme.name
            preferences[SPEECH_RATE] = updated.speechRate
            preferences[VOICE_LANGUAGE] = updated.voiceLanguage.name
            preferences[SPEECH_LANGUAGE] = updated.speechLanguage.name
            preferences[PHONETIC_ALPHABET] = updated.phoneticAlphabet
            preferences[LISTEN_WHOLE_MOVE] = updated.listenWholeMove
            preferences[SEPARATE_LETTER_AND_NUMBER] = updated.separateLetterAndNumber
        }
    }

    private fun Preferences.toSettings() = Settings(
        theme = this[THEME]
            ?.let { name -> ThemeChoice.entries.find { it.name == name } }
            ?: Settings.DEFAULT.theme,
        speechRate = this[SPEECH_RATE]
            ?.coerceIn(Settings.MIN_SPEECH_RATE, Settings.MAX_SPEECH_RATE)
            ?: Settings.DEFAULT.speechRate,
        voiceLanguage = this[VOICE_LANGUAGE]
            ?.let { name -> VoiceLanguage.entries.find { it.name == name } }
            ?: Settings.DEFAULT.voiceLanguage,
        speechLanguage = this[SPEECH_LANGUAGE]
            ?.let { name -> SpeechLanguage.entries.find { it.name == name } }
            ?: Settings.DEFAULT.speechLanguage,
        phoneticAlphabet = this[PHONETIC_ALPHABET] ?: Settings.DEFAULT.phoneticAlphabet,
        listenWholeMove = this[LISTEN_WHOLE_MOVE] ?: Settings.DEFAULT.listenWholeMove,
        separateLetterAndNumber = this[SEPARATE_LETTER_AND_NUMBER]
            ?: Settings.DEFAULT.separateLetterAndNumber
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val PHONETIC_ALPHABET = booleanPreferencesKey("phonetic_alphabet")
        val LISTEN_WHOLE_MOVE = booleanPreferencesKey("listen_whole_move")
        val SEPARATE_LETTER_AND_NUMBER = booleanPreferencesKey("separate_letter_and_number")
    }
}
