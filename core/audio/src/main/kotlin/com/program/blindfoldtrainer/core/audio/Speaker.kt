package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square

/**
 * Izgovaranje teksta. Iza interfejsa je da bi se u testovima mogao zameniti
 * lažnjakom, i da moduli ne zavise od `android.speech.tts` direktno.
 */
interface Speaker {

    /**
     * Izgovara [text].
     *
     * Uz `interrupt = false` **čeka svoj red** umesto da preseče ono što se
     * upravo govori. Motor ume da odgovori pre nego što se izgovori tvoj potez,
     * pa bi bez toga potvrda poteza nestala na pola reči.
     */
    fun say(text: String, interrupt: Boolean = true)

    /**
     * Izgovara polje na jeziku koji je izabran za govor.
     *
     * Formatiranje je ovde, a ne kod pozivaoca, jer zavisi od jezika — a moduli
     * za jezik ne znaju niti treba da znaju.
     */
    fun say(square: Square, interrupt: Boolean = true)

    fun say(move: Move, interrupt: Boolean = true)

    /** Čita celu poziciju: „beli kralj na e2, bela dama na e5…". */
    fun say(board: Board, interrupt: Boolean = true)

    /**
     * Ponavlja poslednje izgovoreno, doslovno.
     *
     * Stoji ovde jer mu treba samo ono što je rečeno — pa radi u **svakom**
     * modulu, a nijedan ne mora ništa da zna o tome.
     */
    fun repeat()

    fun stop()

    /** 0.1 (vrlo sporo) do 2.0 (vrlo brzo). Normalno je 1.0. */
    fun setRate(rate: Float)
}

/**
 * Polje kao izgovorene reči ("e four", „e vier", „е четыре").
 *
 * Slovo pa broj, jer TTS „e4" pročita kao jednu reč i teško se razaznaje.
 */
fun Square.spoken(words: SpeechWords): String =
    "${words.files.getValue(file)} ${words.ranks.getValue(rank.digitToChar())}"

/**
 * Potez kao dva polja. Umesto veznika stoji zarez: „to", „nach", „на" se razlikuju
 * po jezicima, a pauza radi isti posao svuda.
 */
fun Move.spoken(words: SpeechWords): String = "${from.spoken(words)}, ${to.spoken(words)}"

/**
 * Cela pozicija, u delovima.
 *
 * Deli se na „bela dama na" pa „e pet", pa sledeća figura — jer se naslepo
 * pamti u dva koraka: šta stoji, pa gde stoji. Zarez i tačka razdvajaju figure
 * i strane, i na njima motor sam malo zastane.
 *
 * Beli pa crni, a unutar boje po vrednosti figure — kralj prvi, jer se oko njega
 * gradi slika. Redosled je uvek isti da bi se pozicija pamtila kao niz, a ne kao
 * skup.
 */
fun Board.spokenParts(words: SpeechWords): List<String> {
    fun side(color: Color): List<List<String>> = occupied()
        .filter { (_, piece) -> piece.color == color }
        .sortedWith(compareBy({ NARRATION_ORDER.indexOf(it.second.type) }, { it.first.index }))
        .map { (square, piece) -> words.describeParts(piece, square.spoken(words)) }

    val white = side(Color.WHITE)
    val black = side(Color.BLACK)

    return buildList {
        // Zarez razdvaja figure iste boje, tačka razdvaja strane — i u zapisu i
        // u govoru, jer TTS na njima i sam malo zastane.
        white.forEachIndexed { index, parts ->
            add(parts.first())
            val isLastOfAll = index == white.lastIndex && black.isEmpty()
            add(parts.last() + if (isLastOfAll) "" else if (index == white.lastIndex) "." else ",")
        }
        black.forEachIndexed { index, parts ->
            add(parts.first())
            add(parts.last() + if (index == black.lastIndex) "" else ",")
        }
    }
}

/** Ista pozicija, spojena u jednu rečenicu. */
fun Board.spoken(words: SpeechWords): String = spokenParts(words).joinToString(" ")

private val NARRATION_ORDER = listOf(
    PieceType.KING,
    PieceType.QUEEN,
    PieceType.ROOK,
    PieceType.BISHOP,
    PieceType.KNIGHT,
    PieceType.PAWN
)
