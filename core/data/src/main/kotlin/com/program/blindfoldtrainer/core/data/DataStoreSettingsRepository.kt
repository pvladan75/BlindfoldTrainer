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
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.model.ThemeChoice
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
            preferences[LANGUAGE] = updated.language.name
            preferences[EYES_FREE] = updated.eyesFree
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
        // Ranije su ovde stajala dva jezika. Nasledje se cita iz starog kljuca
        // za izgovor, jer je on jedini imao i srpski — ko je slusao srpski,
        // nastavlja da ga slusa.
        language = this[LANGUAGE]
            ?.let { name -> Language.entries.find { it.name == name } }
            ?: this[SPEECH_LANGUAGE]
                ?.let { name -> Language.entries.find { it.name == name } }
            ?: Settings.DEFAULT.language,
        eyesFree = this[EYES_FREE] ?: Settings.DEFAULT.eyesFree,
        phoneticAlphabet = this[PHONETIC_ALPHABET] ?: Settings.DEFAULT.phoneticAlphabet,
        listenWholeMove = this[LISTEN_WHOLE_MOVE] ?: Settings.DEFAULT.listenWholeMove,
        separateLetterAndNumber = this[SEPARATE_LETTER_AND_NUMBER]
            ?: Settings.DEFAULT.separateLetterAndNumber
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val LANGUAGE = stringPreferencesKey("language")

        /** Samo za citanje: podesavanja upisana pre spajanja dva jezika u jedan. */
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val EYES_FREE = booleanPreferencesKey("eyes_free")
        val PHONETIC_ALPHABET = booleanPreferencesKey("phonetic_alphabet")
        val LISTEN_WHOLE_MOVE = booleanPreferencesKey("listen_whole_move")
        val SEPARATE_LETTER_AND_NUMBER = booleanPreferencesKey("separate_letter_and_number")
    }
}
