package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.util.Log
import com.program.blindfoldtrainer.core.chess.Square
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
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
    @ApplicationContext private val context: Context
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Preparing)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var unpackStarted = false
    private var onSquareRecognized: ((Square) -> Unit)? = null

    init {
        prepareModel()
    }

    private fun prepareModel() {
        if (unpackStarted) return
        unpackStarted = true

        if (!isModelBundled()) {
            // Bez modela glasovni unos ne postoji — UI to vidi kroz stanje i
            // sakrije mikrofon, umesto da nudi dugme koje ne radi ništa.
            _state.value = VoiceState.Unavailable("Jezički model nije preuzet")
            Log.w(TAG, "Vosk model '$MODEL_ASSET' nije nađen u assets")
            return
        }

        _state.value = VoiceState.Preparing
        StorageService.unpack(
            context,
            MODEL_ASSET,
            MODEL_TARGET_DIR,
            { loaded: Model ->
                model = loaded
                _state.value = VoiceState.Idle
                Log.d(TAG, "Vosk model spreman")
            },
            { error: IOException ->
                _state.value = VoiceState.Unavailable("Model nije učitan: ${error.message}")
                Log.e(TAG, "Raspakivanje Vosk modela nije uspelo", error)
            }
        )
    }

    private fun isModelBundled(): Boolean =
        runCatching { context.assets.list("")?.contains(MODEL_ASSET) == true }
            .getOrDefault(false)

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
        const val MODEL_ASSET = "model-en-us"
        const val MODEL_TARGET_DIR = "vosk-model"
        const val SAMPLE_RATE = 16000.0f
    }
}
