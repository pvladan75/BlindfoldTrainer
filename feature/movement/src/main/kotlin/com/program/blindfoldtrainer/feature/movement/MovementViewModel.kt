package com.program.blindfoldtrainer.feature.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.audio.VoiceInput
import com.program.blindfoldtrainer.core.audio.VoiceState
import com.program.blindfoldtrainer.core.audio.listenForSquare
import com.program.blindfoldtrainer.core.chess.EmptyBoard
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.random.Random

/**
 * Presek zraka figure i jedne linije — pitanje zadatka „Domet na liniji".
 *
 * Linija je kolona ili red, jer je isto pitanje i isti posao: iz dometa se mora
 * izdvojiti ono što pada na zadatu liniju.
 */
data class Line(val isFile: Boolean, val index: Int) {
    /** 'a'..'h' za kolonu. */
    val fileLabel: String get() = ('a' + index).toString()

    /** 1..8 za red. */
    val rankLabel: Int get() = index + 1
}

/** Koju vrstu zadatka modul upravo radi. */
enum class MovementTask { REACH, WALK, KNIGHT_WALK }

/**
 * Odrađena šetnja, prikazana natrag — polje po polje, uz izgovaranje.
 *
 * **Ovo nije oslonac nego odgovor.** Oslonac je koliko slike aplikacija drži
 * umesto tebe *dok radiš*; ovo se dešava kad je šetnja već gotova i ne pomaže
 * pri njoj ni najmanje. Da je vezano za oslonac, čovek bi prikaz **gubio** kako
 * napreduje — a upravo je prikaz mesto na kom se vidi šta se držalo.
 *
 * Isto pravilo po kom Geometrija posle odgovora pokaže polje: test kaže da li
 * si pogodio, vežba pokaže istinu. Šetnja je dotle govorila samo broj.
 *
 * Nema strelica jer ih tabla ne ume da crta — a i ne trebaju: prikaz **korača**,
 * pa se redosled vidi iz samog toka, dok potrošena polja ostaju obojena i iz
 * njih se vidi oblik.
 */
data class Replay(
    val piece: PieceType,
    /** Cela putanja, počev od polaznog polja. */
    val path: List<Square>,
    /** Dokle je prikaz stigao. */
    val step: Int = 0,
    /** Prikaz je došao do kraja i čeka se dodir. */
    val isDone: Boolean = false
) {
    val current: Square get() = path[step.coerceIn(path.indices)]

    /** Polja kroz koja se već prošlo — bez onog na kom figura sad stoji. */
    val behind: List<Square> get() = path.take(step)
}

data class MovementUiState(
    val task: MovementTask = MovementTask.REACH,
    val roundNumber: Int = 0,
    val roundCount: Int = 0,
    val attempted: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val isFinished: Boolean = false,
    /** Da li je sesija došla do kraja, ili je napuštena. */
    val isFinishedCleanly: Boolean = true,
    /**
     * Krug je gotov, sledeći još nije počeo.
     *
     * Bez ovoga se mikrofon otvarao na tren u tišini između dva kruga — a
     * otvaranje mikrofona vibrira, pa je to bio i lažan znak „sad govori".
     */
    val isBetweenRounds: Boolean = false,

    // — Šetnja —
    val walk: Walk? = null,
    /** Prikaz odrađene šetnje; `null` dok se radi. */
    val replay: Replay? = null,

    // — Domet na liniji —
    val piece: PieceType? = null,
    val from: Square? = null,
    val line: Line? = null,
    /** Polja koja je korisnik do sada izgovorio u ovom pitanju. */
    val answer: List<Square> = emptyList()
) {
    val isAcceptingInput: Boolean get() = !isFinished && !isBetweenRounds
}

/**
 * **Domet na liniji** — „lovac je na e5, koja polja na b-liniji dohvata?"
 *
 * Traži presek zraka figure i jedne linije, što je korak dalje od „kuda ide
 * lovac": domet se ne nabraja nego se iz njega bira. Prazan odgovor je **valjan
 * odgovor** — skakač sa e5 ne stiže ni na jedno polje b-linije — pa se pitanje
 * ne može rešavati nagađanjem.
 *
 * Samo [Support.NONE]. Uz tablu bi se odgovor pročitao umesto izračunao, a to
 * nije lakša ista vežba nego druga vežba.
 */
internal val REACH_ON_LINE = TaskSpec(
    id = "reach_on_line",
    skills = listOf(Skill.PIECE_GEOMETRY, Skill.COORDINATES),
    supports = listOf(Support.NONE),
    benchmarks = mapOf(Support.NONE to Benchmark(millisPerAttempt = 15_000, minAccuracy = 0.8f))
)

/**
 * **Šetnja figurom** — top, lovac, ili dama naizmenično.
 *
 * Najmanje moguće ažuriranje pozicije: jedna veza figura–polje, jedan potez, bez
 * uzimanja i bez smetnji. Zabrana ponavljanja je ono što ga čini vežbom, jer se
 * uz trenutno polje mora držati i rastući spisak potrošenih.
 *
 * Meri se **po potezu**, ne po šetnji: šetnja od osam i šetnja od dvanaest
 * poteza bi inače ušle u isti broj kao jednaki pokušaji, a nisu.
 */
internal val WALK_PIECE = TaskSpec(
    id = "walk_piece",
    skills = listOf(Skill.POSITION_UPDATE, Skill.PIECE_GEOMETRY, Skill.POSITION_HOLD),
    supports = listOf(Support.NONE),
    benchmarks = mapOf(Support.NONE to Benchmark(millisPerAttempt = 10_000, minAccuracy = 0.85f))
)

/**
 * **Šetnja skakačem** — odvojen zadatak, ne težina prethodnog.
 *
 * Skakač je u dometu figure granica, i njegova šetnja se sa topovskom ne sme
 * sabirati u jedan broj: mera je po zadatku, a ovo su dva različita posla pod
 * istim imenom. Isto pravilo po kom se preko modula ne sabira.
 */
internal val WALK_KNIGHT = TaskSpec(
    id = "walk_knight",
    skills = listOf(Skill.POSITION_UPDATE, Skill.PIECE_GEOMETRY, Skill.POSITION_HOLD),
    supports = listOf(Support.NONE),
    benchmarks = mapOf(Support.NONE to Benchmark(millisPerAttempt = 15_000, minAccuracy = 0.8f))
)

/** Redosled je pedagoški: kratko pitanje pre dugog niza. */
internal val MOVEMENT_TASKS = listOf(REACH_ON_LINE, WALK_PIECE, WALK_KNIGHT)

/**
 * Podešavanja po težini.
 *
 * Prečka je ovde samo jedna, pa je težina **jedina lestvica** koja se penje —
 * prvi modul u kom je tako. Zato skalira obe stvari koje zadatak čine težim:
 * dužinu i, kod šetnje figurom, samu figuru.
 */
private data class Setup(
    val roundCount: Int,
    val moves: Int,
    val pieces: List<PieceType>
)

private fun setupFor(task: MovementTask, difficulty: Difficulty): Setup = when (task) {
    // Pitanja su kratka, pa ih ide više; teže je ono što se pita, ne koliko.
    MovementTask.REACH -> when (difficulty) {
        Difficulty.EASY -> Setup(10, 0, listOf(PieceType.ROOK, PieceType.BISHOP))
        Difficulty.MEDIUM -> Setup(12, 0, listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.QUEEN))
        Difficulty.HARD -> Setup(
            14, 0,
            listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.QUEEN, PieceType.KNIGHT)
        )
    }

    // Prvo se šetnja produžava, pa tek onda ulazi naizmenična dama — dva skoka
    // odjednom se ne prave.
    MovementTask.WALK -> when (difficulty) {
        Difficulty.EASY -> Setup(3, 8, listOf(PieceType.ROOK, PieceType.BISHOP))
        Difficulty.MEDIUM -> Setup(3, 12, listOf(PieceType.ROOK, PieceType.BISHOP))
        Difficulty.HARD -> Setup(3, 12, listOf(PieceType.QUEEN))
    }

    MovementTask.KNIGHT_WALK -> when (difficulty) {
        Difficulty.EASY -> Setup(3, 6, listOf(PieceType.KNIGHT))
        Difficulty.MEDIUM -> Setup(3, 10, listOf(PieceType.KNIGHT))
        Difficulty.HARD -> Setup(3, 14, listOf(PieceType.KNIGHT))
    }
}

/**
 * Koliko se najduže čeka da govor uopšte **krene** pošto je zatražen.
 *
 * Nije trajanje rečenice nego trajanje zahteva motoru. Govornik koji svoj govor
 * ne prati ovde uvek istekne, i to je ispravno: tada se nastavlja odmah.
 */
private const val SPEECH_START_MILLIS = 500L

/** Tišina između izgovorenog i sledećeg pitanja — da se dve stvari ne sliju. */
private const val BREATH_MILLIS = 450L

@HiltViewModel
class MovementViewModel @Inject constructor(
    private val speaker: Speaker,
    private val voiceInput: VoiceInput
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementUiState())
    val uiState: StateFlow<MovementUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceInput.state

    private lateinit var setup: Setup
    private var task: MovementTask = MovementTask.REACH
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis = 0L
    private var isStarted = false
    private var roundJob: Job? = null

    /** Najdublja šetnja u sesiji — koliko je slika izdržala pre prve greške. */
    private var bestHeld: Int? = null

    /**
     * Sluša **do prvog polja, pa se sama zaustavlja** — kao i svi ostali moduli.
     *
     * Ovde je jedno vreme stajalo neprekidno slušanje, da se ne bi diralo
     * dugme između dva poteza. Ispalo je skuplje nego što je vredelo, iz dva
     * razloga.
     *
     * Prvi je da je aplikacija time morala da zna kad govori, pa da se skloni
     * sama sebi s puta — inače Vosk prepozna polje iz zvučnika kao i iz usta, i
     * najava „šetnja topom sa e5" uđe nazad kao tvoj potez. Mikrofon koji otvara
     * korisnik tu opasnost nema uopšte.
     *
     * Drugi je da je to bilo **drugačije iskustvo nego u ostalih osam modula**.
     * Jedan pokret koji se nauči jednom vredi više od ušteđenog dodira, a dodir
     * se ionako dešava dok se misli — pa ništa i ne usporava.
     */
    fun onVoiceInput() {
        voiceInput.listenForSquare { square -> onSquareSpoken(square) }
    }

    /** Prekid slušanja na dodir — bez toga se upaljen mikrofon ne može ugasiti. */
    fun onVoiceStop() = voiceInput.stop()

    fun onRepeat() = speaker.repeat()

    /**
     * Čita dokle se stiglo.
     *
     * Ovo je protivteža pravilu da aplikacija ćuti dok je tačno: potvrda posle
     * svakog polja ubija ritam, ali bez ikakvog načina da se stanje proveri,
     * jedno pogrešno prepoznato polje bi tiho odnelo celu šetnju.
     */
    fun onReadState() = speaker.aside {
        val state = _uiState.value
        if (state.task == MovementTask.REACH) {
            val piece = state.piece ?: return@aside
            val from = state.from ?: return@aside
            val line = state.line ?: return@aside
            sayQuestion(piece, from, line, interrupt = true)
            sayAnswerSoFar(state.answer)
            return@aside
        }

        val walk = state.walk ?: return@aside
        speaker.say(interrupt = true) { walkStandsOn(nameOf(walk.piece)) }
        speaker.say(walk.current, interrupt = false)
        speaker.say(interrupt = false) { walkHeld(walk.movesMade) }
        speaker.say(interrupt = false) { movesLeft(walk.movesLeft) }
        if (walk.piece == PieceType.QUEEN) sayQueenTurn(walk)
    }

    /**
     * Briše sastavljeni odgovor i ponavlja pitanje — samo kod „Dometa na liniji".
     *
     * Postoji zato što se izgovoreno polje ne može povući: glas prepoznaje polja
     * a ne poricanje, pa bi jedno pogrešno prepoznato polje inače nužno oborilo
     * pitanje. Stoji na **dugom dodiru**, da se kratkim ne obriše slučajno.
     */
    fun onAnswerClear() {
        val state = _uiState.value
        if (!state.isAcceptingInput || state.task != MovementTask.REACH) return
        if (state.answer.isEmpty()) return

        _uiState.update { it.copy(answer = emptyList()) }

        val piece = state.piece ?: return
        val from = state.from ?: return
        val line = state.line ?: return
        sayQuestion(piece, from, line, interrupt = true)
    }

    /** Prvi dodir na zonu za odustajanje — traži potvrdu, jer je nepovratno. */
    fun onGiveUpArmed() = speaker.say(interrupt = false) { confirmGiveUp }


    /** Odustajanje: sesija se zatvara sa onim što je do sada urađeno. */
    fun onGiveUp() {
        if (!_uiState.value.isAcceptingInput) return
        finish(completed = false)
    }

    /**
     * Odgovor je gotov — samo kod „Dometa na liniji".
     *
     * Postoji zato što je **prazan odgovor valjan**: da se odgovor zaključivao iz
     * broja izgovorenih polja, „nijedno" se ne bi moglo reći. Glas prepoznaje
     * polja, ne odsustvo polja.
     */
    fun onAnswerDone() {
        val state = _uiState.value
        if (!state.isAcceptingInput || state.task != MovementTask.REACH) return

        val piece = state.piece ?: return
        val from = state.from ?: return
        val line = state.line ?: return

        val truth = reachOn(from, piece, line).toSet()
        val isCorrect = truth == state.answer.toSet()

        _uiState.update {
            it.copy(
                attempted = it.attempted + 1,
                solved = it.solved + if (isCorrect) 1 else 0,
                mistakes = it.mistakes + if (isCorrect) 0 else 1,
                isBetweenRounds = true
            )
        }

        if (isCorrect) {
            speaker.say(interrupt = false) { correct }
        } else {
            // Vežba pokaže istinu, test samo kaže da si promašio.
            speaker.say(interrupt = false) { reachTruthIs }
            if (truth.isEmpty()) {
                speaker.say(interrupt = false) { reachNone }
            } else {
                truth.sortedBy { it.index }.forEach { speaker.say(it, interrupt = false) }
            }
        }

        roundJob?.cancel()
        roundJob = viewModelScope.launch {
            awaitSilence()
            nextRound()
        }
    }

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    fun startOnce(difficulty: Difficulty, taskId: String? = null, rounds: Int? = null) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        task = taskOf(taskId)
        setup = setupFor(task, difficulty).let { base ->
            if (rounds == null || rounds <= 0) base else base.copy(roundCount = rounds)
        }

        startedAtMillis = System.currentTimeMillis()
        _uiState.value = MovementUiState(task = task, roundCount = setup.roundCount)

        // Pravilo se kaže **jednom, pre svega ostalog**. Ceo modul ima samo jedno
        // pravilo — govori kad aplikacija ućuti — a ono se nije imalo odakle
        // saznati; sa uređaja je stiglo baš to, da se ne zna kako se odgovara.
        speaker.say(interrupt = false) {
            if (task == MovementTask.REACH) reachHowTo else walkHowTo
        }

        nextRound()
    }

    /**
     * Prima jedno izgovoreno polje.
     *
     * **Ćuti dok je tačno.** Potvrda posle svakog polja ubija ritam, a ritam je
     * ono što se ovde gradi; progovara se samo na grešci, i kaže se **koja** —
     * „tako se ne ide" i „tu si već bio" nisu isti promašaj.
     */
    private fun onSquareSpoken(square: Square) {
        val state = _uiState.value
        if (!state.isAcceptingInput) return

        if (state.task == MovementTask.REACH) {
            // Ovde se odgovor samo skuplja; ocena stiže tek na „GOTOVO".
            if (square !in state.answer) {
                _uiState.update { it.copy(answer = it.answer + square) }
            }
            // Primljeno polje se **izgovara**, kao i u ostalim modulima. Kad
            // potez stiže glasom, to je jedina potvrda da je prepoznato ono što
            // je i rečeno — a fonetska azbuka pomaže da se pogodi, ne da se
            // sazna šta je pogođeno. Izgovara se i polje koje je već u odgovoru:
            // ono jeste primljeno, a ćutanje bi ličilo na to da nije.
            speaker.say(square, interrupt = false)
            return
        }

        val walk = state.walk ?: return
        val (next, step) = walk.announce(square)

        _uiState.update {
            it.copy(
                walk = next,
                attempted = it.attempted + 1,
                solved = it.solved + if (step == Step.ACCEPTED) 1 else 0,
                mistakes = it.mistakes + if (step == Step.ACCEPTED) 0 else 1
            )
        }

        when (step) {
            Step.ILLEGAL -> speaker.say(interrupt = false) { walkIllegal(nameOf(next.mover)) }
            Step.VISITED -> speaker.say(interrupt = false) { walkVisited }
            // Primljen potez se izgovara. Prvobitno je ovde stajalo ćutanje,
            // da se ne kvari ritam niza — ali ćutanje ne razlikuje „primljeno"
            // od „nisam te čuo", a to je usred šetnje od dvanaest poteza
            // najskuplja moguća nedoumica.
            Step.ACCEPTED -> {
                speaker.say(square, interrupt = false)
                if (next.piece == PieceType.QUEEN && !next.isDone) sayQueenTurn(next)
            }
        }

        if (next.isDone) endWalk(next)
    }

    private fun endWalk(walk: Walk) {
        _uiState.update { it.copy(isBetweenRounds = true) }

        // Zaglavljivanje nije greška nego kraj — dužina je rezultat.
        if (walk.isStuck) speaker.say(interrupt = false) { walkStuck }
        speaker.say(interrupt = false) { walkHeld(walk.movesMade) }

        // Dubina je **najbolja šetnja u sesiji**: to je granica onoga što glava
        // trenutno drži, a prosek preko tri šetnje bi je zamaglio. Šetnja bez
        // greške je izdržala sve svoje poteze.
        val held = walk.heldUntil ?: walk.movesMade
        bestHeld = maxOf(bestHeld ?: held, held)

        _uiState.update { it.copy(replay = Replay(walk.piece, walk.visited)) }

        roundJob?.cancel()
        roundJob = viewModelScope.launch {
            awaitSilence()
            speaker.say(interrupt = false) { walkReplay }

            // Korak čeka da se prethodno polje **izgovori**, umesto da se pogađa
            // koliko izgovor traje. Otud i osećaj da prikaz ide onoliko brzo
            // koliko se stiže pratiti.
            walk.visited.indices.forEach { step ->
                if (_uiState.value.replay == null) return@launch
                _uiState.update { it.copy(replay = it.replay?.copy(step = step)) }
                speaker.say(walk.visited[step], interrupt = false)
                awaitSilence()
            }

            _uiState.update { it.copy(replay = it.replay?.copy(isDone = true)) }
        }
    }

    /**
     * Dalje sa prikaza na sledeću šetnju.
     *
     * Radi i **pre** nego što prikaz dođe do kraja: ko je video dovoljno ne mora
     * da čeka ostatak. Zato dugme stoji na ekranu sve vreme, a ne tek na kraju.
     */
    fun onReplayDone() {
        if (_uiState.value.replay == null) return
        roundJob?.cancel()
        speaker.stop()
        _uiState.update { it.copy(replay = null) }
        nextRound()
    }

    /**
     * Čeka da se izgovori sve što je u redu, pa još kratko.
     *
     * Zamenilo je pauzu od sekunde i po. Ta pauza je **pogađala** koliko traje
     * rečenica, a rečenica zavisi od jezika, od brzine govora koju je korisnik
     * podesio, i od toga koliko se polja nabraja — pa je novo pitanje redovno
     * preseklo prethodni odgovor na pola reči.
     *
     * Prvo se sačeka da govor **krene**, jer je zahtev motoru asinhron i tišina
     * odmah posle njega ne znači da je gotovo. Ako ne krene za [SPEECH_START_MILLIS],
     * ide se dalje — govornik koji ne prati svoj govor javlja tišinu uvek, i na
     * njemu se ne sme zaglaviti.
     */
    private suspend fun awaitSilence() {
        withTimeoutOrNull(SPEECH_START_MILLIS) { speaker.isSpeaking.first { it } }
        speaker.isSpeaking.first { !it }
        delay(BREATH_MILLIS)
    }

    private fun nextRound() {
        // Mikrofon ne sme da pređe iz kruga u krug: polje izgovoreno za prošlo
        // pitanje ne sme da uđe u sledeće.
        voiceInput.stop()

        if (_uiState.value.roundNumber >= setup.roundCount) {
            finish(completed = true)
            return
        }

        val piece = setup.pieces.random()
        val from = Square(Random.nextInt(64))

        if (task == MovementTask.REACH) {
            val line = lineFor(from)
            sayQuestion(piece, from, line, interrupt = false)
            _uiState.update {
                it.copy(
                    roundNumber = it.roundNumber + 1,
                    piece = piece,
                    from = from,
                    line = line,
                    answer = emptyList(),
                    isBetweenRounds = false
                )
            }
            return
        }

        val walk = Walk(piece = piece, start = from, targetMoves = setup.moves)
        speaker.say(interrupt = false) { walkWith(nameOf(walk.piece)) }
        speaker.say(from, interrupt = false)
        if (walk.piece == PieceType.QUEEN) sayQueenTurn(walk)
        _uiState.update {
            it.copy(
                roundNumber = it.roundNumber + 1,
                walk = walk,
                replay = null,
                isBetweenRounds = false
            )
        }
    }

    /**
     * Linija o kojoj se pita.
     *
     * Bira se tako da **ne prolazi kroz polje na kom figura stoji**: „lovac je na
     * e5, koja polja na e-liniji dohvata" je drugo i mnogo lakše pitanje, jer
     * odgovor pada sa same figure.
     */
    private fun lineFor(from: Square): Line {
        val isFile = Random.nextBoolean()
        val taken = if (isFile) from.fileIndex else from.rankIndex
        return Line(isFile = isFile, index = ((0..7) - taken).random())
    }

    private fun reachOn(from: Square, piece: PieceType, line: Line): List<Square> =
        if (line.isFile) {
            EmptyBoard.reachOnFile(from, piece, line.index)
        } else {
            EmptyBoard.reachOnRank(from, piece, line.index)
        }

    /**
     * [interrupt] je `true` samo kad **korisnik traži** da čuje pitanje sad.
     * Sopstvena najava se uvek reda iza onoga što još traje — presecanje
     * sopstvenog govora je bilo ono što je razbijalo tok.
     */
    private fun sayQuestion(piece: PieceType, from: Square, line: Line, interrupt: Boolean) {
        speaker.say(interrupt) { reachPieceOn(nameOf(piece)) }
        speaker.say(from, interrupt = false)
        speaker.say(interrupt = false) {
            if (line.isFile) reachWhichOnFile(line.fileLabel) else reachWhichOnRank(line.rankLabel)
        }
    }

    private fun sayAnswerSoFar(answer: List<Square>) {
        speaker.say(interrupt = false) { reachYouSaid }
        if (answer.isEmpty()) {
            speaker.say(interrupt = false) { reachNone }
        } else {
            answer.forEach { speaker.say(it, interrupt = false) }
        }
    }

    /** Dami se način kretanja smenjuje, pa se sledeći red uvek izgovori. */
    private fun sayQueenTurn(walk: Walk) {
        speaker.say(interrupt = false) {
            if (walk.mover == PieceType.ROOK) walkAsRook else walkAsBishop
        }
    }

    private fun taskOf(taskId: String?): MovementTask = when (taskId) {
        WALK_PIECE.id -> MovementTask.WALK
        WALK_KNIGHT.id -> MovementTask.KNIGHT_WALK
        else -> MovementTask.REACH
    }

    private fun specOf(task: MovementTask): TaskSpec = when (task) {
        MovementTask.REACH -> REACH_ON_LINE
        MovementTask.WALK -> WALK_PIECE
        MovementTask.KNIGHT_WALK -> WALK_KNIGHT
    }

    private fun finish(completed: Boolean) {
        roundJob?.cancel()
        voiceInput.stop()
        val state = _uiState.value
        // Bez ekrana se sažetak ne vidi, pa bi sesija prosto utihnula.
        speaker.say(interrupt = false) { sessionEndSolved(state.solved, state.attempted) }
        _uiState.update { it.copy(isFinished = true, isFinishedCleanly = completed) }
    }

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        val spec = specOf(state.task)
        val elapsed = System.currentTimeMillis() - startedAtMillis
        val tally = SkillTally(
            attempted = state.attempted,
            solved = state.solved,
            millis = elapsed
        )

        return SessionResult(
            moduleId = ModuleId.MOVEMENT,
            difficulty = difficulty,
            attempted = state.attempted,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = elapsed,
            completed = state.isFinishedCleanly,
            support = Support.NONE,
            taskId = spec.id,
            // Veštinu koju zadatak **meri** nosi ceo rezultat; ostale idu uz nju
            // istim brojevima, jer ih ista sesija zaista i dodiruje.
            bySkill = spec.skills.associateWith { tally },
            // Kod dometa se greška ne gomila kroz niz, pa se dubina ni ne meri.
            heldUntil = bestHeld.takeIf { state.task != MovementTask.REACH }
        )
    }

    override fun onCleared() {
        super.onCleared()
        roundJob?.cancel()
        speaker.stop()
        voiceInput.stop()
    }
}
