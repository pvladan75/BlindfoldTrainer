package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.Language
import java.util.Locale

/**
 * Reči kojima se na jednom jeziku izgovara polje: kolone a–h i redovi 1–8, i uz
 * njih imena figura ako ih jezik ima.
 *
 * Rečnik je uzak namerno — Vosk sluša samo ove reči i zato gotovo ne greši. Sve
 * što se ovde doda povećava i broj prilika da se pogreši.
 */
data class VoiceWords(
    /** Izgovorena kolona → slovo kolone. */
    val files: Map<String, Char>,
    /** Izgovoren red → cifra reda. */
    val ranks: Map<String, Char>,
    /**
     * Izgovoreno ime figure → vrsta („rook" → top).
     *
     * **Sme da bude prazno**, i za većinu jezika i jeste: imena figura su dodata
     * posle polja, a proverena su samo na engleskom. Jezik bez njih radi kao i
     * pre, poljima — prazan skup je uskraćena mogućnost, ne greška.
     */
    val pieces: Map<String, PieceType> = emptyMap()
) {
    init {
        require(files.values.toSet() == ('a'..'h').toSet()) { "Files must cover a–h" }
        require(ranks.values.toSet() == ('1'..'8').toSet()) { "Ranks must cover 1–8" }
        require(pieces.isEmpty() || pieces.values.toSet() == PieceType.entries.toSet()) {
            "If piece names exist, they must cover all six types"
        }
    }

    /** Reči za polja — kolone i redovi. Uvek ih je šesnaest. */
    val squareWords: List<String> get() = files.keys.toList() + ranks.keys.toList()

    /** Sve reči koje ulaze u Vosk gramatiku. */
    val allWords: List<String> get() = squareWords + pieces.keys
}

/** Jedan jezik: šta se preuzima, šta se sluša, i kojim se glasom čita. */
data class VoiceModelSpec(
    val archiveName: String,
    /** Za TTS glas na uređaju. Isti jezik služi i za slušanje i za čitanje. */
    val locale: Locale,
    /** Veličina preuzimanja, da korisnik zna na šta pristaje. */
    val downloadMegabytes: Int,
    val words: VoiceWords,
    /**
     * Da li je izgovor proveren na uređaju. Reči za jezike koje niko od nas ne
     * govori upisane su po pravopisu, a ne po sluhu — model ih možda uopšte
     * nema u svom rečniku. Ovo je oznaka da se to tek proverava, ne obećanje.
     */
    val isVerified: Boolean = false
)

/**
 * Jezici koje glasovni unos podržava.
 *
 * Dodavanje jezika je jedan unos u ovu tabelu: ime arhive sa
 * `alphacephei.com/vosk/models` i šesnaest reči. Ništa drugo u aplikaciji ne
 * zna za jezike.
 */
object VoiceLanguages {

    const val BASE_URL = "https://alphacephei.com/vosk/models/"

    fun specFor(language: Language): VoiceModelSpec = SPECS.getValue(language)

    fun urlFor(language: Language): String = BASE_URL + specFor(language).archiveName

    fun localeFor(language: Language): Locale = specFor(language).locale

    /**
     * [pieces] ide redom: kralj, dama, top, lovac, skakač, pešak. Izostavlja se
     * za jezik na kom imena figura još nisu proverena.
     */
    private fun wordsOf(
        files: List<String>,
        ranks: List<String>,
        pieces: List<String> = emptyList()
    ) = VoiceWords(
        files = files.mapIndexed { index, word -> word to ('a' + index) }.toMap(),
        ranks = ranks.mapIndexed { index, word -> word to ('1' + index) }.toMap(),
        pieces = pieces.zip(PIECE_ORDER).toMap()
    )

    private val PIECE_ORDER = listOf(
        PieceType.KING,
        PieceType.QUEEN,
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.KNIGHT,
        PieceType.PAWN
    )

    private val SPECS: Map<Language, VoiceModelSpec> = mapOf(
        Language.ENGLISH to VoiceModelSpec(
            archiveName = "vosk-model-small-en-us-0.15.zip",
            locale = Locale.US,
            downloadMegabytes = 39,
            words = wordsOf(
                files = listOf("a", "b", "c", "d", "e", "f", "g", "h"),
                ranks = listOf("one", "two", "three", "four", "five", "six", "seven", "eight"),
                pieces = listOf("king", "queen", "rook", "bishop", "knight", "pawn")
            ),
            isVerified = true
        )
    )
}
