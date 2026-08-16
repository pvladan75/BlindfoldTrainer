package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Square

/** Šta je prepoznato iz jednog izgovora. */
sealed interface SpokenInput {
    /** Celo polje ("e four"). */
    data class Full(val square: Square) : SpokenInput

    /** Samo kolona ("e", ili „echo" po NATO abecedi). */
    data class File(val file: Char) : SpokenInput

    /** Samo red ("four"). */
    data class Rank(val rank: Int) : SpokenInput

    /** Nešto što nije ni jedno ni drugo. */
    data object Unknown : SpokenInput
}

/**
 * NATO abeceda za kolone.
 *
 * Postoji zato što engleski model lako meša slična slova — „b" i „d" su
 * najčešća zamena. Reč od dva sloga se ne meša ni sa čim, ali proširuje rečnik,
 * pa se uključuje samo kad korisnik to izabere.
 */
val NATO_FILES: Map<String, Char> = mapOf(
    "alpha" to 'a',
    "bravo" to 'b',
    "charlie" to 'c',
    "delta" to 'd',
    "echo" to 'e',
    "foxtrot" to 'f',
    "golf" to 'g',
    "hotel" to 'h'
)

private val NUMBER_WORDS: Map<String, Char> = mapOf(
    "one" to '1',
    "two" to '2',
    "three" to '3',
    "four" to '4',
    "five" to '5',
    "six" to '6',
    "seven" to '7',
    "eight" to '8'
)

/**
 * Prevodi ono što je prepoznato u polje, kolonu ili red.
 *
 * Vosk brojeve vraća rečima ("e four"), razmaci padaju kako padnu, a po NATO
 * abecedi kolona stiže kao cela reč — sve se svodi na isti oblik pre čitanja.
 */
fun parseSpokenInput(text: String): SpokenInput {
    val normalized = text.lowercase()
        .split(' ', '\t', '\n')
        .filter { it.isNotBlank() }
        .joinToString("") { token -> normalizeToken(token) }

    Square.fromAlgebraic(normalized)?.let { return SpokenInput.Full(it) }

    if (normalized.length == 1) {
        val single = normalized.first()
        if (single in 'a'..'h') return SpokenInput.File(single)
        if (single in '1'..'8') return SpokenInput.Rank(single - '0')
    }

    return SpokenInput.Unknown
}

/** Zadržano zbog mesta koja traže samo celo polje. */
fun parseSpokenSquare(text: String): Square? =
    (parseSpokenInput(text) as? SpokenInput.Full)?.square

private fun normalizeToken(token: String): String {
    val clean = token.trim().trimEnd('.', ',')
    NATO_FILES[clean]?.let { return it.toString() }
    NUMBER_WORDS[clean]?.let { return it.toString() }

    // "e4" ili "e" stižu takvi kakvi jesu; sve ostalo se propušta pa otpadne
    // pri čitanju polja.
    return clean
}
