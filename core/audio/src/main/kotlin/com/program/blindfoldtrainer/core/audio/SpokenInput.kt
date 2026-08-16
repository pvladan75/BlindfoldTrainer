package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.VoiceLanguage

/** Šta je prepoznato iz jednog izgovora. */
sealed interface SpokenInput {
    /** Celo polje ("e four"). */
    data class Full(val square: Square) : SpokenInput

    /**
     * Dva polja u **jednom** izgovoru („b four g four") — ceo potez odjednom.
     *
     * Tako se potez i izgovara kad se ne misli na aplikaciju: u jednom dahu.
     * Bez ovoga se „b four g four" sastavi u `b4g4`, a to nije polje — pa se
     * ćuti. Sa uređaja je prijavljeno baš to.
     */
    data class Move(val from: Square, val to: Square) : SpokenInput

    /** Samo kolona ("e", ili „echo" po fonetskoj abecedi). */
    data class File(val file: Char) : SpokenInput

    /** Samo red ("four"). */
    data class Rank(val rank: Int) : SpokenInput

    /** Ništa od navedenog. */
    data object Unknown : SpokenInput
}

/**
 * Fonetska abeceda za kolone — ista ona koju koriste piloti i radio-veza.
 *
 * Naziv je namerno opisan, a ne „NATO": standard se zove i ICAO abeceda i
 * međunarodna radio-telefonska abeceda, a skraćenica nekome smeta bez ikakve
 * dobiti po značenje.
 *
 * Ne zavisi od jezika i zato stoji van tabele jezika: reč od dva sloga se ne
 * meša ni sa čim, pa pomaže svuda gde model brka slična slova. Reči su ipak
 * engleske, pa na modelu drugog jezika možda ne postoje u leksikonu.
 */
val PHONETIC_FILES: Map<String, Char> = mapOf(
    "alpha" to 'a',
    "bravo" to 'b',
    "charlie" to 'c',
    "delta" to 'd',
    "echo" to 'e',
    "foxtrot" to 'f',
    "golf" to 'g',
    "hotel" to 'h'
)

/**
 * Prevodi ono što je prepoznato u polje, kolonu ili red.
 *
 * Reči zavise od jezika ("four" ili „vier" ili „четыре"), pa se tabela prosleđuje
 * spolja. Latinična slova a–h i cifre 1–8 prolaze uvek — model ih ponekad vrati
 * takve kakve jesu, bez obzira na jezik.
 */
fun parseSpokenInput(
    text: String,
    words: VoiceWords = VoiceLanguages.specFor(VoiceLanguage.ENGLISH).words
): SpokenInput {
    val normalized = text.lowercase()
        .split(' ', '\t', '\n')
        .filter { it.isNotBlank() }
        .joinToString("") { token -> normalizeToken(token, words) }

    Square.fromAlgebraic(normalized)?.let { return SpokenInput.Full(it) }

    // Ceo potez u jednom dahu: „b four g four" dođe kao `b4g4`.
    if (normalized.length == 4) {
        val from = Square.fromAlgebraic(normalized.take(2))
        val to = Square.fromAlgebraic(normalized.drop(2))
        if (from != null && to != null) return SpokenInput.Move(from, to)
    }

    if (normalized.length == 1) {
        val single = normalized.first()
        if (single in 'a'..'h') return SpokenInput.File(single)
        if (single in '1'..'8') return SpokenInput.Rank(single - '0')
    }

    return SpokenInput.Unknown
}

/** Zadržano zbog mesta koja traže samo celo polje. */
fun parseSpokenSquare(
    text: String,
    words: VoiceWords = VoiceLanguages.specFor(VoiceLanguage.ENGLISH).words
): Square? = (parseSpokenInput(text, words) as? SpokenInput.Full)?.square

private fun normalizeToken(token: String, words: VoiceWords): String {
    val clean = token.trim().trimEnd('.', ',')

    words.files[clean]?.let { return it.toString() }
    words.ranks[clean]?.let { return it.toString() }
    PHONETIC_FILES[clean]?.let { return it.toString() }

    // "e4" ili "e" stižu takvi kakvi jesu; sve ostalo se propušta pa otpadne
    // pri čitanju polja.
    return clean
}
