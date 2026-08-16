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
    private var pending: List<String>? = null

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
    private var lastSpoken: List<String>? = null

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

        pending?.let { parts ->
            pending = null
            speakParts(parts)
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

    // Pozicija ide u delovima, sa tišinom između — vidi Board.spokenParts.
    override fun say(board: Board) = sayParts(board.spokenParts(wordsForSpeech()))

    /** Ponavlja doslovno, sa istim pauzama; ako ništa nije rečeno, ćuti. */
    override fun repeat() {
        lastSpoken?.let { sayParts(it) }
    }

    private fun wordsForSpeech(): SpeechWords = SpeechLanguages.wordsFor(spokenLanguage())

    override fun say(text: String) = sayParts(listOf(text))

    private fun sayParts(parts: List<String>) {
        val spoken = parts.filter { it.isNotBlank() }
        if (spoken.isEmpty()) return

        lastSpoken = spoken
        if (!isReady) {
            // Prvi potez ume da stigne pre nego što se motor podigne;
            // pamtimo ga umesto da ga nečujno progutamo.
            pending = spoken
            return
        }
        speakParts(spoken)
    }

    /**
     * Izgovara delove sa tišinom između njih.
     *
     * Tišina ide kao zasebna izjava u redu, a ne kao interpunkcija: dužina pauze
     * tako ne zavisi od toga kako je koji TTS motor tumači.
     */
    private fun speakParts(parts: List<String>) {
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(part, mode, null, "part-$index")

            if (index != parts.lastIndex) {
                tts.playSilentUtterance(PAUSE_MILLIS, TextToSpeech.QUEUE_ADD, "pause-$index")
            }
        }
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

        /** Pauza između „bela dama na" i „e pet". */
        const val PAUSE_MILLIS = 200L
    }
}
