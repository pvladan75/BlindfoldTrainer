package com.program.blindfoldtrainer.core.model

import kotlinx.coroutines.flow.Flow

/** Izgled aplikacije. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Jezik na kom aplikacija radi.
 *
 * **Jedan spisak za sve tri stvari** koje jezik dodiruje: reči kojima se čitaju
 * polja, rečenice koje se izgovaraju, i model kojim se sluša.
 *
 * Od 18. oktobra 2026. aplikacija je **samo na engleskom** — ekran, govor i
 * slušanje. Osam jezika koji su ovde stajali imali su reči za polja upisane po
 * pravopisu a ne po sluhu, nijedan nije imao rečenice, i nijedan nije proveren
 * na uređaju; obrisani su umesto da se nude kao pola-jezik. Enum ostaje zato što
 * ga podešavanja i paket za slušanje nose kao ključ.
 *
 * Dodavanje jezika je jedan unos ovde, jedan u `VoiceLanguages`, rečenice u
 * `SpeechPhrases` i snimljeni isečci u `SpeechVocabulary`. Dok isečaka nema,
 * jezik se ne nudi.
 */
enum class Language(val code: String) {
    ENGLISH("en-us")
}

/**
 * Korisnikova podešavanja.
 *
 * Ovde stoji **samo ono što zavisi od korisnika, a ne od toga šta je objektivno
 * bolje**. Glasovne opcije su takve: koja je bolja zavisi od izgovora i od toga
 * koliko je kome udobno da izgovori celo polje odjednom, a to aplikacija ne može
 * da zna. Podrazumevane vrednosti su zatečeno ponašanje — ko ništa ne dira,
 * ništa mu se i ne menja.
 */
data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,

    /** Brzina izgovaranja poteza. 0.1 sporo, 2.0 brzo, 1.0 normalno. */
    val speechRate: Float = DEFAULT_SPEECH_RATE,

    /**
     * Jezik na kom aplikacija govori **i** sluša.
     *
     * Dugo su to bila dva odvojena podešavanja, jer zavise od različitih stvari
     * — glas na uređaju naspram preuzetog paketa. Spojena su zato što se od
     * korisnika tražilo previše: ko vežba zatvorenih očiju i sklapa tablu u
     * glavi ne sme uz to da pamti da sluša jedan jezik a govori drugi. Sam
     * autor je nekoliko puta ostao u nedoumici šta je gde podesio.
     *
     * Danas je jezik jedan i korisnik ga ne bira; polje ostaje da se podešavanja
     * i paket za slušanje ne bi prepravljali kad se drugi jezik jednom vrati.
     */
    val language: Language = Language.ENGLISH,

    /**
     * Umesto table i dugmadi — velike zone koje se pogađaju bez gledanja.
     *
     * Za vežbanje sklopljenih očiju: pozicija se pročita, potezi se izgovaraju,
     * a ekran služi samo kao površina koja se dodiruje.
     */
    val eyesFree: Boolean = false,

    /**
     * Kolone se izgovaraju rečima ("bravo" umesto "b"), po fonetskoj abecedi.
     * Pomaže kad model brka slična slova — na engleskom su „b" i „d" najčešća
     * zamena.
     */
    val phoneticAlphabet: Boolean = false,

    /**
     * U Završnici jedan pritisak sluša ceo potez: pošto se prepozna polazno
     * polje, slušanje se samo nastavlja za odredišno.
     */
    val listenWholeMove: Boolean = false,

    /**
     * Slovo i broj smeju da stignu odvojeno ("e", pa „four"), umesto da polje
     * mora u jednom dahu.
     */
    val separateLetterAndNumber: Boolean = false
) {
    init {
        require(speechRate in MIN_SPEECH_RATE..MAX_SPEECH_RATE) {
            "Speech rate must be in $MIN_SPEECH_RATE..$MAX_SPEECH_RATE, got $speechRate"
        }
    }

    /**
     * Fonetske reči („alpha", „bravo") su engleske, a Vosk prima samo reči koje
     * postoje u leksikonu modela — pa uz model drugog jezika ne bi bile
     * prepoznate. Zato ovo podešavanje postoji samo uz engleski.
     */
    val isPhoneticAlphabetAvailable: Boolean
        get() = language == Language.ENGLISH

    /**
     * Da li se fonetske reči zaista slušaju. Odvojeno od [phoneticAlphabet] da
     * promena jezika ne bi nečujno gasila korisnikov izbor — izbor ostaje
     * upamćen i vraća se sam kad se vrati engleski.
     */
    val usesPhoneticAlphabet: Boolean
        get() = phoneticAlphabet && isPhoneticAlphabetAvailable

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 1.5f
        const val DEFAULT_SPEECH_RATE = 0.85f

        val DEFAULT = Settings()
    }
}

/**
 * Čitanje i upis podešavanja.
 *
 * Interfejs stoji u čistom Kotlinu da bi moduli koji podešavanja koriste — glas,
 * govor, tema — mogli da se testiraju bez DataStore-a i bez Androida.
 */
interface SettingsRepository {

    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
