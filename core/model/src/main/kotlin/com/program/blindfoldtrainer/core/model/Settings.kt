package com.program.blindfoldtrainer.core.model

import kotlinx.coroutines.flow.Flow

/** Izgled aplikacije. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Jezik na kom aplikacija radi.
 *
 * **Jedan spisak za sve tri stvari** koje jezik dodiruje: reči kojima se čitaju
 * polja, rečenice koje se izgovaraju, i model kojim se sluša. Ranije su
 * postojala dva enuma — jedan za izgovor, jedan za prepoznavanje — jer je
 * izgovor imao i srpski, a Vosk nema nijedan južnoslovenski model.
 *
 * Kad je srpski izašao iz ponude, ta dva spiska su postala isti spisak, a dva
 * imena za istu stvar se pre ili kasnije raziđu.
 *
 * Dodavanje jezika je jedan unos ovde, jedan u `VoiceLanguages`, jedan u
 * `SpeechLanguages` i jedan `SpeechPhrases`. Dok rečenica nema, jezik se ne nudi.
 */
enum class Language(val code: String) {
    ENGLISH("en-us"),
    GERMAN("de"),
    RUSSIAN("ru"),
    FRENCH("fr"),
    SPANISH("es"),
    ITALIAN("it"),
    POLISH("pl"),
    CZECH("cs"),
    TURKISH("tr")
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
            "Brzina govora mora biti u $MIN_SPEECH_RATE..$MAX_SPEECH_RATE, dobijeno $speechRate"
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
