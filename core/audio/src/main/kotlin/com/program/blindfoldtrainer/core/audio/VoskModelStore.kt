package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.util.Log
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.model.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Stanje jezičkog modela na uređaju. */
sealed interface ModelState {
    /** Model nije preuzet. Glasovni unos ne postoji dok se to ne promeni. */
    data object Absent : ModelState

    /** Preuzimanje u toku; [fraction] je `null` dok se ne zna ukupna veličina. */
    data class Downloading(val fraction: Float?) : ModelState

    /** Preuzeto, raspakuje se. */
    data object Unpacking : ModelState

    data object Ready : ModelState

    data class Failed(val reason: String) : ModelState
}

/**
 * Jezički model za Vosk — preuzimanje, raspakivanje i brisanje.
 *
 * Model je 39 MB za preuzimanje i oko 67 MB na disku, pa **ne ide u APK**.
 * Preuzima se na zahtev korisnika: kome glasovni unos ne treba, taj ga i ne
 * plaća. Isto tako sme i da ga obriše i vrati prostor.
 *
 * Nedovršeno preuzimanje se ne pamti kao model: folder se briše pri neuspehu, a
 * spremnost se proverava po fajlovima koje Vosk zaista traži, ne po postojanju
 * foldera.
 */
@Singleton
class VoskModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    @Volatile
    private var language: VoiceLanguage = VoiceLanguage.ENGLISH

    /** Folder sa modelom; put koji se prosleđuje Vosk-u kad je [ModelState.Ready]. */
    val directory: File get() = directoryFor(language)

    /**
     * Svaki jezik ima svoj folder, pa povratak na jezik koji je već preuzet ne
     * traži novo preuzimanje.
     */
    private fun directoryFor(language: VoiceLanguage) =
        File(File(context.filesDir, DIRECTORY), language.code)

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                if (settings.voiceLanguage == language) return@collect

                // Promena jezika prekida preuzimanje koje je u toku: ono što se
                // preuzima više nije ono što je traženo.
                downloadJob?.cancel()
                language = settings.voiceLanguage
                refreshState()
            }
        }
        refreshState()
    }

    private fun refreshState() {
        _state.value =
            if (ModelArchive.isComplete(directory)) ModelState.Ready else ModelState.Absent
    }

    /** Bezbedno je zvati više puta — drugo pozivanje dok traje preuzimanje ne radi ništa. */
    fun download() {
        if (_state.value is ModelState.Downloading || _state.value is ModelState.Unpacking) return
        if (_state.value is ModelState.Ready) return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _state.value = ModelState.Downloading(fraction = null)
                val archive = fetchArchive()

                _state.value = ModelState.Unpacking
                unpack(archive)

                _state.value = if (ModelArchive.isComplete(directory)) {
                    ModelState.Ready
                } else {
                    directory.deleteRecursively()
                    ModelState.Failed("Preuzeti model je nepotpun")
                }
            } catch (cancellation: CancellationException) {
                cleanUp()
                _state.value = ModelState.Absent
                throw cancellation
            } catch (error: Throwable) {
                Log.e(TAG, "Preuzimanje modela nije uspelo", error)
                cleanUp()
                _state.value = ModelState.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
    }

    /** Briše model sa uređaja i vraća oko 67 MB prostora. */
    fun delete() {
        downloadJob?.cancel()
        scope.launch {
            cleanUp()
            _state.value = ModelState.Absent
        }
    }

    private suspend fun fetchArchive(): File = withContext(Dispatchers.IO) {
        val archive = File(context.cacheDir, ARCHIVE_NAME)
        archive.delete()

        val connection = (URL(VoiceLanguages.urlFor(language)).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Server je vratio $code")

            val total = connection.contentLengthLong.takeIf { it > 0 }
            var written = 0L

            connection.inputStream.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        // Stanje se osvežava po komadu, ne po bajtu — traka ne
                        // treba da bude tačnija od onoga što se vidi.
                        _state.value = ModelState.Downloading(total?.let { written.toFloat() / it })
                    }
                }
            }
            archive
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun unpack(archive: File) = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
        archive.inputStream().use { ModelArchive.unpack(it, directory) }
        archive.delete()
    }

    private fun cleanUp() {
        File(context.cacheDir, ARCHIVE_NAME).delete()
        directory.deleteRecursively()
    }

    private companion object {
        const val TAG = "VoskModelStore"
        const val DIRECTORY = "vosk-model"
        const val ARCHIVE_NAME = "vosk-model.zip"
        const val TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
