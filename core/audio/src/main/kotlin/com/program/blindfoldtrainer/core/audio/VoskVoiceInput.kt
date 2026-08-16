package com.program.blindfoldtrainer.core.audio

import android.util.Log
import com.program.blindfoldtrainer.core.chess.Square
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
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
    private val modelStore: VoskModelStore
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Preparing)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var onSquareRecognized: ((Square) -> Unit)? = null

    init {
        // Model se ne pakuje u APK nego se preuzima na zahtev, pa se glasovni
        // unos pali i gasi u toku rada aplikacije — otud praćenje stanja umesto
        // jednokratne provere pri pokretanju.
        scope.launch {
            modelStore.state.collect { modelState -> onModelState(modelState) }
        }
    }

    private fun onModelState(modelState: ModelState) {
        when (modelState) {
            is ModelState.Ready -> loadModel()

            ModelState.Absent -> {
                releaseModel()
                // UI ovo vidi kroz stanje i sakrije mikrofon, umesto da nudi
                // dugme koje ne radi ništa.
                _state.value = VoiceState.Unavailable("Jezički model nije preuzet")
            }

            is ModelState.Downloading, ModelState.Unpacking -> {
                _state.value = VoiceState.Preparing
            }

            is ModelState.Failed -> {
                releaseModel()
                _state.value = VoiceState.Unavailable(modelState.reason)
            }
        }
    }

    private fun loadModel() {
        if (model != null) return
        _state.value = VoiceState.Preparing

        try {
            model = Model(modelStore.directory.absolutePath)
            _state.value = VoiceState.Idle
            Log.d(TAG, "Vosk model spreman")
        } catch (error: Throwable) {
            Log.e(TAG, "Učitavanje Vosk modela nije uspelo", error)
            _state.value = VoiceState.Unavailable("Model nije učitan: ${error.message}")
        }
    }

    private fun releaseModel() {
        stopService()
        model?.close()
        model = null
    }

    override fun listenForSquare(onSquare: (Square) -> Unit) {
        val readyModel = model
        if (readyModel == null) {
            Log.w(TAG, "Traženo slušanje pre nego što je model spreman")
            return
        }
        if (_state.value == VoiceState.Listening) return

        onSquareRecognized = onSquare

        try {
            stopService()
            val recognizer = Recognizer(readyModel, SAMPLE_RATE, chessGrammar())
            speechService = SpeechService(recognizer, SAMPLE_RATE)

            if (speechService?.startListening(listener) == true) {
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
        if (_state.value == VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    private fun stopService() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
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

        val square = parseSpokenSquare(text)
        if (square == null) {
            Log.d(TAG, "Prepoznato \"$text\", ali to nije polje")
            return
        }

        val callback = onSquareRecognized
        stop()
        callback?.invoke(square)
    }

    /**
     * Rečnik ograničen na ono što korisnik uopšte može da kaže. Uz ovako uzak
     * rečnik Vosk gotovo ne greši, dok bi opšti model stalno nudio obične reči.
     */
    private fun chessGrammar(): String {
        val words = buildList {
            for (file in 'a'..'h') {
                for (rank in '1'..'8') add("$file$rank")
                add(file.toString())
            }
            for (rank in '1'..'8') add(rank.toString())
            addAll(listOf("one", "two", "three", "four", "five", "six", "seven", "eight"))
            add("[unk]")
        }
        return words.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    private companion object {
        const val TAG = "VoskVoiceInput"
        const val SAMPLE_RATE = 16000.0f
    }
}
