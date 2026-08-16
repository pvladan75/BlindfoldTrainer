package com.program.blindfoldtrainer.core.audio

import android.util.Log
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline prepoznavanje polja preko Vosk-a.
 *
 * Singleton, i model se raspakuje **najviše jednom po pokretanju aplikacije**.
 * Stara aplikacija je pri svakom pravljenju ViewModel-a brisala pa ponovo
 * raspakivala ceo model (desetine megabajta), pa je svaki ulazak u modul
 * zastajao — i to na glavnoj niti korisnikovog utiska.
 */
@Singleton
class VoskVoiceInput @Inject constructor(
    private val modelStore: VoskModelStore,
    settingsRepository: SettingsRepository
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Preparing)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var onSquareRecognized: ((Square) -> Boolean)? = null

    /**
     * Brava oko predaje polja.
     *
     * Isti izgovor ume da stigne dvaput — kroz `onResult`, pa još jednom kroz
     * `onFinalResult` pri gašenju, a gašenje se pokreće baš iz predaje. Bez
     * brave bi se polje predalo dva puta, a naslepo je to odigran potez koji
     * niko nije rekao.
     */
    private val isDelivering = AtomicBoolean(false)

    /** Kolona koja čeka svoj red, kad se polje izgovara u dva dela. */
    @Volatile
    private var pendingFile: Char? = null

    @Volatile
    private var settings: Settings = Settings.DEFAULT

    init {
        // Paket se ne pakuje u APK nego se preuzima na zahtev, a jezik se bira u
        // Podešavanjima — glasovni unos se zato pali i gasi u toku rada, pa se
        // prati i izbor jezika i šta je instalirano i šta se upravo preuzima.
        scope.launch {
            combine(
                settingsRepository.settings,
                modelStore.installed,
                modelStore.state
            ) { settings, installed, modelState -> Triple(settings, installed, modelState) }
                .collect { (updated, installed, modelState) ->
                    val languageChanged = updated.voiceLanguage != settings.voiceLanguage
                    settings = updated

                    // Paket je vezan za jezik: kad se jezik promeni, stari se pušta.
                    if (languageChanged) releaseModel()

                    applyState(installed, modelState)
                }
        }
    }

    private fun applyState(installed: Set<VoiceLanguage>, modelState: ModelState) {
        val language = settings.voiceLanguage
        val isBusyWithThisLanguage = when (modelState) {
            is ModelState.Downloading -> modelState.language == language
            is ModelState.Unpacking -> modelState.language == language
            else -> false
        }

        when {
            isBusyWithThisLanguage -> _state.value = VoiceState.Preparing

            language in installed -> loadModel(language)

            else -> {
                releaseModel()
                val failure = (modelState as? ModelState.Failed)?.takeIf { it.language == language }
                _state.value = VoiceState.Unavailable(
                    failure?.reason ?: "Paket za izabrani jezik nije preuzet"
                )
            }
        }
    }

    private fun loadModel(language: VoiceLanguage) {
        if (model != null) return
        _state.value = VoiceState.Preparing

        try {
            model = Model(modelStore.directoryFor(language).absolutePath)
            _state.value = VoiceState.Idle
            Log.d(TAG, "Vosk paket za ${language.code} spreman")
        } catch (error: Throwable) {
            Log.e(TAG, "Učitavanje paketa za ${language.code} nije uspelo", error)
            _state.value = VoiceState.Unavailable("Paket nije učitan: ${error.message}")
        }
    }

    private fun releaseModel() {
        stopService()
        model?.close()
        model = null
    }

    override fun listenForSquares(onSquare: (Square) -> Boolean) {
        val readyModel = model
        if (readyModel == null) {
            Log.w(TAG, "Traženo slušanje pre nego što je model spreman")
            return
        }
        if (_state.value == VoiceState.Listening) return

        onSquareRecognized = onSquare
        pendingFile = null

        try {
            stopService()
            val recognizer = Recognizer(readyModel, SAMPLE_RATE, chessGrammar())
            speechService = SpeechService(recognizer, SAMPLE_RATE)

            // Sa vremenskim ograničenjem, a ne bez njega: ako ono što je rečeno
            // nije polje, Vosk bi inače slušao dok ga neko ne prekine — a
            // korisnik je javio da mu je mikrofon ostao upaljen bez izlaza.
            if (speechService?.startListening(listener, LISTEN_TIMEOUT_MILLIS) == true) {
                _state.value = VoiceState.Listening
            } else {
                Log.e(TAG, "startListening je vratio false")
                _state.value = VoiceState.Idle
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pokretanje slušanja nije uspelo", e)
            _state.value = VoiceState.Idle
        }
    }

    override fun stop() {
        stopService()
        onSquareRecognized = null
        pendingFile = null
        if (_state.value == VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    /**
     * Gašenje prepoznavanja ne sme da obori stanje: ako Vosk baci pri
     * zatvaranju, dugme bi ostalo crveno zauvek jer se do reda ispod ne bi ni
     * stiglo.
     */
    private fun stopService() {
        val service = speechService ?: return
        speechService = null

        runCatching {
            service.stop()
            service.shutdown()
        }.onFailure { Log.w(TAG, "Zatvaranje prepoznavanja nije prošlo čisto", it) }
    }

    private val listener = object : RecognitionListener {
        override fun onResult(hypothesis: String?) = handle(hypothesis)
        override fun onFinalResult(hypothesis: String?) = handle(hypothesis)

        override fun onPartialResult(hypothesis: String?) {
            // Slušamo samo jedno polje, delimični rezultat nam ne treba.
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Greška u prepoznavanju", exception)
            stop()
        }

        override fun onTimeout() {
            stop()
        }
    }

    private fun handle(hypothesis: String?) {
        val text = hypothesis
            ?.let { runCatching { JSONObject(it).optString("text", "") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: return

        when (val spoken = parseSpokenInput(text, currentWords())) {
            is SpokenInput.Full -> deliver(spoken.square)

            is SpokenInput.File -> if (settings.separateLetterAndNumber) {
                // Slušanje se ne prekida — čeka se broj koji ide uz ovu kolonu.
                pendingFile = spoken.file
            }

            is SpokenInput.Rank -> {
                val file = pendingFile
                if (settings.separateLetterAndNumber && file != null) {
                    pendingFile = null
                    Square.of(file, spoken.rank)?.let { deliver(it) }
                }
            }

            SpokenInput.Unknown -> Log.d(TAG, "Prepoznato \"$text\", ali to nije polje")
        }
    }

    private fun deliver(square: Square) {
        val callback = onSquareRecognized ?: return
        if (!isDelivering.compareAndSet(false, true)) return

        try {
            pendingFile = null
            // Slušanje ostaje upaljeno ako se traži još jedno polje — vidi
            // VoiceInput.listenForSquares.
            if (!callback(square)) stop()
        } finally {
            isDelivering.set(false)
        }
    }

    /**
     * Rečnik ograničen na ono što korisnik uopšte može da kaže. Uz ovako uzak
     * rečnik Vosk gotovo ne greši, dok bi opšti model stalno nudio obične reči.
     */
    private fun chessGrammar(): String {
        val words = buildList {
            addAll(currentWords().allWords)

            // Fonetske reči ulaze samo kad su izabrane, i samo uz engleski model:
            // engleske su, pa ih leksikon drugog jezika nema. Uz to širi rečnik
            // znači i više prilika da se pogreši.
            if (settings.usesPhoneticAlphabet) addAll(PHONETIC_FILES.keys)

            add("[unk]")
        }
        return words.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    private fun currentWords(): VoiceWords =
        VoiceLanguages.specFor(settings.voiceLanguage).words

    private companion object {
        const val TAG = "VoskVoiceInput"
        const val SAMPLE_RATE = 16000.0f

        /** Koliko se najduže sluša pre nego što se odustane samo od sebe. */
        const val LISTEN_TIMEOUT_MILLIS = 10_000
    }
}
