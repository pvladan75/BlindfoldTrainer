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
import com.program.blindfoldtrainer.core.model.ProfileRepository
import com.program.blindfoldtrainer.core.model.ThemeChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Podešavanja u DataStore-u.
 *
 * Nepoznata ili oštećena vrednost se ne prenosi dalje nego se **vraća na
 * podrazumevanu**: podešavanje je udobnost, i ne sme da obori modul zato što je
 * u zapisu ostalo nešto iz starije verzije.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository
) : SettingsRepository {

    /**
     * Podešavanja **aktivnog profila**.
     *
     * Otac i sin se razlikuju baš u onome što ovde stoji — jezik, brzina govora,
     * „bez ekrana" — pa bi zajednička podešavanja obesmislila razdvajanje.
     *
     * Ključevi nose prefiks profila. Sve što je upisano **pre** profila čita
     * prvi profil, jer je to bilo njegovo; vidi [Preferences.toSettings].
     */
    override val settings: Flow<Settings> = profiles.active.flatMapLatest { profile ->
        context.settingsStore.data.map { preferences -> preferences.toSettings(profile.id) }
    }

    override suspend fun update(transform: (Settings) -> Settings) {
        val profileId = profiles.active.first().id

        context.settingsStore.edit { preferences ->
            val updated = transform(preferences.toSettings(profileId))
            preferences[theme(profileId)] = updated.theme.name
            preferences[speechRate(profileId)] = updated.speechRate
            preferences[language(profileId)] = updated.language.name
            preferences[eyesFree(profileId)] = updated.eyesFree
            preferences[phonetic(profileId)] = updated.phoneticAlphabet
            preferences[wholeMove(profileId)] = updated.listenWholeMove
            preferences[separate(profileId)] = updated.separateLetterAndNumber
        }
    }

    /**
     * Čita podešavanja jednog profila.
     *
     * Prvi profil nasleđuje ono što je upisano pre nego što su profili
     * postojali: ključ bez prefiksa je bio njegov, pa se koristi kad prefiksa
     * još nema. Ostali profili kreću od podrazumevanog.
     */
    private fun Preferences.toSettings(profileId: Long) = Settings(
        theme = (this[theme(profileId)] ?: legacy(profileId, THEME))
            ?.let { name -> ThemeChoice.entries.find { it.name == name } }
            ?: Settings.DEFAULT.theme,
        speechRate = (this[speechRate(profileId)] ?: legacy(profileId, SPEECH_RATE))
            ?.coerceIn(Settings.MIN_SPEECH_RATE, Settings.MAX_SPEECH_RATE)
            ?: Settings.DEFAULT.speechRate,
        // Ranije su ovde stajala dva jezika. Nasledje se cita iz starog kljuca
        // za izgovor, jer je on jedini imao i srpski — ko je slusao srpski,
        // nastavlja da ga slusa.
        language = (this[language(profileId)] ?: legacy(profileId, LANGUAGE))
            ?.let { name -> Language.entries.find { it.name == name } }
            ?: legacy(profileId, SPEECH_LANGUAGE)
                ?.let { name -> Language.entries.find { it.name == name } }
            ?: Settings.DEFAULT.language,
        eyesFree = this[eyesFree(profileId)] ?: legacy(profileId, EYES_FREE)
            ?: Settings.DEFAULT.eyesFree,
        phoneticAlphabet = this[phonetic(profileId)] ?: legacy(profileId, PHONETIC_ALPHABET)
            ?: Settings.DEFAULT.phoneticAlphabet,
        listenWholeMove = this[wholeMove(profileId)] ?: legacy(profileId, LISTEN_WHOLE_MOVE)
            ?: Settings.DEFAULT.listenWholeMove,
        separateLetterAndNumber = this[separate(profileId)]
            ?: legacy(profileId, SEPARATE_LETTER_AND_NUMBER)
            ?: Settings.DEFAULT.separateLetterAndNumber
    )

    /**
     * Nasleđe: vrednost upisana **pre profila** pripada prvom profilu.
     *
     * Ostali profili kreću od podrazumevanog — njihova podešavanja nikad nisu ni
     * postojala, pa nemaju šta da naslede.
     */
    private fun <T> Preferences.legacy(profileId: Long, key: Preferences.Key<T>): T? =
        if (profileId == DEFAULT_PROFILE_ID) this[key] else null

    private fun theme(id: Long) = stringPreferencesKey("p${id}_theme")
    private fun speechRate(id: Long) = floatPreferencesKey("p${id}_speech_rate")
    private fun language(id: Long) = stringPreferencesKey("p${id}_language")
    private fun eyesFree(id: Long) = booleanPreferencesKey("p${id}_eyes_free")
    private fun phonetic(id: Long) = booleanPreferencesKey("p${id}_phonetic_alphabet")
    private fun wholeMove(id: Long) = booleanPreferencesKey("p${id}_listen_whole_move")
    private fun separate(id: Long) = booleanPreferencesKey("p${id}_separate_letter_and_number")

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
