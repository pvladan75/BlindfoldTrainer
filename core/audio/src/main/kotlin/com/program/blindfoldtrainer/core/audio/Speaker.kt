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

    /** Prekida ono što se trenutno izgovara i kaže [text]. */
    fun say(text: String)

    /**
     * Izgovara polje na jeziku koji je izabran za govor.
     *
     * Formatiranje je ovde, a ne kod pozivaoca, jer zavisi od jezika — a moduli
     * za jezik ne znaju niti treba da znaju.
     */
    fun say(square: Square)

    fun say(move: Move)

    /** Čita celu poziciju: „beli kralj na e2, bela dama na e5…". */
    fun say(board: Board)

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
 * Cela pozicija, izgovorena.
 *
 * Beli pa crni, a unutar boje po vrednosti figure — kralj prvi, jer se oko
 * njega gradi slika. Redosled je uvek isti da bi se pozicija mogla pamtiti kao
 * niz, a ne kao skup.
 */
fun Board.spoken(words: SpeechWords): String {
    fun side(color: Color): String = occupied()
        .filter { (_, piece) -> piece.color == color }
        .sortedWith(compareBy({ NARRATION_ORDER.indexOf(it.second.type) }, { it.first.index }))
        .joinToString(", ") { (square, piece) ->
            words.describe(piece, square.spoken(words))
        }

    return listOf(side(Color.WHITE), side(Color.BLACK))
        .filter { it.isNotBlank() }
        .joinToString(". ")
}

private val NARRATION_ORDER = listOf(
    PieceType.KING,
    PieceType.QUEEN,
    PieceType.ROOK,
    PieceType.BISHOP,
    PieceType.KNIGHT,
    PieceType.PAWN
)
