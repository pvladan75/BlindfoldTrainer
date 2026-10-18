package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Izgovaranje. Iza interfejsa je da bi se u testovima mogao zameniti lažnjakom,
 * i da moduli ne zavise od zvučnika direktno.
 *
 * Od 18. oktobra 2026. jedina implementacija je [ClipSpeaker]: govor iz
 * snimljenih isečaka jednog glasa, bez TTS-a na uređaju. Sve što se izgovara je
 * [SpokenLine] — spisak isečaka iz [SpeechVocabulary]; slobodnog teksta nema,
 * jer za njega nema isečka.
 */
interface Speaker {

    /**
     * Da li se **upravo sada** nešto govori.
     *
     * Postoji zato što se govor ne sme redati tajmerom. Modul koji posle
     * izgovorene rečenice čeka „otprilike sekundu i po" pogađa koliko rečenica
     * traje — a ona zavisi od brzine govora koju je korisnik podesio i od toga
     * koliko polja se nabraja. Kad se promaši, sledeća rečenica preseče
     * prethodnu na pola reči.
     *
     * Zatečeno je **ćutanje**. Govornik koji ne ume da javi dokle je stigao time
     * kaže „ne znam", a modul se onda ponaša kao i pre — nastavi odmah, umesto
     * da čeka signal koji nikad neće doći.
     */
    val isSpeaking: StateFlow<Boolean> get() = NEVER_SPEAKING

    /**
     * Izgovara rečenicu.
     *
     * Uz `interrupt = false` **čeka svoj red** umesto da preseče ono što se
     * upravo govori. Motor ume da odgovori pre nego što se izgovori tvoj potez,
     * pa bi bez toga potvrda poteza nestala na pola reči.
     */
    fun say(line: SpokenLine, interrupt: Boolean = true)

    /** Izgovara polje. */
    fun say(square: Square, interrupt: Boolean = true)

    fun say(move: Move, interrupt: Boolean = true)

    /**
     * Potez sa figurom koja ga igra: „queen", pa „c3, c2".
     *
     * Sama polja ne kažu **šta** se pomerilo, a naslepo je to pola slike. Bez
     * imena figure se odigran potez ne razlikuje od pogrešno razumljenog, pa
     * potvrda ume da učvrsti baš onu zabludu zbog koje je greška i nastala.
     *
     * Boja se ne izgovara: u vežbi se strane smenjuju, pa bi bila samo duža
     * rečenica. Tako se potez i najavljuje u pravoj partiji naslepo.
     */
    fun say(piece: PieceType, move: Move, interrupt: Boolean = true)

    /** Čita celu poziciju: „white king on e2, white queen on e5…". */
    fun say(board: Board, interrupt: Boolean = true)

    /**
     * Izgovara **rečenicu iz rečnika**.
     *
     * Modul bira rečenicu, ne reči: `speaker.say { correct }`. Rečenica koja bi
     * se pisala u modulu ne bi imala isečak, pa se i ne može napisati.
     */
    fun say(interrupt: Boolean = true, phrase: SpeechVoice.() -> SpokenLine)

    /**
     * Ponavlja **poslednju najavu**, uz fonetska imena kolona.
     *
     * Najava je sve što je izgovoreno u jednom dahu, ma iz koliko poziva došlo:
     * „knight from", „e4", „target", „g7" je **jedna** stvar koja se ponavlja,
     * a ne četiri. Dotad se pamtio samo poslednji poziv, pa je „ponovi" vraćao
     * „g7" i ništa pre toga — baš ono što je čovek već čuo.
     *
     * Ponavlja se fonetski („echo four"): „b" i „d" se preko zvučnika razlikuju
     * tek toliko koliko dozvoli soba, a čovek koji je pritisnuo „ponovi" je već
     * jednom pogrešno čuo — ponoviti mu isto istim rečima znači ponuditi istu
     * nedoumicu drugi put.
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

    /** Brzina: 0.5 (sporo) do 1.5 (brzo). Normalno je 1.0. */
    fun setRate(rate: Float)
}

/** Govornik koji ne prati svoj govor prijavljuje tišinu — vidi [Speaker.isSpeaking]. */
private val NEVER_SPEAKING: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
