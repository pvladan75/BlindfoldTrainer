package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.program.blindfoldtrainer.core.audio.SpokenToken.Clip
import com.program.blindfoldtrainer.core.audio.SpokenToken.Pause
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govor iz snimljenih isečaka, kroz `AudioTrack`.
 *
 * Zamena za TTS na uređaju, 18. oktobra 2026., po vlasnikovoj odluci: jedan
 * glas na svakom telefonu, bez mreže i bez ključa u aplikaciji. Isečci su u
 * `assets/speech/`, po [SpeechVocabulary]; ovde se samo lepe jedan za drugi i
 * puštaju.
 *
 * Što je bilo pogađanje kod TTS-a ovde je računica: dužina onoga što se pušta
 * se zna u bajtovima, pa [isSpeaking] pada tačno kad zvuk stane, a mreža ispod
 * (osigurač) ostaje samo za slučaj da `AudioTrack` ne javi kraj.
 *
 * Singleton je namerno: isečci se učitaju jednom (nekoliko megabajta), i red
 * govora je jedan za celu aplikaciju — dva zvučnika bi se presecala.
 */
@Singleton
class ClipSpeaker @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository
) : Speaker {

    private val library = ClipLibrary(context.assets)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isReady = false

    /**
     * Ono što je traženo pre nego što su se isečci učitali.
     *
     * **Skuplja se, ne prepisuje**: od nekoliko rečenica zatraženih pre
     * podizanja mora preživeti svaka, inače se najava koja se kaže jednom
     * izgubi — i to samo pri prvom ulasku posle pokretanja, što je najgora
     * vrsta greške za pronaći. Prekid prazni red, kao i inače.
     */
    private val pending = mutableListOf<List<SpokenToken>>()

    /** Najave koje čekaju da ona koja se čuje završi. */
    private val queue = ArrayDeque<ByteArray>()

    private var track: AudioTrack? = null

    /** Raste sa svakom novom najavom i svakim prekidom; zakasnelo javljanje nosi staru vrednost i ne prepozna se. */
    private var generation = 0

    private var completion: Job? = null

    @Volatile
    private var rate: Float = Settings.DEFAULT_SPEECH_RATE

    @Volatile
    private var language: Language = Language.ENGLISH

    /**
     * Poslednja **najava**, za dugme „ponovi" — u fonetskom obliku.
     *
     * Skuplja se kroz ceo dah: „knight from", „e4", „target", „g7" je jedna
     * najava iz četiri poziva.
     */
    private var lastSpoken: List<SpokenToken>? = null

    /** Dubina [aside] blokova; unutar njih se „ponovi" ne dira. */
    private var asideDepth = 0

    private val _isSpeaking = MutableStateFlow(false)

    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { updated ->
                language = updated.language
                setRate(updated.speechRate)
            }
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { library.loadAll() } }
            loaded.onFailure { Log.e(TAG, "Speech clips did not load", it) }
            isReady = true
            val waiting = pending.toList()
            pending.clear()
            waiting.forEach { enqueue(it) }
        }
    }

    override fun say(line: SpokenLine, interrupt: Boolean) = sayTokens(line.tokens, interrupt)

    override fun say(square: Square, interrupt: Boolean) = sayTokens(
        tokens = listOf(SpeechVocabulary.square(square)),
        interrupt = interrupt,
        forRepeat = SpeechVocabulary.squarePhonetic(square)
    )

    override fun say(move: Move, interrupt: Boolean) = sayTokens(
        tokens = SpeechVocabulary.move(move),
        interrupt = interrupt,
        forRepeat = SpeechVocabulary.movePhonetic(move)
    )

    // Ime figure pa polja — kao i pri čitanju pozicije.
    override fun say(piece: PieceType, move: Move, interrupt: Boolean) {
        val name = SpeechVocabulary.piece(piece)
        sayTokens(
            tokens = listOf(name) + SpeechVocabulary.move(move),
            interrupt = interrupt,
            forRepeat = listOf(name) + SpeechVocabulary.movePhonetic(move)
        )
    }

    override fun say(board: Board, interrupt: Boolean) = sayTokens(SpeechVocabulary.board(board), interrupt)

    override fun say(interrupt: Boolean, phrase: SpeechVoice.() -> SpokenLine) =
        sayTokens(voiceFor(language).phrase().tokens, interrupt)

    /** Ponavlja poslednju najavu; ako ničega nema, ćuti. */
    override fun repeat() {
        lastSpoken?.let { aside { sayTokens(it) } }
    }

    override fun aside(block: () -> Unit) {
        asideDepth++
        try {
            block()
        } finally {
            asideDepth--
        }
    }

    /** [forRepeat] je isti sadržaj u obliku u kom se **ponavlja**: polja idu fonetski. */
    private fun sayTokens(
        tokens: List<SpokenToken>,
        interrupt: Boolean = true,
        forRepeat: List<SpokenToken> = tokens
    ) {
        if (tokens.none { it is Clip }) return

        rememberForRepeat(forRepeat, interrupt)

        if (interrupt) {
            pending.clear()
            queue.clear()
            generation++
            releaseTrack()
        }

        if (!isReady) {
            pending += tokens
            _isSpeaking.value = true
            return
        }
        enqueue(tokens)
    }

    /**
     * Dopisuje uz tekuću najavu, ili počinje novu. Nova počinje kad se preseče
     * ono što je teklo, ili kad ničega u redu nije ni bilo.
     */
    private fun rememberForRepeat(tokens: List<SpokenToken>, interrupt: Boolean) {
        if (asideDepth > 0) return
        val queued = _isSpeaking.value || pending.isNotEmpty()
        lastSpoken = if (!interrupt && queued) lastSpoken.orEmpty() + tokens else tokens
    }

    private fun enqueue(tokens: List<SpokenToken>) {
        val pcm = render(tokens)
        if (pcm.isEmpty()) {
            if (track == null && queue.isEmpty()) _isSpeaking.value = false
            return
        }
        queue.addLast(pcm)
        _isSpeaking.value = true
        if (track == null) playNext()
    }

    /**
     * Isečci jedan za drugim, sa tišinom gde rečnik kaže: 50 ms ispred polja,
     * zadata pauza između figura. Isečak kog nema je **glasna** greška u logu i
     * rupa u zvuku, ne drugi glas — test rečnika postoji da se to ne desi.
     */
    private fun render(tokens: List<SpokenToken>): ByteArray {
        val out = ByteArrayOutputStream()
        tokens.forEachIndexed { index, token ->
            when (token) {
                is Pause -> out.write(silence(token.millis))
                is Clip -> {
                    val pcm = library.pcm(token.id)
                    if (pcm == null) {
                        Log.e(TAG, "No clip for ${token.id} (\"${token.text}\")")
                    } else {
                        if (index > 0 && token.pauseBeforeMillis > 0) out.write(silence(token.pauseBeforeMillis))
                        out.write(pcm)
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun silence(millis: Int): ByteArray {
        val frames = SpeechVocabulary.SAMPLE_RATE.toLong() * millis / 1000
        return ByteArray((frames * BYTES_PER_FRAME).toInt())
    }

    private fun playNext() {
        val pcm = queue.removeFirstOrNull()
        if (pcm == null) {
            _isSpeaking.value = false
            return
        }

        val frames = pcm.size / BYTES_PER_FRAME
        val built = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SpeechVocabulary.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        }
        val audio = built.getOrElse {
            Log.e(TAG, "AudioTrack could not be created; the announcement is dropped", it)
            playNext()
            return
        }

        val mine = ++generation
        track = audio
        applyRate(audio)

        // Kraj se javlja dvaput, a prvi važi: marker na poslednjem okviru, i
        // tajmer na izračunato trajanje — dužina zvuka je poznata do bajta, pa
        // tajmer nije pogađanje nego osigurač za motor koji marker proguta.
        audio.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) = finished(mine)
            override fun onPeriodicNotification(track: AudioTrack?) = Unit
        }, Handler(Looper.getMainLooper()))
        audio.notificationMarkerPosition = (frames - 1).coerceAtLeast(1)

        val millis = frames * 1000L / SpeechVocabulary.SAMPLE_RATE
        completion?.cancel()
        completion = scope.launch {
            delay((millis / rate).toLong() + COMPLETION_SLACK_MILLIS)
            finished(mine)
        }

        audio.play()
    }

    private fun finished(mine: Int) {
        if (mine != generation) return
        completion?.cancel()
        releaseTrack()
        playNext()
    }

    private fun releaseTrack() {
        completion?.cancel()
        track?.let { audio ->
            runCatching { audio.stop() }
            runCatching { audio.release() }
        }
        track = null
    }

    override fun stop() {
        pending.clear()
        queue.clear()
        generation++
        releaseTrack()
        _isSpeaking.value = false
    }

    /** Brzina menja tempo a ne visinu: `PlaybackParams` rasteže vreme. */
    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(Settings.MIN_SPEECH_RATE, Settings.MAX_SPEECH_RATE)
        track?.let(::applyRate)
    }

    private fun applyRate(audio: AudioTrack) {
        runCatching {
            audio.playbackParams = PlaybackParams().setSpeed(rate).setPitch(1f)
        }.onFailure { Log.w(TAG, "Speech rate $rate not applied", it) }
    }

    private companion object {
        const val TAG = "ClipSpeaker"
        const val BYTES_PER_FRAME = 2

        /** Koliko se posle izračunatog kraja još čeka na marker pre nego što se red pomeri sam. */
        const val COMPLETION_SLACK_MILLIS = 120L
    }
}

/**
 * Isečci iz `assets/speech/`, učitani u memoriju jednom.
 *
 * Svi odjednom, pri podizanju: nekoliko megabajta PCM-a, a svaki isečak treba
 * tačno u trenutku kad se govori — čitanje sa diska usred rečenice bi se čulo
 * kao zastoj.
 */
internal class ClipLibrary(private val assets: AssetManager) {

    private val clips = HashMap<String, ByteArray>()

    fun loadAll() {
        val names = assets.list(DIRECTORY).orEmpty().filter { it.endsWith(".wav") }
        for (name in names) {
            val clip = assets.open("$DIRECTORY/$name").use { WavPcm.read(it.readBytes()) }
            val format = clip.format
            require(
                format.sampleRate == SpeechVocabulary.SAMPLE_RATE &&
                    format.bitsPerSample == SpeechVocabulary.BITS_PER_SAMPLE &&
                    format.channels == SpeechVocabulary.CHANNELS
            ) { "$name: ${format.sampleRate} Hz / ${format.bitsPerSample} bit / ${format.channels} ch" }
            clips[name.removeSuffix(".wav")] = clip.pcm
        }
    }

    fun pcm(id: String): ByteArray? = clips[id]

    private companion object {
        const val DIRECTORY = "speech"
    }
}
