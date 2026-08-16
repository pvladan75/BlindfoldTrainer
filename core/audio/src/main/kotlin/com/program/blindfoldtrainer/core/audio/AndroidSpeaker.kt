package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.SpeechLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS preko Android sistema.
 *
 * Singleton je namerno: `TextToSpeech` se inicijalizuje asinhrono i traje
 * stotinak milisekundi, pa pravljenje po ekranu daje osetnu tišinu pri svakom
 * ulasku u modul. Iz istog razloga nema `shutdown()` — stara aplikacija ga je
 * zvala iz `onCleared()` jednog modula i time gasila TTS ostalima.
 */
@Singleton
class AndroidSpeaker @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository
) : Speaker, TextToSpeech.OnInitListener {

    private var isReady = false
    /** Ono što je traženo pre nego što je motor bio spreman. */
    private var pending: String? = null

    private val tts = TextToSpeech(context, this)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _availableLanguages = MutableStateFlow(emptySet<SpeechLanguage>())

    /**
     * Jezici za koje uređaj **zaista ima glas**. Prazan skup dok se TTS ne
     * podigne. Podešavanja odatle znaju šta sme da se ponudi — spisak jezika
     * koje uređaj ne ume da izgovori bio bi obećanje koje se ne održi.
     */
    val availableLanguages: StateFlow<Set<SpeechLanguage>> = _availableLanguages.asStateFlow()

    @Volatile
    private var settings: Settings = Settings.DEFAULT

    /** Poslednje izgovoreno, za dugme „ponovi". */
    @Volatile
    private var lastSpoken: String? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { updated ->
                val languageChanged = updated.speechLanguage != settings.speechLanguage
                settings = updated

                // Brzinu i jezik bira korisnik. Ranije su ih moduli zakucavali
                // svaki za sebe, pa je izmena tražila diranje tri ViewModel-a.
                setRate(updated.speechRate)
                if (languageChanged) applyLanguage()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS nije uspeo da se pokrene (status=$status)")
            return
        }

        _availableLanguages.value = SpeechLanguage.entries.filterTo(mutableSetOf()) { language ->
            tts.isLanguageAvailable(SpeechLanguages.localeFor(language)) >= TextToSpeech.LANG_AVAILABLE
        }

        isReady = true
        applyLanguage()
        setRate(settings.speechRate)

        pending?.let { text ->
            pending = null
            say(text)
        }
    }

    /**
     * Postavlja glas za izabrani jezik, uz **povratak na engleski** kad ga uređaj
     * nema. Bolje razumljiv engleski nego ćutanje ili nasumičan glas.
     */
    private fun applyLanguage() {
        if (!isReady) return

        val wanted = settings.speechLanguage
        val result = tts.setLanguage(SpeechLanguages.localeFor(wanted))

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Glas za ${wanted.code} nije dostupan, vraćam se na engleski")
            tts.setLanguage(SpeechLanguages.localeFor(SpeechLanguage.ENGLISH))
        }
    }

    /** Jezik kojim se zaista govori — izabrani, ili engleski ako glasa nema. */
    private fun spokenLanguage(): SpeechLanguage {
        val wanted = settings.speechLanguage
        val available = _availableLanguages.value
        return if (available.isEmpty() || wanted in available) wanted else SpeechLanguage.ENGLISH
    }

    override fun say(square: Square) = say(square.spoken(wordsForSpeech()))

    override fun say(move: Move) = say(move.spoken(wordsForSpeech()))

    override fun say(board: Board) = say(board.spoken(wordsForSpeech()))

    /** Ponavlja doslovno; ako ništa nije rečeno, ćuti umesto da izmišlja. */
    override fun repeat() {
        lastSpoken?.let { say(it) }
    }

    private fun wordsForSpeech(): SpeechWords = SpeechLanguages.wordsFor(spokenLanguage())

    override fun say(text: String) {
        lastSpoken = text
        if (!isReady) {
            // Prvi potez ume da stigne pre nego što se motor podigne;
            // pamtimo ga umesto da ga nečujno progutamo.
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    override fun stop() {
        pending = null
        if (isReady) tts.stop()
    }

    override fun setRate(rate: Float) {
        tts.setSpeechRate(rate.coerceIn(0.1f, 2.0f))
    }

    private companion object {
        const val TAG = "AndroidSpeaker"
    }
}
