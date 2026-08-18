package com.program.blindfoldtrainer.feature.endgame

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.SpeechVoice
import com.program.blindfoldtrainer.core.audio.SpokenInput
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Move
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Position
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.board.PieceVisibility
import com.program.blindfoldtrainer.core.engine.ChessEngine
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.SettingsRepository
import com.program.blindfoldtrainer.core.moduleapi.userReason
import com.program.blindfoldtrainer.feature.endgame.data.EndgameCatalog
import com.program.blindfoldtrainer.feature.endgame.data.EndgamePuzzle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kako se završila jedna pozicija. */
enum class EndgameOutcome {
    /** Još se igra. */
    IN_PROGRESS,

    /** Mat — pozicija je privedena kraju. */
    MATED,

    /** Matiran je sam igrač. U dobijenoj poziciji redak, ali moguć ishod. */
    LOST,

    /** Pat: pozicija je bila dobijena, a prokockana je u remi. */
    STALEMATE,

    /** Pravilo 50 poteza — mat nije stigao na vreme. */
    FIFTY_MOVES,

    /** Korisnik je odustao i otkrio figure. */
    GAVE_UP
}

data class EndgameUiState(
    val position: Position = Position.STARTING,
    val visibility: PieceVisibility = PieceVisibility.None,
    /**
     * Korisnik još gleda početnu poziciju. Zasebno od [visibility] jer se ona
     * kratko menja i pri svakom potezu — na njoj se ne sme zasnivati UI.
     */
    val isMemorizing: Boolean = true,
    val selectedSquare: Square? = null,
    val lastMove: Move? = null,
    val errorSquare: Square? = null,
    val puzzleNumber: Int = 0,
    val puzzleCount: Int = 0,
    val mistakes: Int = 0,
    val outcome: EndgameOutcome = EndgameOutcome.IN_PROGRESS,
    val statusMessage: String = "",
    val evaluationLabel: String = "",
    val isEngineThinking: Boolean = false,
    val elapsedMillis: Long = 0,
    val isLoading: Boolean = true,
    val infoMessage: String? = null,
    val isFinished: Boolean = false
) {
    val isPlayerTurn: Boolean
        get() = outcome == EndgameOutcome.IN_PROGRESS && !isEngineThinking && !isMemorizing
}

private data class Setup(val puzzleCount: Int, val engineDepth: Int)

/** Stanje partije pre jednog tvog poteza. */
private data class UndoPoint(
    val position: Position,
    val lastMove: Move?,
    val statusMessage: String
)

/** Ime figure za izgovor. Rod se ne menja jer stoji uz „može" i „ne može". */
/**
 * Ishod kao izgovorena rečenica.
 *
 * Odvojeno od `messageFor`, koje ostaje za ekran: ekran prati jezik aplikacije,
 * govor prati izabrani glas, i to su dve različite ose.
 */
private fun SpeechVoice.spokenOutcome(outcome: EndgameOutcome): String = when (outcome) {
    EndgameOutcome.MATED -> outcomeMated
    EndgameOutcome.LOST -> outcomeLost
    EndgameOutcome.STALEMATE -> outcomeStalemate
    EndgameOutcome.FIFTY_MOVES -> outcomeFiftyMoves
    EndgameOutcome.GAVE_UP -> outcomeGaveUp
    EndgameOutcome.IN_PROGRESS -> ""
}

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(puzzleCount = 3, engineDepth = 10)
    Difficulty.MEDIUM -> Setup(puzzleCount = 3, engineDepth = 12)
    Difficulty.HARD -> Setup(puzzleCount = 3, engineDepth = 14)
}

private const val MOVE_FLASH_MILLIS = 700L
private const val OUTCOME_PAUSE_MILLIS = 2_000L
private const val TAG = "EndgameViewModel"

@HiltViewModel
class EndgameViewModel @Inject constructor(
    private val catalog: EndgameCatalog,
    private val engine: ChessEngine,
    private val speaker: Speaker,
    private val voiceInput: VoiceInput,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    /** Prekid slušanja na dodir — bez toga se upaljen mikrofon ne može ugasiti. */
    fun onVoiceStop() = voiceInput.stop()

    /**
     * Poništava tvoj potez i odgovor motora.
     *
     * Postoji zbog glasovnog unosa: ako te pogrešno razume, odigra se potez koji
     * nisi rekao, a bez ekrana se to ni ne vidi. **Ne broji se kao greška** —
     * nije tvoja.
     */
    fun onUndo() {
        val point = undoStack.removeLastOrNull()
        if (point == null) {
            speaker.say { nothingToUndo }
            return
        }

        // Motor možda još misli, a možda je već najavljen ishod i zakazana
        // sledeća pozicija — oboje se prekida.
        engineJob?.cancel()
        outcomeJob?.cancel()
        voiceInput.stop()

        _uiState.update {
            it.copy(
                position = point.position,
                lastMove = point.lastMove,
                statusMessage = point.statusMessage,
                selectedSquare = null,
                errorSquare = null,
                outcome = EndgameOutcome.IN_PROGRESS,
                isEngineThinking = false,
                visibility = PieceVisibility.None
            )
        }

        speaker.say { undone }
        // Posle poništavanja se pozicija ponovo čita: to je ispravka, a ne
        // pomoć, pa se i ne broji.
        speaker.say(point.position.board, interrupt = false)
    }

    /** Prvi dodir na zonu za odustajanje — traži potvrdu, jer je nepovratno. */
    fun onGiveUpArmed() = speaker.say { confirmGiveUp }

    /** Ponavlja poslednje izgovoreno — nisi dočuo, a ne da ti se slika raspala. */
    fun onRepeatLast() = speaker.repeat()

    /**
     * Čita **trenutnu** poziciju. Broji se, jer znači da se slika u glavi
     * raspala — ali se ne ograničava: kome ide teže, taj sme da pita koliko god
     * puta treba. Broj stoji u sažetku kao merilo napretka, ne kao prekor.
     */
    fun onReadPosition() {
        positionReads++
        speaker.say(_uiState.value.position.board)
    }

    private var settings: Settings = Settings.DEFAULT

    private val _isEyesFree = MutableStateFlow(Settings.DEFAULT.eyesFree)

    /** Da li se vežba bez gledanja u ekran; bira se u Podešavanjima. */
    val isEyesFree: StateFlow<Boolean> = _isEyesFree.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                settings = it
                _isEyesFree.value = it.eyesFree
            }
        }
    }

    /**
     * Prima izgovor u bilo kom obliku koji Završnica ume:
     *
     * - **polje** („e four") — bira figuru, pa sledeće polje dovršava potez;
     * - **ceo potez** („e four e two") — oba polja iz jednog daha;
     * - **figura i odredište** („rook e two") — polazište traži sama.
     *
     * Ne bira se između njih i nema režima: rečnik je jedan, a šta je rečeno
     * vidi se tek pri čitanju.
     *
     * Uz uključeno „slušaj ceo potez" drugi korak ne traži nov pritisak:
     * **mikrofon prosto ostaje upaljen** dok se ne prepozna i odredišno polje.
     * Ranije se gasio pa palio posle 250 ms i tu bi umeo da ne krene, jer se
     * prethodni snimač još zatvarao.
     */
    fun onVoiceInput() {
        voiceInput.listen { spoken -> onSpokenInput(spoken) }
    }

    /** Vraća `true` ako se sluša dalje. */
    private fun onSpokenInput(spoken: SpokenInput): Boolean = when (spoken) {
        is SpokenInput.Full -> {
            onSquareClicked(spoken.square)
            settings.listenWholeMove && _uiState.value.selectedSquare != null
        }

        is SpokenInput.Move -> {
            onSpokenMove(spoken.from, spoken.to, spoken.piece)
            false
        }

        is SpokenInput.PieceMove -> {
            onSpokenPieceMove(spoken.piece, spoken.to)
            false
        }

        else -> false
    }

    /**
     * Potez zadat sa oba polja. Ako je uz njih **imenovana** i figura, ime se
     * proverava i potez se odbija kad se ne slaže sa tablom.
     *
     * Ime nije ukras. „Top ce tri ce dva" dok na c3 stoji dama znači da slika u
     * glavi nije tačna; odigrati taj potez bi zabludu **potvrdilo**, jer polja
     * jesu ispravna pa bi sve zvučalo kao da je prošlo. Zato se kaže šta tamo
     * zaista stoji i ne dira se tabla.
     *
     * Broji se kao promašaj, isto kao nemoguć potez: i jedno i drugo je pogrešna
     * predstava o poziciji, a ne omaška u kucanju.
     */
    private fun onSpokenMove(from: Square, to: Square, named: PieceType?) {
        val state = _uiState.value
        if (!state.isPlayerTurn) return

        val actual = state.position.board[from]
        if (named != null && actual?.type != named) {
            speaker.say { onSquare }
            speaker.say(from, interrupt = false)
            speaker.say(interrupt = false) {
                if (actual == null) {
                    noPieceThere
                } else {
                    pieceMismatch(nameOf(named), nameOf(actual.type))
                }
            }
            onIllegalMove(from)
            return
        }

        // Ista dva dodira, samo iz jednog izgovora.
        onSquareClicked(from)
        onSquareClicked(to)
    }

    /**
     * „Top na e dva" — polazište se traži iz legalnih poteza.
     *
     * Kad na isto polje mogu dve iste figure, odgovor nije jedan i **ne pogađa
     * se**: kaže se da su dve i traži se polazište. To nije greška korisnika
     * nego nedorečenost, pa se i ne broji kao promašaj — a usput je i korisna
     * vest, jer dve figure koje gađaju isto polje su baš ono što naslepo izmiče.
     */
    private fun onSpokenPieceMove(type: PieceType, to: Square) {
        val state = _uiState.value
        if (!state.isPlayerTurn) return

        val candidates = state.position.legalMoves()
            .filter { it.to == to && state.position.board[it.from]?.type == type }
        val origins = candidates.map { it.from }.distinct()

        when {
            origins.isEmpty() -> {
                speaker.say { noneCanReach(nameOf(type)) }
                speaker.say(to, interrupt = false)
                onIllegalMove(to)
            }

            origins.size > 1 -> {
                speaker.say { twoCanReach(nameOf(type)) }
                speaker.say(to, interrupt = false)
                speaker.say(interrupt = false) { sayOriginToo }
            }

            // Promocija se podrazumeva u damu — kao i pri dodiru.
            else -> applyPlayerMove(
                candidates.firstOrNull { it.promotion == PieceType.QUEEN } ?: candidates.first()
            )
        }
    }

    private val _uiState = MutableStateFlow(EndgameUiState())
    val uiState: StateFlow<EndgameUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var puzzles: List<EndgamePuzzle> = emptyList()
    private var playerColor: Color = Color.WHITE
    private var solvedCount = 0
    private var positionReads = 0

    /**
     * Stanje pre svakog tvog poteza, za poništavanje.
     *
     * Pamti se **pre** poteza, a poništavanje vraća i tvoj potez i odgovor
     * motora: tvoj potez je taj odgovor i izazvao, pa bi vraćanje samo jednog
     * ostavilo poziciju koja u partiji nije ni postojala.
     */
    private val undoStack = ArrayDeque<UndoPoint>()
    private var startedAtMillis = 0L
    private var timerJob: Job? = null
    private var engineJob: Job? = null
    private var outcomeJob: Job? = null
    private var isStarted = false

    fun startOnce(difficulty: Difficulty) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty)

        viewModelScope.launch {
            // Motor se podiže dok korisnik još gleda prvu poziciju, da se
            // učitavanje NNUE mreže ne oseti kao zastoj usred partije.
            launch { engine.start() }

            // Prvo podešavanje se sačeka: bez toga bi prva pozicija mogla da se
            // učita pre nego što se sazna da se vežba bez ekrana, pa bi umesto
            // čitanja krenula faza pamćenja koju bez ekrana nije čime završiti.
            settings = settingsRepository.settings.first()
            _isEyesFree.value = settings.eyesFree

            val loaded = runCatching { catalog.puzzles(difficulty) }
            val failure = loaded.exceptionOrNull()
            // runCatching hvata i otkazivanje, a ono nije greška u učitavanju.
            if (failure is CancellationException) throw failure
            if (failure != null) {
                Log.e(TAG, "Zagonetke za $difficulty nisu učitane", failure)
            }

            val available = loaded.getOrDefault(emptyList())
            if (available.isEmpty()) {
                // Razlog ide i na ekran, ne samo u log: bez uređaja na kablu je
                // poruka jedini trag zašto modul nema nijednu poziciju.
                val reason = failure?.let { "\n\n${it.userReason()}" }.orEmpty()
                _uiState.update {
                    it.copy(isLoading = false, infoMessage = "Nema pozicija za ovu težinu.$reason")
                }
                return@launch
            }

            puzzles = available.shuffled().take(setup.puzzleCount)
            startedAtMillis = System.currentTimeMillis()
            startTimer()
            loadPuzzle(0)
        }
    }

    private fun loadPuzzle(index: Int) {
        voiceInput.stop()
        undoStack.clear()
        val puzzle = puzzles[index]
        val position = Position.fromFen(puzzle.fen)

        if (position == null) {
            if (index + 1 >= puzzles.size) finishSession() else loadPuzzle(index + 1)
            return
        }

        playerColor = position.sideToMove

        // Pozicija se i pročita, ne samo prikaže: bez toga se modul ne može
        // odraditi zatvorenih očiju. Čeka svoj red, da ne preseče izgovor
        // ishoda prethodne pozicije.
        speaker.say(position.board, interrupt = false)

        _uiState.update {
            it.copy(
                position = position,
                // Prvo se pozicija vidi — treba je zapamtiti pre nego što se ugasi.
                // Bez ekrana te faze nema: čitanje pozicije **jeste** pamćenje, a
                // dugmeta „zapamtio sam" nema, pa bi se u njoj zaglavilo.
                visibility = if (settings.eyesFree) PieceVisibility.None else PieceVisibility.All,
                isMemorizing = !settings.eyesFree,
                selectedSquare = null,
                lastMove = null,
                errorSquare = null,
                puzzleNumber = index + 1,
                puzzleCount = puzzles.size,
                outcome = EndgameOutcome.IN_PROGRESS,
                statusMessage = if (settings.eyesFree) "Ti si na potezu" else "Zapamti poziciju, pa igraj",
                evaluationLabel = puzzle.evaluation,
                isLoading = false,
                infoMessage = null
            )
        }
    }

    /** Korisnik je zapamtio poziciju — tabla se gasi i partija počinje. */
    fun onHidePieces() {
        _uiState.update {
            it.copy(
                visibility = PieceVisibility.None,
                isMemorizing = false,
                statusMessage = "Ti si na potezu"
            )
        }
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (!state.isPlayerTurn) return

        val selected = state.selectedSquare
        if (selected == null) {
            // Bira se figura. Naslepo se lako promaši prazno polje —
            // tada se prosto ništa ne dešava.
            val piece = state.position.board[square]
            if (piece != null && piece.color == playerColor) {
                _uiState.update { it.copy(selectedSquare = square, errorSquare = null) }

                // Bez ekrana je izbor figure nevidljiv, a ćutanje ovde znači
                // promašaj — pa se izabrano polje potvrđuje naglas.
                if (settings.eyesFree) speaker.say(square)
            }
            return
        }

        if (square == selected) {
            _uiState.update { it.copy(selectedSquare = null) }
            return
        }

        val move = findLegalMove(state.position, selected, square)
        if (move == null) {
            onIllegalMove(square)
            return
        }

        applyPlayerMove(move)
    }

    /**
     * Traži legalan potez sa polja na polje. Promocija se podrazumeva u damu —
     * u ovim pozicijama nema situacije gde bi nešto drugo bilo bolje.
     */
    private fun findLegalMove(position: Position, from: Square, to: Square): Move? {
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        return candidates.firstOrNull { it.promotion == PieceType.QUEEN }
            ?: candidates.firstOrNull()
    }

    private fun onIllegalMove(square: Square) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    errorSquare = square,
                    selectedSquare = null,
                    mistakes = it.mistakes + 1,
                    statusMessage = "Taj potez nije moguć"
                )
            }
            delay(600)
            _uiState.update {
                it.copy(errorSquare = null, statusMessage = "Ti si na potezu")
            }
        }
    }

    private fun applyPlayerMove(move: Move) {
        val before = _uiState.value
        undoStack.addLast(
            UndoPoint(
                position = before.position,
                lastMove = before.lastMove,
                statusMessage = before.statusMessage
            )
        )

        // Izgovara se šta je odigrano, **sa figurom**: bez ekrana je to jedina
        // potvrda da je prepoznato ono što je i rečeno, a sama polja ne kažu
        // šta se pomerilo.
        val moving = before.position.board[move.from]?.type
        if (moving != null) speaker.say(moving, move) else speaker.say(move)

        val next = _uiState.value.position.applyMove(move)
        _uiState.update {
            it.copy(
                position = next,
                selectedSquare = null,
                lastMove = move,
                errorSquare = null,
                visibility = PieceVisibility.only(move.to)
            )
        }

        viewModelScope.launch {
            delay(MOVE_FLASH_MILLIS)
            _uiState.update { it.copy(visibility = PieceVisibility.None) }

            if (checkOutcome(next)) return@launch
            playEngineReply(next)
        }
    }

    private fun playEngineReply(position: Position) {
        engineJob?.cancel()
        engineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isEngineThinking = true, statusMessage = "Protivnik razmišlja…")
            }

            val reply = engine.bestMove(position, setup.engineDepth)
                // Ako motor zataji, biramo bilo koji legalan potez da partija
                // ne stane — bolje slabija odbrana nego zamrznuta tabla.
                ?: position.legalMoves().randomOrNull()

            if (reply == null) {
                _uiState.update { it.copy(isEngineThinking = false) }
                checkOutcome(position)
                return@launch
            }

            val after = position.applyMove(reply)
            // Čeka svoj red: motor ume da odgovori pre nego što se dovrši
            // izgovor tvog poteza, pa bi ga inače presekao.
            val moving = position.board[reply.from]?.type
            if (moving != null) {
                speaker.say(moving, reply, interrupt = false)
            } else {
                speaker.say(reply, interrupt = false)
            }

            _uiState.update {
                it.copy(
                    position = after,
                    lastMove = reply,
                    isEngineThinking = false,
                    visibility = PieceVisibility.only(reply.to),
                    statusMessage = "$reply"
                )
            }
            delay(MOVE_FLASH_MILLIS)
            _uiState.update { it.copy(visibility = PieceVisibility.None) }

            if (!checkOutcome(after)) {
                _uiState.update { it.copy(statusMessage = "Ti si na potezu") }
            }
        }
    }

    /** Vraća `true` ako je pozicija završena. */
    private fun checkOutcome(position: Position): Boolean {
        val outcome = when {
            // Matiran je onaj ko je na potezu — otud provera čiji je red.
            position.isCheckmate ->
                if (position.sideToMove != playerColor) EndgameOutcome.MATED
                else EndgameOutcome.LOST
            position.isStalemate -> EndgameOutcome.STALEMATE
            position.isDrawByFiftyMoveRule -> EndgameOutcome.FIFTY_MOVES
            else -> return false
        }

        if (outcome == EndgameOutcome.MATED) solvedCount++

        val message = messageFor(outcome)
        // Ekran i govor idu odvojeno: poruka prati jezik aplikacije, izgovor
        // prati izabrani glas.
        speaker.say(interrupt = false) { spokenOutcome(outcome) }

        _uiState.update {
            it.copy(
                outcome = outcome,
                visibility = PieceVisibility.All,
                isEngineThinking = false,
                statusMessage = message
            )
        }

        outcomeJob?.cancel()
        outcomeJob = viewModelScope.launch {
            delay(OUTCOME_PAUSE_MILLIS)
            onNextPuzzle()
        }
        return true
    }

    private fun messageFor(outcome: EndgameOutcome) = when (outcome) {
        EndgameOutcome.MATED -> "Mat! Pozicija je privedena kraju."
        EndgameOutcome.LOST -> "Matiran si — u dobijenoj poziciji."
        EndgameOutcome.STALEMATE -> "Pat — dobijena pozicija je prokockana u remi."
        EndgameOutcome.FIFTY_MOVES -> "Pedeset poteza bez napretka."
        EndgameOutcome.GAVE_UP -> "Figure su otkrivene."
        EndgameOutcome.IN_PROGRESS -> ""
    }

    /** Odustajanje — pozicija se otkrije i ne broji se kao rešena. */
    fun onGiveUp() {
        engineJob?.cancel()
        speaker.stop()
        // Mikrofon ne sme da ostane upaljen kad se od korisnika više ništa ne traži.
        voiceInput.stop()
        speaker.say { outcomeGaveUp }

        _uiState.update {
            it.copy(
                outcome = EndgameOutcome.GAVE_UP,
                visibility = PieceVisibility.All,
                isEngineThinking = false,
                statusMessage = messageFor(EndgameOutcome.GAVE_UP)
            )
        }

        if (!settings.eyesFree) return

        // Bez ekrana nema zone „sledeća pozicija", a otkrivene figure se ionako
        // ne vide — pa bi vežba posle odustajanja stala zauvek.
        speaker.say(interrupt = false) { movingToNextPosition }
        outcomeJob?.cancel()
        outcomeJob = viewModelScope.launch {
            delay(OUTCOME_PAUSE_MILLIS)
            onNextPuzzle()
        }
    }

    fun onNextPuzzle() {
        val next = _uiState.value.puzzleNumber
        if (next >= puzzles.size) finishSession() else loadPuzzle(next)
    }

    private fun finishSession() {
        timerJob?.cancel()
        engineJob?.cancel()
        voiceInput.stop()

        // Kraj se izgovara: bez ekrana se sažetak ne vidi, pa bi sesija prosto
        // utihnula.
        val state = _uiState.value
        speaker.say(interrupt = false) { sessionEndSolved(solvedCount, state.puzzleNumber) }
        _uiState.update { it.copy(isFinished = true) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { it.copy(elapsedMillis = it.elapsedMillis + 1_000) }
            }
        }
    }

    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.ENDGAME,
            difficulty = difficulty,
            attempted = state.puzzleNumber,
            solved = solvedCount,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished,
            // Prečka na kojoj je sesija stvarno odrađena. Zasad su zauzeti samo
            // krajevi lestvice — modul još ne prima porudžbinu, nego čita
            // podešavanje, ali profil od sada zna koliko uspeh vredi.
            support = if (_isEyesFree.value) Support.NONE else Support.FULL,
            taskId = ENDGAME_PLAY_OUT.id,
            bySkill = mapOf(
                ENDGAME_PLAY_OUT.measures to SkillTally(
                    attempted = state.puzzleNumber,
                    solved = solvedCount,
                    // Vreme je deo mere: tačno a sporo znači da veština
                    // još nije automatska, pa se na njoj ne može graditi dalje.
                    millis = System.currentTimeMillis() - startedAtMillis
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        engineJob?.cancel()
        engine.stopSearch()
        speaker.stop()
        voiceInput.stop()
    }
}

/**
 * Dobijena pozicija se privodi kraju bez table, protiv motora koji se brani.
 *
 * Meri **ažuriranje**, jer se posle svakog para poteza slika mora obnoviti; uz
 * njega idu držanje, računanje i oporavak — ovo je jedini zadatak u aplikaciji
 * koji sve četiri traži odjednom, i zato je najbliži pravoj partiji.
 */
internal val ENDGAME_PLAY_OUT = TaskSpec(
    id = "play_out",
    skills = listOf(
        Skill.POSITION_UPDATE,
        Skill.POSITION_HOLD,
        Skill.CALCULATION,
        Skill.RECOVERY
    ),
    supports = listOf(Support.FULL, Support.NONE)
)
