package com.program.blindfoldtrainer.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /**
     * Ono što je traženo pre nego što se motor podigao.
     *
     * **Skuplja se, ne prepisuje.** Ranije je stajala jedna lista, pa je od
     * nekoliko rečenica zatraženih pre podizanja preživela samo poslednja —
     * najava koja se kaže jednom bi se prosto izgubila, i to samo pri prvom
     * ulasku posle pokretanja aplikacije, što je najgora vrsta greške za
     * pronaći. Prekid prazni red, kao i inače.
     */
    private val pending = mutableListOf<String>()

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

    /**
     * Poslednja **najava**, za dugme „ponovi" — u fonetskom obliku.
     *
     * Skuplja se kroz ceo dah: „skakač sa", „e četiri", „cilj", „g sedam" je
     * jedna najava iz četiri poziva. Dotle se pamtio samo poslednji poziv, pa je
     * „ponovi" vraćao „g sedam" — jedini deo koji je čovek sigurno već čuo, jer
     * je bio poslednji.
     */
    @Volatile
    private var lastSpoken: List<String>? = null

    /** Dubina [aside] blokova; unutar njih se „ponovi" ne dira. */
    private var asideDepth = 0

    private val _isSpeaking = MutableStateFlow(false)

    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * Oznaka **poslednjeg** dela koji je stavljen u red.
     *
     * Po njemu se zna da je red ispražnjen: motor izgovara delove redom, pa kad
     * se javi da je gotov baš taj, ništa iza njega nije ostalo.
     *
     * Zašto poslednji a ne brojač: `QUEUE_FLUSH` pobaca ono što je čekalo, a
     * javljanja za pobačene delove stižu **posle** toga. Brojač bi na njima
     * skliznuo ispod nule i ostao pokvaren do kraja rada aplikacije. Ovako
     * zastarelo javljanje nosi tuđu oznaku i prosto se ne prepozna.
     */
    @Volatile
    private var lastQueuedId: String? = null

    private var utteranceCount = 0

    private var watchdog: Job? = null

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

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = markFinished(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                markFinished(utteranceId)

            @Deprecated("Traži ga stariji API; novi zove onError(String, Int).")
            override fun onError(utteranceId: String?) = markFinished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = markFinished(utteranceId)
        })

        isReady = true
        applyLanguage()
        setRate(settings.speechRate)

        if (pending.isNotEmpty()) {
            val parts = pending.toList()
            pending.clear()
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

    override fun say(square: Square, interrupt: Boolean) {
        val words = wordsForSpeech()
        sayParts(
            parts = listOf(square.spoken(words)),
            interrupt = interrupt,
            forRepeat = listOf(square.spokenPhonetic(words))
        )
    }

    override fun say(move: Move, interrupt: Boolean) {
        val words = wordsForSpeech()
        sayParts(
            parts = listOf(move.spoken(words)),
            interrupt = interrupt,
            forRepeat = listOf(move.spokenPhonetic(words))
        )
    }

    // Ime figure pa polja, u dva dela — kao i pri čitanju pozicije.
    override fun say(piece: PieceType, move: Move, interrupt: Boolean) {
        val words = wordsForSpeech()
        val name = words.pieces.getValue(piece)
        sayParts(
            parts = listOf(name, move.spoken(words)),
            interrupt = interrupt,
            forRepeat = listOf(name, move.spokenPhonetic(words))
        )
    }

    // Pozicija ide u delovima — vidi Board.spokenParts.
    override fun say(board: Board, interrupt: Boolean) =
        sayParts(board.spokenParts(wordsForSpeech()), interrupt)

    /** Ponavlja poslednju najavu; ako ničega nema, ćuti. */
    override fun repeat() {
        // Samo ponavljanje se ne pamti kao nova najava — ono i jeste ta najava.
        lastSpoken?.let { aside { sayParts(it) } }
    }

    private fun wordsForSpeech(): SpeechWords = SpeechLanguages.wordsFor(spokenLanguage())

    // Jezik se bira ovde, po istom pravilu kao za polja: ono što se zaista
    // govori, a ne ono što je izabrano ako glasa za to nema.
    override fun say(interrupt: Boolean, phrase: SpeechVoice.() -> String) =
        sayParts(listOf(voiceFor(spokenLanguage()).phrase()), interrupt)

    override fun say(text: String, interrupt: Boolean) = sayParts(listOf(text), interrupt)

    /**
     * [forRepeat] je isti sadržaj u obliku u kom se **ponavlja**: polja idu
     * fonetski. Gde se ne razlikuje — obične rečenice — to je isti tekst.
     */
    private fun sayParts(
        parts: List<String>,
        interrupt: Boolean = true,
        forRepeat: List<String> = parts
    ) {
        // Tačka iza cifre ovde otpada, pre svega ostalog: „4." bi se pročitalo
        // kao „četvrti". Vidi [withoutOrdinalPeriod].
        val spoken = parts.filter { it.isNotBlank() }.map(::withoutOrdinalPeriod)
        if (spoken.isEmpty()) return

        rememberForRepeat(forRepeat, interrupt)

        if (!isReady) {
            // Prvi potez ume da stigne pre nego što se motor podigne;
            // pamtimo ga umesto da ga nečujno progutamo.
            if (interrupt) pending.clear()
            pending += spoken
            return
        }
        speakParts(spoken, interrupt)
    }

    /**
     * Dopisuje uz tekuću najavu, ili počinje novu.
     *
     * Nova počinje kad se **preseče** ono što je teklo, ili kad ničega u redu
     * nije ni bilo. Sve što se dopisuje iza toga pripada istom dahu, pa se i
     * ponavlja zajedno — modul ne mora ništa da zna o tome ni da bilo šta
     * označava.
     *
     * Mora se izračunati **pre** nego što se zastavica govora digne, inače bi
     * svaka najava izgledala kao nastavak same sebe.
     */
    private fun rememberForRepeat(parts: List<String>, interrupt: Boolean) {
        // Ono što ima svoje dugme ne otima „ponovi" — vidi [aside].
        if (asideDepth > 0) return

        val clean = parts.filter { it.isNotBlank() }.map(::withoutOrdinalPeriod)
        if (clean.isEmpty()) return

        val queued = _isSpeaking.value || pending.isNotEmpty()
        lastSpoken = if (!interrupt && queued) (lastSpoken.orEmpty()) + clean else clean
    }

    override fun aside(block: () -> Unit) {
        asideDepth++
        try {
            block()
        } finally {
            asideDepth--
        }
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
        // Oznake moraju biti **jedinstvene kroz ceo rad**, ne po pozivu: dva
        // uzastopna izgovora sa istim „part-0" se ne bi razlikovala, pa bi
        // javljanje za prethodni ugasilo praćenje tekućeg.
        val ids = parts.map { "u-${utteranceCount++}" }

        // Oboje se postavlja **pre** prvog `speak`, i to je ceo smisao ovog
        // redosleda. Javljanja stižu sa tuđe niti i umeju da preteknu kod ispod
        // njih, a obe trke koje odatle slede su tihe i obe kvare mikrofon:
        //
        // - da se `lastQueuedId` pomerao u petlji, kraj **prvog** dela bi se
        //   primio kao kraj celog reda, pa bi se mikrofon otvorio usred govora i
        //   aplikacija bi čula samu sebe;
        // - da se `_isSpeaking` dizao posle petlje, kraj **poslednjeg** dela bi
        //   stigao pre toga, pa bi zastavica ostala podignuta zauvek i mikrofon
        //   se ne bi otvorio nikad.
        lastQueuedId = ids.last()
        _isSpeaking.value = true

        parts.forEachIndexed { index, part ->
            val mode = if (index == 0 && interrupt) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            tts.speak(part, mode, null, ids[index])
        }

        armWatchdog(parts.sumOf { it.length })
    }

    /** Red je prazan tek kad se javi **poslednji** stavljeni deo. */
    private fun markFinished(utteranceId: String?) {
        if (utteranceId == null || utteranceId != lastQueuedId) return
        watchdog?.cancel()
        _isSpeaking.value = false
    }

    /**
     * Mreža za slučaj da motor **ne javi** da je gotov.
     *
     * Nije mehanizam nego osigurač: neki TTS motori umeju da progutaju javljanje,
     * a modul koji čeka tišinu bi tada ostao zauvek sa zatvorenim mikrofonom i
     * bez ijednog načina da se to razreši iznutra.
     *
     * Rok je namerno **višestruko duži** od svake rečenice koja se stvarno
     * izgovara, da u ispravnom radu nikad ne opali; ovde se ne pogađa trajanje
     * govora nego se bira granica preko koje je nešto sigurno otkazalo.
     */
    private fun armWatchdog(characters: Int) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(WATCHDOG_BASE_MILLIS + WATCHDOG_PER_CHAR_MILLIS * characters)
            if (_isSpeaking.value) {
                Log.w(TAG, "TTS nije javio kraj izgovora; puštam mikrofon dalje")
                _isSpeaking.value = false
            }
        }
    }

    override fun stop() {
        pending.clear()
        watchdog?.cancel()
        lastQueuedId = null
        _isSpeaking.value = false
        if (isReady) tts.stop()
    }

    override fun setRate(rate: Float) {
        tts.setSpeechRate(rate.coerceIn(0.1f, 2.0f))
    }

    private companion object {
        const val TAG = "AndroidSpeaker"

        /** Osigurač: rok za kratku rečenicu, plus dodatak po znaku. */
        const val WATCHDOG_BASE_MILLIS = 3_000L
        const val WATCHDOG_PER_CHAR_MILLIS = 150L
    }
}
