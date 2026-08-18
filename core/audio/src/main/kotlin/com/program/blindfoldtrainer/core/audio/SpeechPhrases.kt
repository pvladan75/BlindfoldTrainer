package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.model.Skill

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

    /**
     * Ime veštine u govoru.
     *
     * Postoji iako imena stoje i kao resursi ekrana, i to je izuzetak od pravila
     * o dva izvora istine — jer bez ekrana je ovo **jedini** način da se sazna
     * šta je sesija pomerila, a to je najvredniji red celog sažetka.
     */
    fun skillName(skill: Skill): String
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
 * Jezici koji se **smeju izabrati** — oni koji imaju rečenice.
 *
 * Reči za polja i imena figura postoje za svih devet jezika, ali jezik bez
 * rečenica nije pola-jezik nego mešavina. Sa uređaja, uz nemački: „pola na
 * engleskom, pola na nemačkom" — engleska rečenica sa nemačkim imenom figure u
 * sredini.
 *
 * Zato je prevod **uslov**, a ne dodatak: jezik se pojavljuje u Podešavanjima
 * tek kad dobije rečenice. Osam jezika ih čeka; niko od nas ne može da proveri
 * prevod koji ne govori, pa se ne izmišljaju — isto pravilo po kom reči za polja
 * nose `isVerified`.
 */
val TRANSLATED_LANGUAGES: Set<Language> = setOf(Language.ENGLISH)

/**
 * Glas za jezik — rečenice **i** imena, uvek iz istog jezika.
 *
 * Jezik bez rečenica se ovde ceo prebacuje na engleski. Prvo je bilo obrnuto:
 * imena su pratila izabrani jezik, a rečenice zamenu. Na papiru je delovalo kao
 * da se čuva ono što jezik ima; u ušima je to bila mešavina dva jezika u istoj
 * rečenici, i sa uređaja je odmah prijavljeno kao zbunjujuće.
 *
 * > Korisnik je izabrao **jedan** jezik i to je ono što mora da čuje.
 */
internal fun voiceFor(language: Language): SpeechVoice {
    val spoken = if (language in TRANSLATED_LANGUAGES) language else Language.ENGLISH
    return Voice(phrasesFor(spoken), SpeechLanguages.wordsFor(spoken))
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

    override fun skillName(skill: Skill): String = when (skill) {
        Skill.COORDINATES -> "square knowledge"
        Skill.PIECE_GEOMETRY -> "piece geometry"
        Skill.POSITION_HOLD -> "holding the position"
        Skill.POSITION_UPDATE -> "updating the position"
        Skill.NOTATION -> "notation to picture"
        Skill.RECOVERY -> "rebuilding the picture"
        Skill.SQUARE_CONTROL -> "square control"
        Skill.CALCULATION -> "blindfold calculation"
    }
}

/**
 * Rečenice za jezik.
 *
 * Jezik koji ih još nema dobija **engleske**. To je poslednja mreža — takav
 * jezik se u Podešavanjima i ne nudi, pa se ovde stiže samo sa izborom koji je
 * zapamćen ranije ili sa jezikom za koji uređaj nema glas.
 */
fun phrasesFor(language: Language): SpeechPhrases = when (language) {
    else -> EnglishPhrases
}
