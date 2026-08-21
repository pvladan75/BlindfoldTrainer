package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Izgovaranje teksta. Iza interfejsa je da bi se u testovima mogao zameniti
 * lažnjakom, i da moduli ne zavise od `android.speech.tts` direktno.
 */
interface Speaker {

    /**
     * Da li se **upravo sada** nešto govori.
     *
     * Postoji zato što se govor ne sme redati tajmerom. Modul koji posle
     * izgovorene rečenice čeka „otprilike sekundu i po" pogađa koliko rečenica
     * traje — a ona zavisi od jezika, brzine govora koju je korisnik podesio, i
     * od toga koliko polja se nabraja. Kad se promaši, sledeća rečenica preseče
     * prethodnu na pola reči.
     *
     * Isto obrazloženje po kom se slušanje više ne gasi pa pali između dva
     * polja: **pogađati dužinu tuđeg posla je uzaludno.**
     *
     * Zatečeno je **ćutanje**. Govornik koji ne ume da javi dokle je stigao time
     * kaže „ne znam", a modul se onda ponaša kao i pre — nastavi odmah, umesto
     * da čeka signal koji nikad neće doći.
     */
    val isSpeaking: StateFlow<Boolean> get() = NEVER_SPEAKING

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

    /**
     * Potez sa figurom koja ga igra: „dama", pa „c tri, c dva".
     *
     * Sama polja ne kažu **šta** se pomerilo, a naslepo je to pola slike. Bez
     * imena figure se odigran potez ne razlikuje od pogrešno razumljenog, pa
     * potvrda ume da učvrsti baš onu zabludu zbog koje je greška i nastala.
     *
     * Boja se ne izgovara: u vežbi se strane smenjuju, pa bi bila samo duža
     * rečenica. Tako se potez i najavljuje u pravoj partiji naslepo.
     */
    fun say(piece: PieceType, move: Move, interrupt: Boolean = true)

    /** Čita celu poziciju: „beli kralj na e2, bela dama na e5…". */
    fun say(board: Board, interrupt: Boolean = true)

    /**
     * Izgovara **rečenicu na jeziku koji je izabran za govor**.
     *
     * Modul bira rečenicu, ne jezik: `speaker.say { correct }`. Tako je i sa
     * poljima — modul za jezik ne zna niti treba da zna, a rečenica koja bi se
     * pisala u modulu bila bi zauvek na jednom jeziku.
     */
    fun say(interrupt: Boolean = true, phrase: SpeechVoice.() -> String)

    /**
     * Ponavlja **poslednju najavu**, uz fonetska imena kolona.
     *
     * Najava je sve što je izgovoreno u jednom dahu, ma iz koliko poziva došlo:
     * „skakač sa", „e četiri", „cilj", „g sedam" je **jedna** stvar koja se
     * ponavlja, a ne četiri. Dotad se pamtio samo poslednji poziv, pa je „ponovi"
     * vraćao „g sedam" i ništa pre toga — baš ono što je čovek već čuo.
     *
     * Ponavlja se fonetski — vidi [Square.spokenPhonetic].
     *
     * Stoji ovde jer mu treba samo ono što je rečeno — pa radi u **svakom**
     * modulu, a nijedan ne mora ništa da zna o tome.
     */
    fun repeat()

    /**
     * Sve izgovoreno unutar [block] **ne ulazi u „ponovi"**.
     *
     * Za ono što već ima svoje dugme: čitanje pozicije i čitanje stanja. Bez
     * ovoga jedan dodir na „pozicija" pojede „ponovi" — a onda se do rečenice
     * koja je zaista promakla više ne može, dok se pozicija ionako dobija
     * ponovnim dodirom na njeno sopstveno dugme.
     *
     * Zatečeno ne radi ništa: govornik koji ne pamti šta je rekao nema šta ni
     * da izuzme.
     */
    fun aside(block: () -> Unit) = block()

    fun stop()

    /** 0.1 (vrlo sporo) do 2.0 (vrlo brzo). Normalno je 1.0. */
    fun setRate(rate: Float)
}

/** Govornik koji ne prati svoj govor prijavljuje tišinu — vidi [Speaker.isSpeaking]. */
private val NEVER_SPEAKING: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

/**
 * Sklanja tačku koja stoji **odmah iza cifre**.
 *
 * „Rešeno 1 od 4." se izgovara kao **„Rešeno jedan od četvrti"** — u srpskom je
 * broj sa tačkom redni broj, i TTS to pravilo poštuje doslovno. Isto važi za
 * nemački („4." → „vierte") i još pokoji jezik; engleskom ne smeta.
 *
 * Tačka se ne briše nego **postaje zarez** kad rečenica ide dalje: pauza je bila
 * i namena te tačke, a zarez je daje bez rednog broja. Na samom kraju se briše —
 * izgovor se ionako tu završava.
 *
 * Stoji na jednom mestu, u [AndroidSpeaker], jer pravilo ne zna nijedan modul a
 * važi za svaki: kraj sesije se u pet modula izgovara rečenicom koja se završava
 * brojem. Sa uređaja je i prijavljeno baš tako — „Rešeno jedan od četvrti".
 *
 * Decimale se ne diraju: tačka između dve cifre nije kraj rečenice.
 */
fun withoutOrdinalPeriod(text: String): String = text
    .replace(DIGIT_BEFORE_PAUSE, "$1,$2")
    .replace(DIGIT_AT_END, "$1")

private val DIGIT_BEFORE_PAUSE = Regex("""(\d)\.(\s)""")
private val DIGIT_AT_END = Regex("""(\d)\.$""")

/**
 * Polje kao izgovorene reči ("e four", „e vier", „е четыре").
 *
 * Slovo pa broj, jer TTS „e4" pročita kao jednu reč i teško se razaznaje.
 */
fun Square.spoken(words: SpeechWords): String =
    "${words.files.getValue(file)} ${words.ranks.getValue(rank.digitToChar())}"

/**
 * Polje sa **imenom kolone umesto slova**: „bravo pet" umesto „b pet".
 *
 * Za ponavljanje, ne za prvo izgovaranje. „B" i „D" se preko zvučnika razlikuju
 * tek toliko koliko dozvoli soba u kojoj sediš, a čovek koji je pritisnuo
 * „ponovi" je već jednom pogrešno čuo — ponoviti mu isto istim rečima znači
 * ponuditi istu nedoumicu drugi put.
 *
 * Ista tablica po kojoj se polje **prima** glasom, samo okrenuta. Reči su
 * engleske i ne prevode se: standard je međunarodni, a poenta je što se dva
 * sloga ne mešaju ni sa čim.
 */
fun Square.spokenPhonetic(words: SpeechWords): String =
    "${PHONETIC_FILE_NAMES.getValue(file)} ${words.ranks.getValue(rank.digitToChar())}"

/** [PHONETIC_FILES] okrenuta: kolona → reč kojom se izgovara. */
private val PHONETIC_FILE_NAMES: Map<Char, String> =
    PHONETIC_FILES.entries.associate { (word, file) -> file to word }

fun Move.spokenPhonetic(words: SpeechWords): String =
    "${from.spokenPhonetic(words)}, ${to.spokenPhonetic(words)}"

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
