package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.Language
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

    private val _availableLanguages = MutableStateFlow(emptySet<Language>())

    /**
     * Jezici za koje uređaj **zaista ima glas**. Prazan skup dok se TTS ne
     * podigne. Podešavanja odatle znaju šta sme da se ponudi — spisak jezika
     * koje uređaj ne ume da izgovori bio bi obećanje koje se ne održi.
     */
    val availableLanguages: StateFlow<Set<Language>> = _availableLanguages.asStateFlow()

    @Volatile
    private var settings: Settings = Settings.DEFAULT

    /** Poslednje izgovoreno, za dugme „ponovi". */
    @Volatile
    private var lastSpoken: List<String>? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { updated ->
                val languageChanged = updated.language != settings.language
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

        _availableLanguages.value = Language.entries.filterTo(mutableSetOf()) { language ->
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

        val wanted = settings.language
        val result = tts.setLanguage(SpeechLanguages.localeFor(wanted))

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Glas za ${wanted.code} nije dostupan, vraćam se na engleski")
            tts.setLanguage(SpeechLanguages.localeFor(Language.ENGLISH))
        }
    }

    /**
     * Jezik kojim se **zaista** govori.
     *
     * Dva razloga da to ne bude izabrani jezik, i oba vode na engleski:
     * uređaj nema glas za njega, ili rečenice još nisu prevedene. Odluka je na
     * jednom mestu zato što važi i za polja i za rečenice — inače bi se čula
     * mešavina, engleska rečenica sa nemačkim imenom figure u sredini.
     */
    private fun spokenLanguage(): Language {
        val wanted = settings.language
        val available = _availableLanguages.value
        val hasVoice = available.isEmpty() || wanted in available
        return if (hasVoice && wanted in TRANSLATED_LANGUAGES) wanted else Language.ENGLISH
    }

    override fun say(square: Square, interrupt: Boolean) =
        sayParts(listOf(square.spoken(wordsForSpeech())), interrupt)

    override fun say(move: Move, interrupt: Boolean) =
        sayParts(listOf(move.spoken(wordsForSpeech())), interrupt)

    // Ime figure pa polja, u dva dela — kao i pri čitanju pozicije.
    override fun say(piece: PieceType, move: Move, interrupt: Boolean) {
        val words = wordsForSpeech()
        sayParts(listOf(words.pieces.getValue(piece), move.spoken(words)), interrupt)
    }

    // Pozicija ide u delovima — vidi Board.spokenParts.
    override fun say(board: Board, interrupt: Boolean) =
        sayParts(board.spokenParts(wordsForSpeech()), interrupt)

    /** Ponavlja doslovno; ako ništa nije rečeno, ćuti. */
    override fun repeat() {
        lastSpoken?.let { sayParts(it) }
    }

    private fun wordsForSpeech(): SpeechWords = SpeechLanguages.wordsFor(spokenLanguage())

    // Jezik se bira ovde, po istom pravilu kao za polja: ono što se zaista
    // govori, a ne ono što je izabrano ako glasa za to nema.
    override fun say(interrupt: Boolean, phrase: SpeechVoice.() -> String) =
        sayParts(listOf(voiceFor(spokenLanguage()).phrase()), interrupt)

    override fun say(text: String, interrupt: Boolean) = sayParts(listOf(text), interrupt)

    private fun sayParts(parts: List<String>, interrupt: Boolean = true) {
        // Tačka iza cifre ovde otpada, pre svega ostalog: „4." bi se pročitalo
        // kao „četvrti". Vidi [withoutOrdinalPeriod].
        val spoken = parts.filter { it.isNotBlank() }.map(::withoutOrdinalPeriod)
        if (spoken.isEmpty()) return

        lastSpoken = spoken
        if (!isReady) {
            // Prvi potez ume da stigne pre nego što se motor podigne;
            // pamtimo ga umesto da ga nečujno progutamo.
            pending = spoken
            return
        }
        speakParts(spoken, interrupt)
    }

    /**
     * Izgovara delove, jedan za drugim.
     *
     * Između njih je stajala tišina od 50 ms, da bi se „bela dama na" i „e pet"
     * čuli kao dva koraka. Sa uređaja je stiglo da ne treba: motor i sam
     * zastane na zarezu i tački iz [Board.spokenParts], a pauza je samo
     * usporavala čitanje.
     *
     * Podela na delove ostaje — po njoj se ponavlja i po njoj se prekida.
     */
    private fun speakParts(parts: List<String>, interrupt: Boolean = true) {
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0 && interrupt) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            tts.speak(part, mode, null, "part-$index")
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
    }
}
