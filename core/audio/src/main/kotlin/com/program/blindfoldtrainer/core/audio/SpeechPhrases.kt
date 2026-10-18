package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.audio.SpeechVocabulary.asLine
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.audio.SpokenToken.Pause

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
 * ### Od 18. oktobra 2026: rečenica je spisak isečaka
 *
 * Govor ne ide kroz TTS nego iz snimljenih isečaka ([SpeechVocabulary]), pa
 * svaka rečenica vraća [SpokenLine] — šta se čuje i šta piše, iz istog spiska.
 * Parametri su zato **tipovi**, ne reči: figura je [PieceType], jer rečenica
 * mora da zna koji isečak da uzme, a ime figure u tekstu je posledica.
 *
 * ### Šta ovde **ne** ide
 *
 * Samo ono što se **čuje**. Gde ista rečenica ide i u govor i na ekran — ishod u
 * Završnici, pitanje u Prati partiju — ekran crta [SpokenLine.text].
 */
interface SpeechPhrases {

    // — zajedničko za module —

    /** Prvi dodir na zonu za odustajanje; drugi zaista odustaje. */
    val confirmGiveUp: SpokenLine

    /** Isto, tamo gde se sesija prekida a ne odustaje od zadatka. */
    val confirmStop: SpokenLine

    val correct: SpokenLine

    /** „Kraj sesije. Rešeno 3 od 5" — tamo gde se zadaci rešavaju. */
    fun sessionEndSolved(solved: Int, total: Int): SpokenLine

    /** Isto, tamo gde se na pitanja odgovara tačno ili netačno. */
    fun sessionEndCorrect(correct: Int, total: Int): SpokenLine

    // — Dokrajči protivnika —

    val nothingToUndo: SpokenLine
    val undone: SpokenLine
    val movingToNextPosition: SpokenLine

    /** Ide ispred izgovorenog polja: „Na" pa „ce tri". */
    val onSquare: SpokenLine

    /**
     * Ide iza polja: „nije top nego lovac". Figure stižu kao tipovi, da
     * rečenica sama nađe isečak.
     */
    fun pieceMismatch(named: PieceType, actual: PieceType): SpokenLine

    val noPieceThere: SpokenLine

    fun noneCanReach(piece: PieceType): SpokenLine

    fun twoCanReach(piece: PieceType): SpokenLine

    val sayOriginToo: SpokenLine

    val outcomeMated: SpokenLine
    val outcomeLost: SpokenLine
    val outcomeStalemate: SpokenLine
    val outcomeFiftyMoves: SpokenLine
    val outcomeGaveUp: SpokenLine

    // — Raskini vezu —

    val notThat: SpokenLine
    val gaveUpMovingOn: SpokenLine
    val puzzleSolved: SpokenLine

    // — Putanja skakača / Daj šah —

    val knightIsOn: SpokenLine

    val goal: SpokenLine

    fun movesLeft(moves: Int): SpokenLine

    val notKnightMove: SpokenLine

    val pieceInTheWay: SpokenLine

    val squareIsAttacked: SpokenLine

    val alreadyThere: SpokenLine

    fun correctInMoves(moves: Int): SpokenLine

    val shortestGoesLikeThis: SpokenLine

    val knightFrom: SpokenLine

    val toSquare: SpokenLine

    fun inMoves(moves: Int): SpokenLine

    // — Kretanje figura —

    fun walkWith(piece: PieceType): SpokenLine

    fun walkIllegal(piece: PieceType): SpokenLine

    val walkVisited: SpokenLine

    val walkStuck: SpokenLine

    val walkReplay: SpokenLine

    fun retellWatch(piece: PieceType): SpokenLine

    val retellNow: SpokenLine

    val retellWrong: SpokenLine

    fun retellLeft(squares: Int): SpokenLine

    val walkAsRook: SpokenLine
    val walkAsBishop: SpokenLine

    fun walkStandsOn(piece: PieceType): SpokenLine

    fun walkHeld(moves: Int): SpokenLine

    fun reachPieceOn(piece: PieceType): SpokenLine

    fun reachWhichOnFile(file: Char): SpokenLine

    fun reachWhichOnRank(rank: Int): SpokenLine

    val reachYouSaid: SpokenLine

    val reachNone: SpokenLine

    val reachTruthIs: SpokenLine

    val walkHowTo: SpokenLine
    val reachHowTo: SpokenLine

    // — Prati partiju —

    val gameReady: SpokenLine

    fun wrongPieceIsOn(piece: PieceType): SpokenLine

    /** „Where is the black queen?" — sa bojom, jer u partiji ima obe. */
    fun whereIsPiece(piece: Piece): SpokenLine

    /** „Not there. The black queen is on d8" */
    fun notThereIsOn(piece: Piece, square: Square): SpokenLine

    /**
     * „Who attacks the black knight on e5? Find 2." — broj napadača se kaže
     * unapred, da se zna kad je odgovor gotov.
     */
    fun whoAttacks(piece: Piece, square: Square, count: Int): SpokenLine

    /** „Attackers: a1, b2" */
    fun attackersAre(squares: List<Square>): SpokenLine

    // — Geometrija —

    val lightSquare: SpokenLine
    val darkSquare: SpokenLine

    fun wrongSquareIs(isLight: Boolean): SpokenLine

    fun timeoutSquareIs(isLight: Boolean): SpokenLine

    // — Diktat / Zapamti poziciju —

    val allCorrect: SpokenLine

    fun correctOutOf(correct: Int, total: Int): SpokenLine

    // — sažetak sesije bez ekrana —

    val summaryZones: SpokenLine

    fun summaryResult(solved: Int, attempted: Int, mistakes: Int): SpokenLine

    fun summaryXp(xp: Int): SpokenLine

    val summaryRankUp: SpokenLine
    val summaryAchievement: SpokenLine
}

/**
 * Ono što modul dobije u ruke kad govori: rečenice **i** imena figura.
 *
 * Imena su ovde zato što idu unutar rečenica („Nijedan top ne može na"); modul
 * ih ne sastavlja sam nego traži rečenicu po tipu figure.
 */
interface SpeechVoice : SpeechPhrases {

    /** „rook" — bez boje, kad je iz rečenice jasno čija je. */
    fun nameOf(type: PieceType): SpokenLine

    /** „black queen" — sa bojom. */
    fun nameOf(piece: Piece): SpokenLine
}

private class Voice(phrases: SpeechPhrases) : SpeechVoice, SpeechPhrases by phrases {

    override fun nameOf(type: PieceType): SpokenLine = SpokenLine(SpeechVocabulary.piece(type))

    override fun nameOf(piece: Piece): SpokenLine =
        SpokenLine(SpeechVocabulary.color(piece.color), SpeechVocabulary.piece(piece.type))
}

/**
 * Glas za jezik. Jezik je od 18. oktobra 2026. jedan, pa ovde nema šta da se
 * bira — mesto ostaje da se drugi jezik, kad se vrati, odlučuje ovde a ne po
 * modulima.
 */
internal fun voiceFor(language: Language): SpeechVoice = Voice(phrasesFor(language))

/**
 * Engleski — jedini jezik koji aplikacija govori, iz isečaka jednog glasa.
 *
 * Javan zato što „Prati partiju" sastavlja svoja pitanja iz istih rečenica a
 * crta njihov tekst na ekranu; drugi spisak rečenica bi bio drugi izvor istine.
 */
object EnglishPhrases : SpeechPhrases {

    private val v = SpeechVocabulary

    override val confirmGiveUp = v.confirmGiveUp.asLine()
    override val confirmStop = v.confirmStop.asLine()
    override val correct = v.correct.asLine()

    override fun sessionEndSolved(solved: Int, total: Int) =
        SpokenLine(listOf(v.endOfSessionSolved.clip()) + v.numberMid(solved) + v.outOf.clip() + v.numberEnd(total))

    override fun sessionEndCorrect(correct: Int, total: Int) =
        SpokenLine(listOf(v.endOfSessionCorrect.clip()) + v.numberMid(correct) + v.outOf.clip() + v.numberEnd(total))

    override val nothingToUndo = v.nothingToUndo.asLine()
    override val undone = v.undone.asLine()
    override val movingToNextPosition = v.movingToNextPosition.asLine()
    override val onSquare = v.onHead.asLine()

    override fun pieceMismatch(named: PieceType, actual: PieceType) =
        SpokenLine(v.thereIsNo.clip(), v.piece(named), v.butA.clip(), v.pieceAtEnd(actual))

    override val noPieceThere = v.thereIsNoPiece.asLine()

    override fun noneCanReach(piece: PieceType) = SpokenLine(v.noHead.clip(), v.piece(piece), v.canGoTo.clip())

    override fun twoCanReach(piece: PieceType) =
        SpokenLine(v.twoOfYour.clip(), v.piecesPlural(piece), v.canGoTo.clip())

    override val sayOriginToo = v.sayOriginToo.asLine()

    override val outcomeMated = v.outcomeMated.asLine()
    override val outcomeLost = v.outcomeLost.asLine()
    override val outcomeStalemate = v.outcomeStalemate.asLine()
    override val outcomeFiftyMoves = v.outcomeFiftyMoves.asLine()
    override val outcomeGaveUp = v.outcomeGaveUp.asLine()

    override val notThat = v.notThat.asLine()
    override val gaveUpMovingOn = v.gaveUpMovingOn.asLine()
    override val puzzleSolved = v.puzzleSolved.asLine()

    override val knightIsOn = v.knightIsOn.asLine()
    override val goal = v.target.asLine()

    override fun movesLeft(moves: Int) = SpokenLine(listOf(v.movesLeftHead.clip()) + v.numberEnd(moves))

    override val notKnightMove = v.notKnightMove.asLine()
    override val pieceInTheWay = v.pieceInTheWay.asLine()
    override val squareIsAttacked = v.squareIsAttacked.asLine()
    override val alreadyThere = v.alreadyThere.asLine()

    // Jednina i množina: „in 1 move" naspram „in 3 moves".
    override fun correctInMoves(moves: Int) =
        SpokenLine(listOf(v.correctIn.clip()) + v.numberMid(moves) + movesWord(moves))

    override val shortestGoesLikeThis = v.shortestGoesLikeThis.asLine()
    override val knightFrom = v.knightFrom.asLine()
    override val toSquare = v.toMid.asLine()

    override fun inMoves(moves: Int) = SpokenLine(listOf(v.inMid.clip()) + v.numberMid(moves) + movesWord(moves))

    private fun movesWord(moves: Int) = if (moves == 1) v.moveTail.clip() else v.movesTail.clip()

    override fun walkWith(piece: PieceType) = SpokenLine(v.walkWithThe.clip(), v.piece(piece), v.from.clip())

    override fun walkIllegal(piece: PieceType) = SpokenLine(v.aHead.clip(), v.piece(piece), v.doesNotGoThere.clip())

    override val walkVisited = v.walkVisited.asLine()
    override val walkStuck = v.walkStuck.asLine()
    override val walkReplay = v.walkReplay.asLine()

    override fun retellWatch(piece: PieceType) = SpokenLine(v.watchThe.clip(), v.pieceAtEnd(piece))

    override val retellNow = v.retellNow.asLine()
    override val retellWrong = v.noItWas.asLine()

    override fun retellLeft(squares: Int) = SpokenLine(listOf(v.squaresToGoHead.clip()) + v.numberEnd(squares))

    override val walkAsRook = v.walkAsRook.asLine()
    override val walkAsBishop = v.walkAsBishop.asLine()

    override fun walkStandsOn(piece: PieceType) = SpokenLine(v.theHead.clip(), v.piece(piece), v.standsOn.clip())

    override fun walkHeld(moves: Int) = SpokenLine(listOf(v.movesSoFarHead.clip()) + v.numberEnd(moves))

    override fun reachPieceOn(piece: PieceType) = SpokenLine(v.theHead.clip(), v.piece(piece), v.isOn.clip())

    override fun reachWhichOnFile(file: Char) =
        SpokenLine(v.whichSquaresOnThe.clip(), v.file(file), v.fileTail.clip())

    override fun reachWhichOnRank(rank: Int) = SpokenLine(v.whichSquaresOnRank.clip(), v.rankInQuestion(rank))

    override val reachYouSaid = v.youSaid.asLine()
    override val reachNone = v.reachNone.asLine()
    override val reachTruthIs = v.itReaches.asLine()

    override val walkHowTo = v.walkHowTo.asLine()
    override val reachHowTo = v.reachHowTo.asLine()

    override val gameReady = v.gameReady.asLine()

    override fun wrongPieceIsOn(piece: PieceType) = SpokenLine(v.noThe.clip(), v.piece(piece), v.isOn.clip())

    override fun whereIsPiece(piece: Piece) =
        SpokenLine(v.whereIsThe.clip(), v.color(piece.color), v.pieceInQuestion(piece.type))

    override fun notThereIsOn(piece: Piece, square: Square) =
        SpokenLine(v.notThereThe.clip(), v.color(piece.color), v.piece(piece.type), v.isOn.clip(), v.square(square))

    override fun whoAttacks(piece: Piece, square: Square, count: Int) = SpokenLine(
        listOf(v.whoAttacksThe.clip(), v.color(piece.color), v.piece(piece.type), v.onMid.clip(), v.square(square)) +
            Pause(SpeechVocabulary.PAUSE_BETWEEN_SQUARES_MILLIS) + v.findHead.clip() + v.numberEnd(count)
    )

    override fun attackersAre(squares: List<Square>) = SpokenLine(listOf(v.attackersHead.clip()) + v.squares(squares))

    override val lightSquare = v.lightSquare.asLine()
    override val darkSquare = v.darkSquare.asLine()

    override fun wrongSquareIs(isLight: Boolean) = SpokenLine(v.noSquareIs.clip(), colorTail(isLight))

    override fun timeoutSquareIs(isLight: Boolean) = SpokenLine(v.timeIsUpSquareIs.clip(), colorTail(isLight))

    private fun colorTail(isLight: Boolean) = if (isLight) v.lightTail.clip() else v.darkTail.clip()

    override val allCorrect = v.allCorrect.asLine()

    override fun correctOutOf(correct: Int, total: Int) =
        SpokenLine(listOf(v.correctHead.clip()) + v.numberMid(correct) + v.outOf.clip() + v.numberEnd(total))

    override val summaryZones = v.summaryZones.asLine()

    override fun summaryResult(solved: Int, attempted: Int, mistakes: Int) = SpokenLine(
        listOf(v.solvedHead.clip()) + v.numberMid(solved) + v.outOf.clip() + v.numberEnd(attempted) +
            v.mistakesHead.clip() + v.numberEnd(mistakes)
    )

    override fun summaryXp(xp: Int) = SpokenLine(listOf(v.youEarned.clip()) + v.numberMid(xp) + v.pointsTail.clip())

    override val summaryRankUp = v.summaryRankUp.asLine()
    override val summaryAchievement = v.summaryAchievement.asLine()

    private fun SpeechVocabulary.Token.clip(): SpokenToken = SpokenToken.Clip(id, text)
}

/**
 * Rečenice za jezik.
 *
 * `when` je iscrpan namerno: jezik koji se jednom doda u [Language] ne sme da
 * prođe kompajler bez svojih rečenica — pola-jezik je gore od nijednog.
 */
fun phrasesFor(language: Language): SpeechPhrases = when (language) {
    Language.ENGLISH -> EnglishPhrases
}
