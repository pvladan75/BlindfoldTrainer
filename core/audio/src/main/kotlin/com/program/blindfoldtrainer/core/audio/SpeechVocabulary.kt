package com.program.blindfoldtrainer.core.audio

import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.audio.SpokenToken.Clip
import com.program.blindfoldtrainer.core.audio.SpokenToken.Pause

/**
 * Jedini rečnik iz kog se sastavlja sve što aplikacija izgovara.
 *
 * Svaki isečak ima `id` (fajl u `assets/speech/`), `text` (šta znači) i recept
 * po kom je snimljen: **fraza** je cela rečenica, snimljena u komadu; **isečena
 * reč** je reč ili dve isečene iz **nosive rečenice** istog oblika — polje iz
 * „Black plays bishop e5.", figura iz „Black plays knight e5." — po vremenima
 * reči koja Azure SDK prijavi. Reč snimljena sama pada kao kraj rečenice, pa se
 * nikad ne snima sama; to je Mislisha platila na vlasnikovom uhu 2.10.2026.
 *
 * **Ovaj fajl je izvor.** [manifestJson] piše `assets/speech/manifest.json`,
 * Mislishin `tools/speech_clips/render.js` iz njega snima isečke (ključ i region
 * su u Mislishinom `.env`, nikad ovde), a [SpeechVocabularyTest] drži manifest,
 * katanac koji render ostavi i svaki fajl uz ono što ovde piše — pa je tekst
 * izmenjen ovde bez novog snimanja crven test, a ne isečak koji kaže staru reč.
 *
 * Polja, figure, brojevi 0–100 i redovi nose **iste id-jeve i iste nosive
 * rečenice kao u Mislishi**, pa ih render uzima iz svog keša i ne plaća.
 */
object SpeechVocabulary {

    const val VOICE = "en-US-AndrewNeural"
    const val SAMPLE_RATE = 22050
    const val BITS_PER_SAMPLE = 16
    const val CHANNELS = 1

    /** Tišina ispred polja (D6). */
    const val PAUSE_BEFORE_SQUARE_MILLIS = 50

    /** Tišina između figura pri čitanju pozicije, i duža između strana. */
    const val PAUSE_BETWEEN_PIECES_MILLIS = 180
    const val PAUSE_BETWEEN_SIDES_MILLIS = 400

    /** Između polja u nabrajanju („a1, b2, c3"). */
    const val PAUSE_BETWEEN_SQUARES_MILLIS = 160

    /** Šta se o isečku zna pre nego što se snimi. */
    data class Token(
        val id: String,
        val text: String,
        val carrier: String,
        /** `null` za frazu; za isečenu reč prva i poslednja reč nosive rečenice, od 1. */
        val wordFrom: Int? = null,
        val wordTo: Int? = null
    ) {
        val isPhrase: Boolean get() = wordFrom == null

        init {
            require(id.matches(Regex("[a-z0-9_]+"))) { "token id must be a file name: $id" }
            require((wordFrom == null) == (wordTo == null)) { "$id: wordFrom and wordTo go together" }
            if (wordFrom != null && wordTo != null) {
                val words = wordCount(carrier)
                require(wordFrom in 1..wordTo && wordTo <= words) { "$id: words $wordFrom..$wordTo of $words" }
            }
        }
    }

    private val tokens = LinkedHashMap<String, Token>()

    private fun phrase(id: String, text: String): Token = register(Token(id, text, text))

    private fun cut(id: String, text: String, carrier: String, from: Int, to: Int = from): Token =
        register(Token(id, text, carrier, from, to))

    private fun register(token: Token): Token {
        val previous = tokens[token.id]
        if (previous != null) {
            require(previous == token) { "token ${token.id} declared twice with different recipes" }
            return previous
        }
        tokens[token.id] = token
        return token
    }

    /** Reči kako ih render broji: razmak deli, znaci interpunkcije otpadaju. */
    fun wordCount(carrier: String): Int =
        carrier.replace(Regex("[.,?!:]"), "").trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    // ---- polja, figure, boje, brojevi — ista imena i nosive rečenice kao u Mislishi ----

    private val squares: Map<Square, Token> = Square.ALL.associateWith { square ->
        cut("sq_$square", "$square", "Black plays bishop $square.", 4)
    }

    private val pieces: Map<PieceType, Token> = PieceType.entries.associateWith { type ->
        cut("piece_${type.word}", type.word, "Black plays ${type.word} e5.", 3)
    }

    /** Figura na kraju rečenice: „…but a bishop." */
    private val piecesAtEnd: Map<PieceType, Token> = PieceType.entries.associateWith { type ->
        val other = if (type == PieceType.KING) PieceType.QUEEN else PieceType.KING
        cut("piece_end_${type.word}", "${type.word}.", "On e4 there is no ${other.word} but a ${type.word}.", 9)
    }

    /** Figura na kraju pitanja: „Where is the black queen?" */
    private val piecesInQuestion: Map<PieceType, Token> = PieceType.entries.associateWith { type ->
        cut("piece_q_${type.word}", "${type.word}?", "Where is the black ${type.word}?", 5)
    }

    /** Množina: „Two of your rooks can go to e5." */
    private val piecesPlural: Map<PieceType, Token> = PieceType.entries.associateWith { type ->
        cut("pieces_${type.word}", "${type.word}s", "Two of your ${type.word}s can go to e5.", 4)
    }

    private val colors: Map<Color, Token> = mapOf(
        Color.WHITE to cut("color_white", "white", "Where is the white queen?", 4),
        Color.BLACK to cut("color_black", "black", "Where is the black queen?", 4)
    )

    /** Broj usred rečenice: „Found 3 of 5." */
    private val numbersMid: Map<Int, Token> = (0..100).associateWith { n ->
        cut("nmid_$n", "$n", "Found $n of 5.", 2)
    }

    /** Broj na kraju rečenice: „Mate in 3." */
    private val numbersEnd: Map<Int, Token> = (0..100).associateWith { n ->
        cut("n_$n", "$n", "Mate in $n.", 3)
    }

    private val hundredWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    /** „four hundred" — poeni umeju da pređu sto. */
    private val hundreds: Map<Int, Token> = (1..9).associateWith { h ->
        cut("hundreds_$h", "${hundredWords[h - 1]} hundred", "You earned ${hundredWords[h - 1]} hundred twenty points.", 3, 4)
    }

    private val thousands: Map<Int, Token> = (1..9).associateWith { t ->
        cut("thousands_$t", "${hundredWords[t - 1]} thousand", "You earned ${hundredWords[t - 1]} thousand points.", 3, 4)
    }

    /** Red na kraju pitanja: „Which squares on rank 4?" */
    private val ranksInQuestion: Map<Int, Token> = (1..8).associateWith { r ->
        cut("rank_q_$r", "$r?", "Which squares on rank $r?", 5)
    }

    /** Kolona usred pitanja: „Which squares on the e file?" */
    private val files: Map<Char, Token> = ('a'..'h').associateWith { f ->
        cut("file_$f", "$f", "Which squares on the $f file?", 5)
    }

    /** Fonetska imena kolona i reči za redove, za „ponovi": „echo five". */
    private val phoneticWords = listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel")
    private val rankWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")

    private val phoneticFiles: Map<Char, Token> = ('a'..'h').associateWith { f ->
        val i = f - 'a'
        cut("pf_$f", phoneticWords[i], "Black plays bishop ${phoneticWords[i]} ${rankWords[i]}.", 4)
    }

    private val phoneticRanks: Map<Int, Token> = (1..8).associateWith { r ->
        val i = r - 1
        cut("pr_$r", rankWords[i], "Black plays bishop ${phoneticWords[i]} ${rankWords[i]}.", 5)
    }

    // ---- delovi rečenica oko polja, figura i brojeva ----

    val onHead = cut("on_head", "On", "On e4 there is no piece.", 1)
    val thereIsNoPiece = cut("there_is_no_piece", "there is no piece", "On e4 there is no piece.", 3, 6)
    val thereIsNo = cut("there_is_no", "there is no", "On e4 there is no queen but a king.", 3, 5)
    val butA = cut("but_a", "but a", "On e4 there is no queen but a king.", 7, 8)
    val noHead = cut("no_head", "No", "No rook can go to e5.", 1)
    val canGoTo = cut("can_go_to", "can go to", "No rook can go to e5.", 3, 5)
    val twoOfYour = cut("two_of_your", "Two of your", "Two of your rooks can go to e5.", 1, 3)
    val onMid = cut("on_mid", "on", "White king on e1, white queen on d1.", 3)
    val knightIsOn = cut("knight_is_on", "The knight is on", "The knight is on b1.", 1, 4)
    val target = cut("target", "Target", "Target g7.", 1)
    val movesLeftHead = cut("moves_left_head", "Moves left:", "Moves left: 3.", 1, 2)
    val correctIn = cut("correct_in", "Correct, in", "Correct, in 4 moves.", 1, 2)
    val movesTail = cut("moves_tail", "moves.", "Correct, in 4 moves.", 4)
    val moveTail = cut("move_tail", "move.", "Correct, in 1 move.", 4)
    val knightFrom = cut("knight_from", "Knight from", "Knight from a1 to h8 in 3 moves.", 1, 2)
    val toMid = cut("to_mid", "to", "Knight from a1 to h8 in 3 moves.", 4)
    val inMid = cut("in_mid", "in", "Knight from a1 to h8 in 3 moves.", 6)
    val walkWithThe = cut("walk_with_the", "Walk with the", "Walk with the rook from a1.", 1, 3)
    val from = cut("from", "from", "Walk with the rook from a1.", 5)
    val aHead = cut("a_head", "A", "A rook does not go there.", 1)
    val doesNotGoThere = cut("does_not_go_there", "does not go there.", "A rook does not go there.", 3, 6)
    val watchThe = cut("watch_the", "Watch the", "Watch the rook.", 1, 2)
    val noItWas = cut("no_it_was", "No. It was", "No. It was a1, b2, c3.", 1, 3)
    val squaresToGoHead = cut("squares_to_go_head", "Squares to go:", "Squares to go: 3.", 1, 3)
    val theHead = cut("the_head", "The", "The bishop is on c1.", 1)
    val standsOn = cut("stands_on", "stands on", "The rook stands on a1.", 3, 4)
    val movesSoFarHead = cut("moves_so_far_head", "Moves so far:", "Moves so far: 3.", 1, 3)
    val isOn = cut("is_on", "is on", "Not there. The black queen is on d8.", 6, 7)
    val whichSquaresOnThe = cut("which_squares_on_the", "Which squares on the", "Which squares on the e file?", 1, 4)
    val fileTail = cut("file_tail", "file?", "Which squares on the e file?", 6)
    val whichSquaresOnRank = cut("which_squares_on_rank", "Which squares on rank", "Which squares on rank 4?", 1, 4)
    val youSaid = cut("you_said", "You said", "You said a1, b2.", 1, 2)
    val itReaches = cut("it_reaches", "It reaches", "It reaches a1, b2.", 1, 2)
    val noThe = cut("no_the", "No. The", "No. The rook is on a1.", 1, 2)
    val whereIsThe = cut("where_is_the", "Where is the", "Where is the black queen?", 1, 3)
    val notThereThe = cut("not_there_the", "Not there. The", "Not there. The black queen is on d8.", 1, 3)
    val whoAttacksThe = cut("who_attacks_the", "Who attacks the", "Who attacks the black knight on e5?", 1, 3)
    val findHead = cut("find_head", "Find", "Find 2.", 1)
    val attackersHead = cut("attackers_head", "Attackers:", "Attackers: a1, b2.", 1)
    val noSquareIs = cut("no_square_is", "No, the square is", "No, the square is light.", 1, 4)
    val lightTail = cut("light_tail", "light.", "No, the square is light.", 5)
    val darkTail = cut("dark_tail", "dark.", "No, the square is dark.", 5)
    val timeIsUpSquareIs = cut("time_is_up_square_is", "Time is up, the square is", "Time is up, the square is light.", 1, 6)
    val correctHead = cut("correct_head", "Correct", "Correct 3 out of 5.", 1)
    val endOfSessionSolved = cut("end_of_session_solved", "End of session. Solved", "End of session. Solved 3 out of 5.", 1, 4)
    val endOfSessionCorrect = cut("end_of_session_correct", "End of session. Correct", "End of session. Correct 3 out of 5.", 1, 4)
    val outOf = cut("out_of", "out of", "End of session. Solved 3 out of 5.", 6, 7)
    val solvedHead = cut("solved_head", "Solved", "Solved 4 out of 6. Mistakes 2.", 1)
    val mistakesHead = cut("mistakes_head", "Mistakes", "Solved 4 out of 6. Mistakes 2.", 6)
    val youEarned = cut("you_earned", "You earned", "You earned four hundred twenty points.", 1, 2)
    val pointsTail = cut("points_tail", "points.", "You earned four hundred twenty points.", 6)

    // ---- cele rečenice ----

    val confirmGiveUp = phrase("confirm_give_up", "Touch again to give up.")
    val confirmStop = phrase("confirm_stop", "Touch again to stop.")
    val correct = phrase("correct", "Correct.")
    val nothingToUndo = phrase("nothing_to_undo", "There is nothing to undo.")
    val undone = phrase("undone", "Move taken back.")
    val movingToNextPosition = phrase("moving_to_next_position", "Moving on to the next position.")
    val sayOriginToo = phrase("say_origin_too", "Say the starting square as well.")
    val outcomeMated = phrase("outcome_mated", "Checkmate! The position is finished off.")
    val outcomeLost = phrase("outcome_lost", "You are checkmated, in a winning position.")
    val outcomeStalemate = phrase("outcome_stalemate", "Stalemate, a won position thrown away.")
    val outcomeFiftyMoves = phrase("outcome_fifty_moves", "Fifty moves without progress.")
    val outcomeGaveUp = phrase("outcome_gave_up", "The pieces are revealed.")
    val notThat = phrase("not_that", "Not that one.")
    val gaveUpMovingOn = phrase("gave_up_moving_on", "You gave up. Moving on to the next one.")
    val puzzleSolved = phrase("puzzle_solved", "Puzzle solved.")
    val notKnightMove = phrase("not_knight_move", "That is not a knight move.")
    val pieceInTheWay = phrase("piece_in_the_way", "There is a piece there.")
    val squareIsAttacked = phrase("square_is_attacked", "That square is attacked.")
    val alreadyThere = phrase("already_there", "The knight is already there.")
    val shortestGoesLikeThis = phrase("shortest_goes_like_this", "That did not work. The shortest way goes like this:")
    val walkVisited = phrase("walk_visited", "You have been there already.")
    val walkStuck = phrase("walk_stuck", "No square left. The walk ends here.")
    val walkReplay = phrase("walk_replay", "Here is the walk you made.")
    val retellNow = phrase("retell_now", "Now say the path, from the first square.")
    val walkAsRook = phrase("walk_as_rook", "Now as a rook.")
    val walkAsBishop = phrase("walk_as_bishop", "Now as a bishop.")
    val reachNone = phrase("reach_none", "None.")
    val walkHowTo = phrase("walk_how_to", "Touch the microphone, then say one square.")
    val reachHowTo = phrase("reach_how_to", "Touch the microphone for each square, then touch done.")
    val gameReady = phrase("game_ready", "The game is ready. Touch for the first move.")
    val lightSquare = phrase("light_square", "Light.")
    val darkSquare = phrase("dark_square", "Dark.")
    val allCorrect = phrase("all_correct", "All correct.")
    val summaryZones = phrase("summary_zones", "Again at the top, result in the middle, menu at the bottom.")
    val summaryRankUp = phrase("summary_rank_up", "New rank.")
    val summaryAchievement = phrase("summary_achievement", "New achievement.")

    // ---- šta rečnik ume da sastavi ----

    /** Svi isečci, redom kojim su upisani. */
    val all: List<Token> get() = tokens.values.toList()

    fun tokenOf(id: String): Token? = tokens[id]

    fun square(square: Square): Clip = squares.getValue(square).clip(PAUSE_BEFORE_SQUARE_MILLIS)

    /** „echo five" — polje za „ponovi", fonetski. */
    fun squarePhonetic(square: Square): List<SpokenToken> = listOf(
        phoneticFiles.getValue(square.file).clip(PAUSE_BEFORE_SQUARE_MILLIS),
        phoneticRanks.getValue(square.rank).clip()
    )

    fun piece(type: PieceType): Clip = pieces.getValue(type).clip()
    fun pieceAtEnd(type: PieceType): Clip = piecesAtEnd.getValue(type).clip()
    fun pieceInQuestion(type: PieceType): Clip = piecesInQuestion.getValue(type).clip()
    fun piecesPlural(type: PieceType): Clip = piecesPlural.getValue(type).clip()
    fun color(color: Color): Clip = colors.getValue(color).clip()
    fun file(file: Char): Clip = files.getValue(file).clip()
    fun rankInQuestion(rank: Int): Clip = ranksInQuestion.getValue(rank).clip()

    /** Broj usred rečenice. Preko sto se slaže iz hiljada, stotina i ostatka. */
    fun numberMid(n: Int): List<SpokenToken> = compose(n, numbersMid)

    /** Broj na kraju rečenice. */
    fun numberEnd(n: Int): List<SpokenToken> = compose(n, numbersEnd)

    private fun compose(n: Int, lastPart: Map<Int, Token>): List<SpokenToken> {
        require(n >= 0) { "a spoken number is not negative: $n" }
        if (n <= 100) return listOf(lastPart.getValue(n).clip())
        require(n < 10_000) { "no clips for $n" }
        val parts = mutableListOf<SpokenToken>()
        val t = n / 1000
        val h = n % 1000 / 100
        val rest = n % 100
        if (t > 0) parts += thousands.getValue(t).clip()
        if (h > 0) parts += hundreds.getValue(h).clip()
        if (rest > 0 || parts.isEmpty()) parts += lastPart.getValue(rest).clip()
        return parts
    }

    /** Polja u nabrajanju: „a1, b2, c3". */
    fun squares(squares: List<Square>): List<SpokenToken> = buildList {
        squares.forEachIndexed { index, square ->
            if (index > 0) add(Pause(PAUSE_BETWEEN_SQUARES_MILLIS))
            add(square(square))
        }
    }

    fun move(move: Move): List<SpokenToken> = listOf(square(move.from), Pause(PAUSE_BETWEEN_SQUARES_MILLIS), square(move.to))

    fun movePhonetic(move: Move): List<SpokenToken> =
        squarePhonetic(move.from) + Pause(PAUSE_BETWEEN_SQUARES_MILLIS) + squarePhonetic(move.to)

    /**
     * Cela pozicija: „white king on e2, white queen on e5. black king on h6".
     *
     * Beli pa crni, a unutar boje po vrednosti figure — kralj prvi, jer se oko
     * njega gradi slika. Redosled je uvek isti da bi se pozicija pamtila kao niz,
     * a ne kao skup. Tišina deli figure, duža deli strane.
     */
    fun board(board: Board): List<SpokenToken> {
        fun side(color: Color): List<Pair<Square, Piece>> = board.occupied()
            .filter { (_, piece) -> piece.color == color }
            .sortedWith(compareBy({ NARRATION_ORDER.indexOf(it.second.type) }, { it.first.index }))

        val white = side(Color.WHITE)
        val black = side(Color.BLACK)
        return buildList {
            fun add(entries: List<Pair<Square, Piece>>) {
                entries.forEachIndexed { index, (square, piece) ->
                    if (index > 0) add(Pause(PAUSE_BETWEEN_PIECES_MILLIS))
                    add(color(piece.color))
                    add(piece(piece.type))
                    add(onMid.clip())
                    add(square(square))
                }
            }
            add(white)
            if (white.isNotEmpty() && black.isNotEmpty()) add(Pause(PAUSE_BETWEEN_SIDES_MILLIS))
            add(black)
        }
    }

    private val NARRATION_ORDER = listOf(
        PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN
    )

    // ---- manifest ----

    /**
     * `manifest.json` kakav render čita: glas, format i isečci redom. Isti
     * oblik kao Mislishin, da isti alat čita oba.
     */
    fun manifestJson(): String = buildString {
        append("{\n")
        append("  \"voice\": \"").append(VOICE).append("\",\n")
        append("  \"format\": { \"sampleRate\": ").append(SAMPLE_RATE)
            .append(", \"bitsPerSample\": ").append(BITS_PER_SAMPLE)
            .append(", \"channels\": ").append(CHANNELS).append(" },\n")
        append("  \"tokens\": [\n")
        all.forEachIndexed { index, token ->
            append("    { \"id\": ").append(json(token.id))
            append(", \"kind\": ").append(if (token.isPhrase) "\"phrase\"" else "\"cut\"")
            append(", \"text\": ").append(json(token.text))
            append(", \"carrier\": ").append(json(token.carrier))
            if (!token.isPhrase) {
                append(", \"wordFrom\": ").append(token.wordFrom).append(", \"wordTo\": ").append(token.wordTo)
            }
            append(" }")
            if (index < all.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n}\n")
    }

    private fun json(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun Token.clip(pauseBefore: Int = 0): Clip = Clip(id, text, pauseBefore)

    private val PieceType.word: String
        get() = when (this) {
            PieceType.KING -> "king"
            PieceType.QUEEN -> "queen"
            PieceType.ROOK -> "rook"
            PieceType.BISHOP -> "bishop"
            PieceType.KNIGHT -> "knight"
            PieceType.PAWN -> "pawn"
        }

    /** Isečak jedne cele rečenice, za fraze koje su samo to. */
    fun Token.asLine(): SpokenLine = SpokenLine(clip())
}
