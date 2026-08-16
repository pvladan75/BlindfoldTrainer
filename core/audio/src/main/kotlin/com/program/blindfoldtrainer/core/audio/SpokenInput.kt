package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.PieceType
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
    data class Move(
        val from: Square,
        val to: Square,
        /**
         * Figura koju je korisnik **imenovao**, ako jeste („rook c three c two").
         *
         * Ne odbacuje se kao suvišna iako polja već sve kažu: ime je tvrdnja o
         * tome šta korisnik misli da tamo stoji. Ako se ne slaže sa tablom,
         * slika u glavi je pogrešna — a odigrati potez bi tu zabludu potvrdilo.
         */
        val piece: PieceType? = null
    ) : SpokenInput

    /**
     * Figura i odredište („rook e two"), bez polazišta.
     *
     * Tako se potez i misli u glavi: ne „sa e četiri na e dva" nego „top na e
     * dva". Polazište traži onaj ko sluša, a ne onaj ko govori — pa ga i ovde
     * traži modul, iz legalnih poteza. Kad na isto polje mogu dve iste figure,
     * odgovor nije jedan i mora se pitati.
     */
    data class PieceMove(val piece: PieceType, val to: Square) : SpokenInput

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

/** Jedna prepoznata reč, pre nego što se sklopi u odgovor. */
private sealed interface Symbol {
    data class Whole(val square: Square) : Symbol
    data class File(val file: Char) : Symbol
    data class Rank(val rank: Int) : Symbol
    data class Piece(val type: PieceType) : Symbol
}

/**
 * Prevodi ono što je prepoznato u polje, potez, kolonu ili red.
 *
 * Ide **reč po reč**, a ne spajanjem svega u jedan niz. Spajanje je radilo dok se
 * očekivalo tačno jedno polje, ali je „b four g four" pretvaralo u `b4g4` — što
 * nije polje, pa se ćutalo. Sada se kolona i red sklope u polje čim se sretnu, a
 * ono što se skupi određuje odgovor:
 *
 * | rečeno | ispada |
 * |---|---|
 * | „e four" | polje |
 * | „e four e two" | potez, polazno pa odredišno |
 * | „rook e two" | figura i odredište |
 * | „rook e four e two" | potez, uz imenovanu figuru — modul je proverava |
 *
 * Reči zavise od jezika ("four" ili „vier" ili „четыре"), pa se tabela prosleđuje
 * spolja. Latinična slova a–h i cifre 1–8 prolaze uvek — model ih ponekad vrati
 * takve kakve jesu, bez obzira na jezik.
 */
fun parseSpokenInput(
    text: String,
    words: VoiceWords = VoiceLanguages.specFor(VoiceLanguage.ENGLISH).words
): SpokenInput {
    val tokens = text.lowercase()
        .split(' ', '\t', '\n')
        .map { it.trim().trimEnd('.', ',') }
        .filter { it.isNotBlank() && it != UNKNOWN_TOKEN }

    val symbols = tokens.map { symbolOf(it, words) ?: return SpokenInput.Unknown }

    val squares = mutableListOf<Square>()
    var piece: PieceType? = null
    var openFile: Char? = null

    for (symbol in symbols) {
        when (symbol) {
            is Symbol.Whole -> squares += symbol.square
            is Symbol.Piece -> if (piece == null) piece = symbol.type
            is Symbol.File -> openFile = symbol.file
            is Symbol.Rank -> {
                val file = openFile
                openFile = null
                // Red bez kolone ispred sebe se propušta. Tako „rook from e four
                // to e two" prolazi: „from" model ne zna, a „to" čuje kao „two",
                // pa taj zalutali red ovde otpadne.
                if (file != null) Square.of(file, symbol.rank)?.let { squares += it }
            }
        }
    }

    return when {
        squares.size >= 2 -> SpokenInput.Move(squares[0], squares[1], piece)
        squares.size == 1 && piece != null -> SpokenInput.PieceMove(piece, squares[0])
        squares.size == 1 -> SpokenInput.Full(squares[0])

        // Polovičan unos vredi samo kad je to sve što je rečeno — inače je ono
        // što je ostalo neuklopljeno znak da izgovor nije razumljen.
        symbols.size == 1 -> when (val only = symbols.first()) {
            is Symbol.File -> SpokenInput.File(only.file)
            is Symbol.Rank -> SpokenInput.Rank(only.rank)
            else -> SpokenInput.Unknown
        }

        else -> SpokenInput.Unknown
    }
}

/** Zadržano zbog mesta koja traže samo celo polje. */
fun parseSpokenSquare(
    text: String,
    words: VoiceWords = VoiceLanguages.specFor(VoiceLanguage.ENGLISH).words
): Square? = (parseSpokenInput(text, words) as? SpokenInput.Full)?.square

private fun symbolOf(token: String, words: VoiceWords): Symbol? {
    words.files[token]?.let { return Symbol.File(it) }
    words.ranks[token]?.let { return Symbol.Rank(it - '0') }
    words.pieces[token]?.let { return Symbol.Piece(it) }
    PHONETIC_FILES[token]?.let { return Symbol.File(it) }

    // Model ponekad vrati polje sklopljeno („e4"), a ponekad samo slovo.
    Square.fromAlgebraic(token)?.let { return Symbol.Whole(it) }
    if (token.length == 1) {
        val single = token.first()
        if (single in 'a'..'h') return Symbol.File(single)
        if (single in '1'..'8') return Symbol.Rank(single - '0')
    }

    return null
}

/** Ono što Vosk vrati za izgovor van gramatike — reč koju prosto preskačemo. */
private const val UNKNOWN_TOKEN = "[unk]"
