package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.util.Log
import com.program.blindfoldtrainer.core.model.Language
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

/** Šta se upravo dešava sa paketima. Odnosi se na jedan jezik, onaj u obradi. */
sealed interface ModelState {
    /** Ništa se ne preuzima. */
    data object Idle : ModelState

    /** [fraction] je `null` dok se ne zna ukupna veličina. */
    data class Downloading(val language: Language, val fraction: Float?) : ModelState

    data class Unpacking(val language: Language) : ModelState

    data class Failed(val language: Language, val reason: String) : ModelState
}

/**
 * Jezički paketi za Vosk — preuzimanje, brisanje i evidencija šta je instalirano.
 *
 * Paket je oko 40 MB za preuzimanje i 60–70 MB na disku, pa **ne ide u APK**.
 * Preuzima se na zahtev korisnika: kome glasovni unos ne treba, taj ga i ne
 * plaća. Svaki jezik ima svoj folder, pa povratak na već preuzet jezik ne traži
 * novo preuzimanje.
 *
 * Nedovršeno preuzimanje se ne pamti kao paket: folder se briše pri neuspehu, a
 * spremnost se proverava po fajlovima koje Vosk zaista traži, ne po postojanju
 * foldera.
 */
@Singleton
class VoskModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ModelState>(ModelState.Idle)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _installed = MutableStateFlow(scanInstalled())

    /** Jezici čiji je paket na uređaju i upotrebljiv. */
    val installed: StateFlow<Set<Language>> = _installed.asStateFlow()

    private var downloadJob: Job? = null

    /** Folder sa paketom; put koji se prosleđuje Vosk-u. */
    fun directoryFor(language: Language): File =
        File(File(context.filesDir, DIRECTORY), language.code)

    fun isInstalled(language: Language): Boolean =
        ModelArchive.isComplete(directoryFor(language))

    /** Bezbedno je zvati više puta — dok jedno preuzimanje traje, drugo se ne počinje. */
    fun download(language: Language) {
        if (_state.value is ModelState.Downloading || _state.value is ModelState.Unpacking) return
        if (isInstalled(language)) return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _state.value = ModelState.Downloading(language, fraction = null)
                val archive = fetchArchive(language)

                _state.value = ModelState.Unpacking(language)
                unpack(language, archive)

                if (isInstalled(language)) {
                    _state.value = ModelState.Idle
                } else {
                    directoryFor(language).deleteRecursively()
                    _state.value = ModelState.Failed(language, "Preuzeti paket je nepotpun")
                }
            } catch (cancellation: CancellationException) {
                cleanUp(language)
                _state.value = ModelState.Idle
                throw cancellation
            } catch (error: Throwable) {
                Log.e(TAG, "Preuzimanje paketa za ${language.code} nije uspelo", error)
                cleanUp(language)
                _state.value = ModelState.Failed(
                    language,
                    error.message ?: error::class.java.simpleName
                )
            } finally {
                _installed.value = scanInstalled()
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
    }

    /** Briše paket sa uređaja i vraća 60–70 MB prostora. */
    fun delete(language: Language) {
        scope.launch {
            cleanUp(language)
            _installed.value = scanInstalled()
            if ((_state.value as? ModelState.Failed)?.language == language) {
                _state.value = ModelState.Idle
            }
        }
    }

    private fun scanInstalled(): Set<Language> =
        Language.entries.filterTo(mutableSetOf()) { isInstalled(it) }

    private suspend fun fetchArchive(language: Language): File = withContext(Dispatchers.IO) {
        val archive = File(context.cacheDir, ARCHIVE_NAME)
        archive.delete()

        val connection = (URL(VoiceLanguages.urlFor(language)).openConnection() as HttpURLConnection)
            .apply {
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
                        _state.value = ModelState.Downloading(
                            language,
                            total?.let { written.toFloat() / it }
                        )
                    }
                }
            }
            archive
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun unpack(language: Language, archive: File) = withContext(Dispatchers.IO) {
        val directory = directoryFor(language)
        directory.deleteRecursively()
        archive.inputStream().use { ModelArchive.unpack(it, directory) }
        archive.delete()
    }

    private fun cleanUp(language: Language) {
        File(context.cacheDir, ARCHIVE_NAME).delete()
        directoryFor(language).deleteRecursively()
    }

    private companion object {
        const val TAG = "VoskModelStore"
        const val DIRECTORY = "vosk-model"
        const val ARCHIVE_NAME = "vosk-model.zip"
        const val TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
