package com.program.blindfoldtrainer.core.audio

/**
 * Jedan deo onoga što se izgovara: isečak glasa, ili tišina između isečaka.
 *
 * Od 18. oktobra 2026. aplikacija ne govori kroz TTS na uređaju nego iz
 * **unapred snimljenih isečaka** jednog glasa (Azure, `en-US-AndrewNeural`),
 * istih kojima govori Mislisha. Rečenica je zato spisak isečaka, a ne niz
 * znakova: [text] je ono što bi stajalo na ekranu i što testovi čitaju, a [Clip.id]
 * je fajl u `assets/speech/` koji se čuje.
 */
sealed interface SpokenToken {
    val text: String

    /**
     * Isečak. [pauseBeforeMillis] je tišina ispred njega kad nije prvi — 50 ms
     * ispred polja, nigde drugde; tako je presuđeno uhom u Mislishi (D6) i
     * potvrđeno ovde 18. oktobra 2026.
     */
    data class Clip(val id: String, override val text: String, val pauseBeforeMillis: Int = 0) : SpokenToken

    /** Tišina. Stoji između figura pri čitanju pozicije, gde je TTS pravio pauzu na zarezu. */
    data class Pause(val millis: Int) : SpokenToken {
        override val text: String get() = ""
    }
}

/**
 * Rečenica kao niz isečaka.
 *
 * Isti spisak ima dva oblika: [text] za ekran i testove, [tokens] za zvučnik.
 * Pošto oba nastaju iz istog spiska, izgovoreno i napisano ne mogu da se
 * raziđu — to je pravilo preuzeto iz Mislishe, gde je rečenica koja se čuje
 * jednaka rečenici koja se vidi po konstrukciji.
 */
class SpokenLine(val tokens: List<SpokenToken>) {

    constructor(vararg tokens: SpokenToken) : this(tokens.toList())

    /** Reči spojene razmakom; tišina ne ostavlja trag. */
    val text: String
        get() = tokens.map { it.text }.filter { it.isNotEmpty() }.joinToString(" ")

    val isBlank: Boolean get() = tokens.none { it is SpokenToken.Clip }

    /** Isečci koje ova rečenica traži — da test proveri da svaki postoji. */
    val clipIds: List<String> get() = tokens.filterIsInstance<SpokenToken.Clip>().map { it.id }

    operator fun plus(other: SpokenLine): SpokenLine = SpokenLine(tokens + other.tokens)

    operator fun plus(token: SpokenToken): SpokenLine = SpokenLine(tokens + token)

    override fun toString(): String = "SpokenLine(\"$text\")"

    override fun equals(other: Any?): Boolean = other is SpokenLine && other.tokens == tokens

    override fun hashCode(): Int = tokens.hashCode()

    companion object {
        val EMPTY = SpokenLine(emptyList())
    }
}

/** Više rečenica u jedan dah, redom. */
fun List<SpokenLine>.joined(): SpokenLine = SpokenLine(flatMap { it.tokens })
