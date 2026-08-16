package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.model.VoiceLanguage

/**
 * Reči kojima se na jednom jeziku izgovara polje: kolone a–h i redovi 1–8.
 *
 * Rečnik je uzak namerno — Vosk sluša samo ovih šesnaest reči i zato gotovo ne
 * greši. Sve što se ovde doda povećava i broj prilika da se pogreši.
 */
data class VoiceWords(
    /** Izgovorena kolona → slovo kolone. */
    val files: Map<String, Char>,
    /** Izgovoren red → cifra reda. */
    val ranks: Map<String, Char>
) {
    init {
        require(files.values.toSet() == ('a'..'h').toSet()) { "Kolone moraju pokriti a–h" }
        require(ranks.values.toSet() == ('1'..'8').toSet()) { "Redovi moraju pokriti 1–8" }
    }

    /** Sve reči koje ulaze u Vosk gramatiku. */
    val allWords: List<String> get() = files.keys.toList() + ranks.keys.toList()
}

/** Model za jedan jezik: šta se preuzima i šta se sluša. */
data class VoiceModelSpec(
    val archiveName: String,
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

    fun specFor(language: VoiceLanguage): VoiceModelSpec = SPECS.getValue(language)

    fun urlFor(language: VoiceLanguage): String = BASE_URL + specFor(language).archiveName

    private fun wordsOf(files: List<String>, ranks: List<String>) = VoiceWords(
        files = files.mapIndexed { index, word -> word to ('a' + index) }.toMap(),
        ranks = ranks.mapIndexed { index, word -> word to ('1' + index) }.toMap()
    )

    private val SPECS: Map<VoiceLanguage, VoiceModelSpec> = mapOf(
        VoiceLanguage.ENGLISH to VoiceModelSpec(
            archiveName = "vosk-model-small-en-us-0.15.zip",
            downloadMegabytes = 39,
            words = wordsOf(
                files = listOf("a", "b", "c", "d", "e", "f", "g", "h"),
                ranks = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")
            ),
            isVerified = true
        ),

        VoiceLanguage.GERMAN to VoiceModelSpec(
            archiveName = "vosk-model-small-de-0.15.zip",
            downloadMegabytes = 44,
            words = wordsOf(
                files = listOf("a", "be", "ce", "de", "e", "ef", "ge", "ha"),
                ranks = listOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht")
            )
        ),

        VoiceLanguage.RUSSIAN to VoiceModelSpec(
            archiveName = "vosk-model-small-ru-0.22.zip",
            downloadMegabytes = 44,
            words = wordsOf(
                files = listOf("а", "бэ", "цэ", "дэ", "е", "эф", "жэ", "аш"),
                ranks = listOf("один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь")
            )
        ),

        VoiceLanguage.FRENCH to VoiceModelSpec(
            archiveName = "vosk-model-small-fr-0.22.zip",
            downloadMegabytes = 40,
            words = wordsOf(
                files = listOf("a", "bé", "cé", "dé", "e", "effe", "gé", "ache"),
                ranks = listOf("un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit")
            )
        ),

        VoiceLanguage.SPANISH to VoiceModelSpec(
            archiveName = "vosk-model-small-es-0.42.zip",
            downloadMegabytes = 38,
            words = wordsOf(
                files = listOf("a", "be", "ce", "de", "e", "efe", "ge", "hache"),
                ranks = listOf("uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho")
            )
        ),

        VoiceLanguage.ITALIAN to VoiceModelSpec(
            archiveName = "vosk-model-small-it-0.22.zip",
            downloadMegabytes = 47,
            words = wordsOf(
                files = listOf("a", "bi", "ci", "di", "e", "effe", "gi", "acca"),
                ranks = listOf("uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto")
            )
        ),

        VoiceLanguage.POLISH to VoiceModelSpec(
            archiveName = "vosk-model-small-pl-0.22.zip",
            downloadMegabytes = 51,
            words = wordsOf(
                files = listOf("a", "be", "ce", "de", "e", "ef", "gie", "ha"),
                ranks = listOf("jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem")
            )
        ),

        VoiceLanguage.CZECH to VoiceModelSpec(
            archiveName = "vosk-model-small-cs-0.4-rhasspy.zip",
            downloadMegabytes = 44,
            words = wordsOf(
                files = listOf("á", "bé", "cé", "dé", "é", "ef", "gé", "há"),
                ranks = listOf("jedna", "dva", "tři", "čtyři", "pět", "šest", "sedm", "osm")
            )
        ),

        VoiceLanguage.TURKISH to VoiceModelSpec(
            archiveName = "vosk-model-small-tr-0.3.zip",
            downloadMegabytes = 35,
            words = wordsOf(
                files = listOf("a", "be", "ce", "de", "e", "fe", "ge", "he"),
                ranks = listOf("bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz")
            )
        )
    )
}
