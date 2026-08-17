package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.SpeechLanguage

/**
 * Rečenice koje aplikacija izgovara.
 *
 * Postoje zato što su polja i imena figura odavno pratili izabrani jezik, a
 * **rečenice oko njih nisu** — stajale su kao literali u modulima. Ko izabere
 * engleski glas, dobio bi engleski izgovor srpskih reči; prijavljeno sa uređaja
 * 17. avgusta 2026.
 *
 * ### Zašto sučelje, a ne tabela u mapi
 *
 * Nova rečenica je **greška u prevođenju** dok je svaki jezik ne dobije. Mapa bi
 * je propustila i otkrila tek na uređaju, kao tišinu ili kao ključ pročitan
 * naglas — a nemi otkaz je u ovom projektu već triput skupo koštao.
 *
 * ### Zašto funkcije, a ne obrasci sa `%s`
 *
 * Jezici se ne slažu oko toga gde broj stoji ni šta uz njega ide: „u 1 poteza"
 * na engleskom mora biti „in 1 move", a u množini „in 3 moves". Funkcija to
 * reši u jeziku kom pripada, umesto da se svuda po modulima pravi izuzetak.
 *
 * ### Šta ovde **ne** ide
 *
 * Samo ono što se **čuje**. Tekst koji se vidi na ekranu prati jezik aplikacije,
 * a to je druga osa: čovek sme da drži aplikaciju na svom jeziku a da polja
 * sluša na engleskom, jer engleski TTS glas ima svaki telefon a njegov možda
 * nema. Gde ista rečenica danas ide i u govor i na ekran — ishod u Završnici i
 * pitanje u Prati partiju — govor uzima odavde, a ekran ostaje na svome.
 */
interface SpeechPhrases {

    // — zajedničko za module —

    /** Prvi dodir na zonu za odustajanje; drugi zaista odustaje. */
    val confirmGiveUp: String

    /** Isto, tamo gde se sesija prekida a ne odustaje od zadatka. */
    val confirmStop: String

    val correct: String

    /** „Kraj sesije. Rešeno 3 od 5" — tamo gde se zadaci rešavaju. */
    fun sessionEndSolved(solved: Int, total: Int): String

    /** Isto, tamo gde se na pitanja odgovara tačno ili netačno. */
    fun sessionEndCorrect(correct: Int, total: Int): String

    // — Dokrajči protivnika —

    val nothingToUndo: String
    val undone: String
    val movingToNextPosition: String

    /** Ide ispred izgovorenog polja: „Na" pa „ce tri". */
    val onSquare: String

    /**
     * „nije top nego dama" — imena figura stižu spolja, iz [SpeechWords], jer
     * ona jezik već prate.
     */
    fun pieceMismatch(named: String, actual: String): String

    val noPieceThere: String

    fun noneCanReach(piece: String): String

    fun twoCanReach(piece: String): String

    val sayOriginToo: String

    val outcomeMated: String
    val outcomeLost: String
    val outcomeStalemate: String
    val outcomeFiftyMoves: String
    val outcomeGaveUp: String

    // — Interaktivni parovi —

    val notThat: String
    val gaveUpMovingOn: String
    val puzzleSolved: String

    // — Putanja skakača —

    val knightIsOn: String

    /** „cilj" — stoji između trenutnog i ciljnog polja. */
    val goal: String

    fun movesLeft(moves: Int): String

    val notKnightMove: String

    fun correctInMoves(moves: Int): String

    val shortestGoesLikeThis: String

    val knightFrom: String

    /** „na" — predlog između dva polja u prikazu rešenja. */
    val toSquare: String

    fun inMoves(moves: Int): String

    // — Prati partiju —

    val gameReady: String

    fun wrongPieceIsOn(piece: String): String

    fun whereIsPiece(piece: String): String

    // — Geometrija table —

    val lightSquare: String
    val darkSquare: String

    fun wrongSquareIs(color: String): String

    fun timeoutSquareIs(color: String): String

    // — Postavi po diktatu —

    val allCorrect: String

    fun correctOutOf(correct: Int, total: Int): String

    // — sažetak sesije —

    /** Šta se sad može, kad se sažetak pojavi bez ekrana. */
    val summaryZones: String

    fun summaryResult(solved: Int, attempted: Int, mistakes: Int): String

    fun summaryXp(xp: Int): String

    /**
     * Bez imena ranga i dostignuća — ona su danas resursi ekrana, pa bi drugi
     * spisak ovde značio dva izvora istine za isto ime. Da se čuje **da** se
     * nešto osvojilo je ono što bez ekrana nedostaje; šta tačno, piše na ekranu.
     */
    val summaryRankUp: String
    val summaryAchievement: String
}

/**
 * Ono što modul dobije u ruke kad govori: rečenice **i** imena figura.
 *
 * Imena su ovde zato što idu unutar rečenica („Nijedan top ne može na"), a dva
 * modula su ih do sada držala kao svoje spiskove — oba na srpskom, iako ih
 * [SpeechWords] nosi po jeziku. Sad se ime traži odavde i prati jezik samo od
 * sebe.
 */
interface SpeechVoice : SpeechPhrases {

    /** „top" — bez boje, kad je iz rečenice jasno čija je. */
    fun nameOf(type: PieceType): String

    /** „bela dama" — sa bojom i slaganjem roda. */
    fun nameOf(piece: Piece): String
}

private class Voice(
    phrases: SpeechPhrases,
    private val words: SpeechWords
) : SpeechVoice, SpeechPhrases by phrases {

    override fun nameOf(type: PieceType): String = words.pieces.getValue(type)

    override fun nameOf(piece: Piece): String = words.name(piece)
}

/**
 * Glas za jezik: rečenice po [phrasesFor], imena figura iz [SpeechWords].
 *
 * Ta dva ne moraju biti isti jezik i to je namerno. Imena figura postoje za svih
 * deset jezika, a rečenice zasad za dva — pa ko govori poljski dobija poljska
 * imena u engleskim rečenicama. Bolje nego da mu se cela vežba prebaci na jezik
 * koji nije tražio.
 */
internal fun voiceFor(language: SpeechLanguage): SpeechVoice =
    Voice(phrasesFor(language), SpeechLanguages.wordsFor(language))

/** Prvi jezik — na njemu su rečenice i pisane. */
internal object SerbianPhrases : SpeechPhrases {

    override val confirmGiveUp = "Dodirni ponovo da odustaneš."
    override val confirmStop = "Dodirni ponovo da prekineš."
    override val correct = "Tačno."

    override fun sessionEndSolved(solved: Int, total: Int) =
        "Kraj sesije. Rešeno $solved od $total"

    override fun sessionEndCorrect(correct: Int, total: Int) =
        "Kraj sesije. Tačno $correct od $total"

    override val nothingToUndo = "Nema šta da se poništi."
    override val undone = "Poništeno."
    override val movingToNextPosition = "Prelazim na sledeću poziciju."
    override val onSquare = "Na"

    override fun pieceMismatch(named: String, actual: String) = "nije $named nego $actual"

    override val noPieceThere = "nema figure"

    override fun noneCanReach(piece: String) = "Nijedan $piece ne može na"

    override fun twoCanReach(piece: String) = "Dva puta $piece može na"

    override val sayOriginToo = "reci i polazno polje"

    override val outcomeMated = "Mat! Pozicija je privedena kraju."
    override val outcomeLost = "Matiran si — u dobijenoj poziciji."
    override val outcomeStalemate = "Pat — dobijena pozicija je prokockana u remi."
    override val outcomeFiftyMoves = "Pedeset poteza bez napretka."
    override val outcomeGaveUp = "Figure su otkrivene."

    override val notThat = "Nije to."
    override val gaveUpMovingOn = "Odustao si. Prelazim na sledeću."
    override val puzzleSolved = "Zagonetka rešena."

    override val knightIsOn = "Skakač je na"
    override val goal = "cilj"

    override fun movesLeft(moves: Int) = "preostalo poteza $moves"

    override val notKnightMove = "Nije potez skakača."

    override fun correctInMoves(moves: Int) = "Tačno, u $moves poteza."

    override val shortestGoesLikeThis = "Nije uspelo. Najkraće ide ovako:"
    override val knightFrom = "Skakač sa"
    override val toSquare = "na"

    override fun inMoves(moves: Int) = "u $moves poteza"

    override val gameReady = "Partija je spremna. Dodirni za prvi potez."

    override fun wrongPieceIsOn(piece: String) = "Nije. $piece je na"

    override fun whereIsPiece(piece: String) = "Gde stoji $piece?"

    override val lightSquare = "svetlo"
    override val darkSquare = "tamno"

    override fun wrongSquareIs(color: String) = "Nije, polje je $color."

    override fun timeoutSquareIs(color: String) = "Isteklo je vreme, polje je $color."

    override val allCorrect = "Sve tačno."

    override fun correctOutOf(correct: Int, total: Int) = "Tačno $correct od $total"

    override val summaryZones = "Gore još jednom, u sredini rezultat, dole meni."

    override fun summaryResult(solved: Int, attempted: Int, mistakes: Int) =
        "Rešeno $solved od $attempted. Grešaka $mistakes"

    override fun summaryXp(xp: Int) = "Osvojeno $xp poena."

    override val summaryRankUp = "Novi rang."
    override val summaryAchievement = "Novo dostignuće."
}

/**
 * Engleski — i sam po sebi, i kao zamena za jezike koji rečenice još nemaju.
 *
 * Engleski TTS glas ima praktično svaki telefon, pa je zamena uvek čujna. To je
 * ista ona odluka po kojoj se čita engleski kad izabrani jezik nema glas.
 */
internal object EnglishPhrases : SpeechPhrases {

    override val confirmGiveUp = "Touch again to give up."
    override val confirmStop = "Touch again to stop."
    override val correct = "Correct."

    override fun sessionEndSolved(solved: Int, total: Int) =
        "End of session. Solved $solved out of $total"

    override fun sessionEndCorrect(correct: Int, total: Int) =
        "End of session. Correct $correct out of $total"

    override val nothingToUndo = "There is nothing to undo."
    override val undone = "Move taken back."
    override val movingToNextPosition = "Moving on to the next position."
    override val onSquare = "On"

    override fun pieceMismatch(named: String, actual: String) =
        "there is no $named but a $actual"

    override val noPieceThere = "there is no piece"

    override fun noneCanReach(piece: String) = "No $piece can go to"

    override fun twoCanReach(piece: String) = "Two of your ${piece}s can go to"

    override val sayOriginToo = "say the starting square as well"

    override val outcomeMated = "Checkmate! The position is finished off."
    override val outcomeLost = "You are checkmated — in a winning position."
    override val outcomeStalemate = "Stalemate — a won position thrown away."
    override val outcomeFiftyMoves = "Fifty moves without progress."
    override val outcomeGaveUp = "The pieces are revealed."

    override val notThat = "Not that one."
    override val gaveUpMovingOn = "You gave up. Moving on to the next one."
    override val puzzleSolved = "Puzzle solved."

    override val knightIsOn = "The knight is on"
    override val goal = "target"

    // Jednina i množina: „1 move left" naspram „3 moves left".
    override fun movesLeft(moves: Int) =
        if (moves == 1) "1 move left" else "$moves moves left"

    override val notKnightMove = "That is not a knight move."

    override fun correctInMoves(moves: Int) =
        if (moves == 1) "Correct, in 1 move." else "Correct, in $moves moves."

    override val shortestGoesLikeThis = "That did not work. The shortest way goes like this:"
    override val knightFrom = "Knight from"
    override val toSquare = "to"

    override fun inMoves(moves: Int) = if (moves == 1) "in 1 move" else "in $moves moves"

    override val gameReady = "The game is ready. Touch for the first move."

    override fun wrongPieceIsOn(piece: String) = "No. The $piece is on"

    override fun whereIsPiece(piece: String) = "Where is the $piece?"

    override val lightSquare = "light"
    override val darkSquare = "dark"

    override fun wrongSquareIs(color: String) = "No, the square is $color."

    override fun timeoutSquareIs(color: String) = "Time is up, the square is $color."

    override val allCorrect = "All correct."

    override fun correctOutOf(correct: Int, total: Int) = "Correct $correct out of $total"

    override val summaryZones = "Again at the top, result in the middle, menu at the bottom."

    override fun summaryResult(solved: Int, attempted: Int, mistakes: Int) =
        "Solved $solved out of $attempted. Mistakes $mistakes"

    override fun summaryXp(xp: Int) = "You earned $xp points."

    override val summaryRankUp = "New rank."
    override val summaryAchievement = "New achievement."
}

/**
 * Rečenice za jezik.
 *
 * Jezik koji ih još nema dobija **engleske**, a ne prazne ili srpske: prazne bi
 * ćutale, a srpske bi ga terale da izgovara reči koje ne ume. Isto pravilo po
 * kom imena polja nose `isVerified` — bolje poznata zamena nego izmišljen
 * prevod koji niko od nas ne može da proveri.
 */
fun phrasesFor(language: SpeechLanguage): SpeechPhrases = when (language) {
    SpeechLanguage.SERBIAN -> SerbianPhrases
    else -> EnglishPhrases
}
