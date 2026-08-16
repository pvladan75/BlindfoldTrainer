package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.SpeechLanguage
import java.util.Locale

/**
 * Reči kojima aplikacija izgovara ono što se vidi na tabli.
 *
 * Za razliku od [VoiceWords], ovo je **samo za govor** — ne ulazi ni u kakav
 * rečnik i ne mora da postoji u leksikonu modela. Zato je i cena greške mala:
 * pogrešna reč se čuje i prijavi, dok se pogrešna reč u prepoznavanju vidi samo
 * kao tišina.
 */
data class SpeechWords(
    val files: Map<Char, String>,
    val ranks: Map<Char, String>,
    val pieces: Map<PieceType, String>,
    /** Boja u muškom rodu. */
    val white: String,
    val black: String,
    /** Boja u ženskom rodu; jednaka muškom kod jezika koji rod ne razlikuju. */
    val whiteFeminine: String = white,
    val blackFeminine: String = black,
    /** Figure ženskog roda u ovom jeziku — dama, top, pešak, kako gde. */
    val femininePieces: Set<PieceType> = emptySet(),
    /** Predlog ispred polja („na e5"); prazan kod jezika kojima ne treba. */
    val on: String
) {
    /** „bela dama na e5" — sa slaganjem roda. */
    fun describe(piece: Piece, squareText: String): String {
        val isFeminine = piece.type in femininePieces
        val color = when {
            piece.color == Color.WHITE && isFeminine -> whiteFeminine
            piece.color == Color.WHITE -> white
            isFeminine -> blackFeminine
            else -> black
        }
        val name = pieces.getValue(piece.type)
        val place = if (on.isBlank()) squareText else "$on $squareText"
        return "$color $name $place"
    }
}

/** Šta jedan jezik nosi za govor. */
data class SpeechSpec(
    val locale: Locale,
    val words: SpeechWords,
    /**
     * Da li je izgovor proveren na uređaju. Kod govora je greška bezopasna —
     * čuje se i prijavi — ali oznaka postoji da se zna šta je slušano a šta
     * upisano po rečniku.
     */
    val isVerified: Boolean = false
)

/**
 * Jezici kojima aplikacija govori.
 *
 * Dodavanje jezika je jedan unos: glas, šesnaest reči za polja, šest za figure i
 * dve za boje. Ništa drugo u aplikaciji ne zna za jezike.
 */
object SpeechLanguages {

    fun specFor(language: SpeechLanguage): SpeechSpec = SPECS.getValue(language)

    fun wordsFor(language: SpeechLanguage): SpeechWords = specFor(language).words

    fun localeFor(language: SpeechLanguage): Locale = specFor(language).locale

    private fun filesOf(vararg words: String): Map<Char, String> =
        words.mapIndexed { index, word -> ('a' + index) to word }.toMap()

    private fun ranksOf(vararg words: String): Map<Char, String> =
        words.mapIndexed { index, word -> ('1' + index) to word }.toMap()

    private fun piecesOf(
        king: String,
        queen: String,
        rook: String,
        bishop: String,
        knight: String,
        pawn: String
    ): Map<PieceType, String> = mapOf(
        PieceType.KING to king,
        PieceType.QUEEN to queen,
        PieceType.ROOK to rook,
        PieceType.BISHOP to bishop,
        PieceType.KNIGHT to knight,
        PieceType.PAWN to pawn
    )

    private val SPECS: Map<SpeechLanguage, SpeechSpec> = mapOf(
        SpeechLanguage.SERBIAN to SpeechSpec(
            locale = Locale.forLanguageTag("sr"),
            words = SpeechWords(
                files = filesOf("a", "be", "ce", "de", "e", "ef", "ge", "ha"),
                ranks = ranksOf("jedan", "dva", "tri", "četiri", "pet", "šest", "sedam", "osam"),
                pieces = piecesOf("kralj", "dama", "top", "lovac", "skakač", "pešak"),
                white = "beli",
                black = "crni",
                whiteFeminine = "bela",
                blackFeminine = "crna",
                femininePieces = setOf(PieceType.QUEEN),
                on = "na"
            )
        ),

        SpeechLanguage.ENGLISH to SpeechSpec(
            locale = Locale.US,
            words = SpeechWords(
                files = filesOf("a", "b", "c", "d", "e", "f", "g", "h"),
                ranks = ranksOf("one", "two", "three", "four", "five", "six", "seven", "eight"),
                pieces = piecesOf("king", "queen", "rook", "bishop", "knight", "pawn"),
                white = "white",
                black = "black",
                on = "on"
            ),
            isVerified = true
        ),

        SpeechLanguage.GERMAN to SpeechSpec(
            locale = Locale.GERMAN,
            words = SpeechWords(
                files = filesOf("a", "be", "ce", "de", "e", "ef", "ge", "ha"),
                ranks = ranksOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht"),
                pieces = piecesOf("König", "Dame", "Turm", "Läufer", "Springer", "Bauer"),
                white = "weißer",
                black = "schwarzer",
                whiteFeminine = "weiße",
                blackFeminine = "schwarze",
                femininePieces = setOf(PieceType.QUEEN),
                on = "auf"
            )
        ),

        SpeechLanguage.RUSSIAN to SpeechSpec(
            locale = Locale.forLanguageTag("ru"),
            words = SpeechWords(
                files = filesOf("а", "бэ", "цэ", "дэ", "е", "эф", "жэ", "аш"),
                ranks = ranksOf("один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь"),
                pieces = piecesOf("король", "ферзь", "ладья", "слон", "конь", "пешка"),
                white = "белый",
                black = "чёрный",
                whiteFeminine = "белая",
                blackFeminine = "чёрная",
                femininePieces = setOf(PieceType.ROOK, PieceType.PAWN),
                on = "на"
            )
        ),

        SpeechLanguage.FRENCH to SpeechSpec(
            locale = Locale.FRENCH,
            words = SpeechWords(
                files = filesOf("a", "bé", "cé", "dé", "e", "effe", "gé", "ache"),
                ranks = ranksOf("un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit"),
                pieces = piecesOf("roi", "dame", "tour", "fou", "cavalier", "pion"),
                white = "blanc",
                black = "noir",
                whiteFeminine = "blanche",
                blackFeminine = "noire",
                femininePieces = setOf(PieceType.QUEEN, PieceType.ROOK),
                on = "en"
            )
        ),

        SpeechLanguage.SPANISH to SpeechSpec(
            locale = Locale.forLanguageTag("es"),
            words = SpeechWords(
                files = filesOf("a", "be", "ce", "de", "e", "efe", "ge", "hache"),
                ranks = ranksOf("uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho"),
                pieces = piecesOf("rey", "dama", "torre", "alfil", "caballo", "peón"),
                white = "blanco",
                black = "negro",
                whiteFeminine = "blanca",
                blackFeminine = "negra",
                femininePieces = setOf(PieceType.QUEEN, PieceType.ROOK),
                on = "en"
            )
        ),

        SpeechLanguage.ITALIAN to SpeechSpec(
            locale = Locale.ITALIAN,
            words = SpeechWords(
                files = filesOf("a", "bi", "ci", "di", "e", "effe", "gi", "acca"),
                ranks = ranksOf("uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto"),
                pieces = piecesOf("re", "donna", "torre", "alfiere", "cavallo", "pedone"),
                white = "bianco",
                black = "nero",
                whiteFeminine = "bianca",
                blackFeminine = "nera",
                femininePieces = setOf(PieceType.QUEEN, PieceType.ROOK),
                on = "in"
            )
        ),

        SpeechLanguage.POLISH to SpeechSpec(
            locale = Locale.forLanguageTag("pl"),
            words = SpeechWords(
                files = filesOf("a", "be", "ce", "de", "e", "ef", "gie", "ha"),
                ranks = ranksOf("jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem"),
                pieces = piecesOf("król", "hetman", "wieża", "goniec", "skoczek", "pion"),
                white = "biały",
                black = "czarny",
                whiteFeminine = "biała",
                blackFeminine = "czarna",
                femininePieces = setOf(PieceType.ROOK),
                on = "na"
            )
        ),

        SpeechLanguage.CZECH to SpeechSpec(
            locale = Locale.forLanguageTag("cs"),
            words = SpeechWords(
                files = filesOf("á", "bé", "cé", "dé", "é", "ef", "gé", "há"),
                ranks = ranksOf("jedna", "dva", "tři", "čtyři", "pět", "šest", "sedm", "osm"),
                pieces = piecesOf("král", "dáma", "věž", "střelec", "jezdec", "pěšec"),
                white = "bílý",
                black = "černý",
                whiteFeminine = "bílá",
                blackFeminine = "černá",
                femininePieces = setOf(PieceType.QUEEN, PieceType.ROOK),
                on = "na"
            )
        ),

        SpeechLanguage.TURKISH to SpeechSpec(
            locale = Locale.forLanguageTag("tr"),
            words = SpeechWords(
                files = filesOf("a", "be", "ce", "de", "e", "fe", "ge", "he"),
                ranks = ranksOf("bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz"),
                pieces = piecesOf("şah", "vezir", "kale", "fil", "at", "piyon"),
                white = "beyaz",
                black = "siyah",
                // Turski ne razlikuje rod, pa predlog nije ni potreban.
                on = ""
            )
        )
    )
}
