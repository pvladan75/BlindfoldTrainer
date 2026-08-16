package com.program.blindfoldtrainer.core.model

import kotlinx.coroutines.flow.Flow

/** Izgled aplikacije. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Jezik glasovnog unosa.
 *
 * Vosk ima model po jeziku, pa jezik određuje i šta se preuzima i koje se reči
 * slušaju. **Srpskog nema** — Vosk nema nijedan južnoslovenski model, pa se ovaj
 * spisak završava tamo gde se završava njihova ponuda.
 */
enum class VoiceLanguage(val code: String) {
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

    /** Jezik na kom se izgovaraju polja. Menja i model koji se preuzima. */
    val voiceLanguage: VoiceLanguage = VoiceLanguage.ENGLISH,

    /**
     * Slova se izgovaraju NATO abecedom ("bravo" umesto "b"). Pomaže kad model
     * meša slična slova — na engleskom su „b" i „d" najčešća zamena.
     */
    val natoAlphabet: Boolean = false,

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
