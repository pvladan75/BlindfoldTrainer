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
import com.program.blindfoldtrainer.core.moduleapi.quantity
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
enum class MovementTask { REACH, RETELL, WALK, KNIGHT_WALK }

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
    val isDone: Boolean = false,
    /**
     * Ovo je **pitanje**, ne odgovor.
     *
     * Ista tabla služi dvema stvarima: da posle šetnje pokaže šta si prošao, i
     * da u „Prepričaj putanju" postavi zadatak. Razlika nije u crtežu nego u
     * tome sme li se preskočiti — pitanje se ne preskače.
     */
    val isPrompt: Boolean = false,
    /**
     * Koliko pređenih polja ostaje obojeno iza figure.
     *
     * Ovo je prečka podrške, prevedena u sliku: uz punu podršku ostaje ceo trag
     * pa se putanja pročita sa table, a na najtežoj se ne vidi ništa osim polja
     * na kom figura stoji.
     */
    val trail: Int = Int.MAX_VALUE
) {
    val current: Square get() = path[step.coerceIn(path.indices)]

    /** Polja kroz koja se već prošlo — bez onog na kom figura sad stoji. */
    val behind: List<Square> get() = path.take(step).takeLast(trail.coerceAtLeast(0))
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

    // — Domet na liniji i Prepričaj putanju —
    val piece: PieceType? = null,
    /** Putanja koja se prepričava — istina sa kojom se poredi. */
    val expected: List<Square> = emptyList(),
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
 * **Prepričaj putanju** — tabla nacrta kretanje, ti ga ispričaš.
 *
 * Jedini zadatak u modulu koji ide **od slike ka zapisu**; sve ostalo ide
 * obrnuto. Zato i meri [Skill.NOTATION]: „viđeno se ume izgovoriti" je pola te
 * veštine, a do sada je nijedan zadatak nije vežbao sa te strane — Diktat radi
 * samo drugi smer.
 *
 * **Tabla ovde nije pomoć nego pitanje.** Otud i jedini zadatak u modulu koji je
 * uopšte ima dok se radi, i jedini koji nema [Support.NONE]: bez slike nema šta
 * da se prevede, pa najteža prečka nije „bez table" nego „bez traga".
 *
 * Dok se putanja crta ništa se ne izgovara. Ime polja bi rešilo baš onaj posao
 * koji zadatak traži.
 */
internal val RETELL_PATH = TaskSpec(
    id = "retell_path",
    skills = listOf(Skill.NOTATION, Skill.POSITION_HOLD, Skill.COORDINATES),
    // Prečka je koliko traga ostaje iza figure — doslovno „koliko slike
    // aplikacija drži umesto tebe".
    supports = listOf(Support.FULL, Support.PARTIAL, Support.TRACE),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 6_000, minAccuracy = 0.85f),
        Support.PARTIAL to Benchmark(millisPerAttempt = 8_000, minAccuracy = 0.8f),
        Support.TRACE to Benchmark(millisPerAttempt = 10_000, minAccuracy = 0.75f)
    )
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

/**
 * Redosled je pedagoški: kratko pitanje pre dugog niza, a **prepoznavanje pre
 * smišljanja** — lakše je ispričati putanju koju si video nego smisliti svoju.
 */
internal val MOVEMENT_TASKS = listOf(REACH_ON_LINE, RETELL_PATH, WALK_PIECE, WALK_KNIGHT)

/**
 * Podešavanja po težini.
 *
 * Prečka je ovde samo jedna, pa je težina **jedina lestvica** koja se penje —
 * prvi modul u kom je tako. Zato skalira obe stvari koje zadatak čine težim:
 * dužinu i, kod šetnje figurom, samu figuru.
 */
internal data class Setup(
    val roundCount: Int,
    val moves: Int,
    val pieces: List<PieceType>
)

internal fun setupFor(task: MovementTask, difficulty: Difficulty): Setup = when (task) {
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

    // Skalira se i duzina i figura: pravolinijska putanja se cita sa table
    // gotovo bez pamcenja, dok se skakacev skok mora zapamtiti kao skok.
    MovementTask.RETELL -> when (difficulty) {
        Difficulty.EASY -> Setup(4, 4, listOf(PieceType.ROOK, PieceType.BISHOP))
        Difficulty.MEDIUM -> Setup(4, 5, listOf(PieceType.KNIGHT))
        Difficulty.HARD -> Setup(4, 7, listOf(PieceType.KNIGHT))
    }

    MovementTask.KNIGHT_WALK -> when (difficulty) {
        Difficulty.EASY -> Setup(3, 6, listOf(PieceType.KNIGHT))
        Difficulty.MEDIUM -> Setup(3, 10, listOf(PieceType.KNIGHT))
        Difficulty.HARD -> Setup(3, 14, listOf(PieceType.KNIGHT))
    }
}

/**
 * Šta težina znači — **po zadatku**, jer ne skalira svuda isto.
 *
 * U dometu raste spisak figura, u prepričavanju i šetnji dužina putanje, a na
 * najtežoj šetnji figurom ulazi naizmenična dama umesto još poteza. Zato se ne
 * može reći jednom rečenicom za ceo modul.
 */
internal fun difficultyDetailOf(difficulty: Difficulty, taskId: String?): String {
    val task = when (taskId) {
        RETELL_PATH.id -> MovementTask.RETELL
        WALK_PIECE.id -> MovementTask.WALK
        WALK_KNIGHT.id -> MovementTask.KNIGHT_WALK
        else -> MovementTask.REACH
    }

    val setup = setupFor(task, difficulty)
    val pieces = setup.pieces

    // Gde figura ulazi u težinu, ona je i vest — broj poteza uz nju kaže manje.
    return when (task) {
        MovementTask.REACH -> when {
            PieceType.KNIGHT in pieces -> "i skakač"
            PieceType.QUEEN in pieces -> "i dama"
            else -> "top i lovac"
        }

        MovementTask.RETELL -> if (PieceType.KNIGHT in pieces) {
            "skakač, ${setup.moves} poteza"
        } else {
            quantity(setup.moves, "potez", "poteza")
        }

        // Na najtežoj se ne dodaju potezi nego dama, pa se to i kaže.
        MovementTask.WALK -> if (PieceType.QUEEN in pieces) {
            "damom, ${setup.moves} poteza"
        } else {
            quantity(setup.moves, "potez", "poteza")
        }

        MovementTask.KNIGHT_WALK -> quantity(setup.moves, "potez", "poteza")
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

/**
 * Koliko jedan korak stoji dok se putanja **crta**.
 *
 * Jedino mesto u modulu gde tempo vodi sat a ne govor — jer se dok se crta i ne
 * govori. Prvi predlog: dovoljno sporo da se polje pročita, dovoljno brzo da se
 * putanja od sedam poteza ne oteže.
 */
private const val PROMPT_STEP_MILLIS = 1_100L

/** Skakačev skok se ne pročita usput nego se potraži — vidi `promptStepFor`. */
private const val KNIGHT_STEP_MILLIS = 1_700L

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

    /**
     * Prečka na kojoj se radi.
     *
     * Dugo je bila zakucana na [Support.NONE], jer je modul imao samo zadatke
     * bez table. „Prepričaj putanju" je prvi koji ih ima više, pa se prečka sad
     * prima kao porudžbina i svodi na najbližu koju zadatak ume.
     */
    private var support: Support = Support.NONE
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
        if (state.task == MovementTask.RETELL) {
            speaker.say(interrupt = true) { retellLeft(state.expected.size - state.answer.size) }
            return@aside
        }

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
    fun startOnce(
        difficulty: Difficulty,
        taskId: String? = null,
        requestedSupport: Support? = null,
        rounds: Int? = null
    ) {
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        task = taskOf(taskId)
        val spec = specOf(task)
        support = spec.nearestSupport(requestedSupport ?: spec.supports.first())
        setup = setupFor(task, difficulty).let { base ->
            if (rounds == null || rounds <= 0) base else base.copy(roundCount = rounds)
        }

        startedAtMillis = System.currentTimeMillis()
        _uiState.value = MovementUiState(task = task, roundCount = setup.roundCount)

        // Pravilo se kaže **jednom, pre svega ostalog**. Ceo modul ima samo jedno
        // pravilo — govori kad aplikacija ućuti — a ono se nije imalo odakle
        // saznati; sa uređaja je stiglo baš to, da se ne zna kako se odgovara.
        if (task != MovementTask.RETELL) {
            speaker.say(interrupt = false) {
                if (task == MovementTask.REACH) reachHowTo else walkHowTo
            }
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

        if (state.task == MovementTask.RETELL) {
            onRetellSpoken(state, square)
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

    /**
     * Jedno polje u prepričavanju putanje.
     *
     * Promašaj **ne zaustavlja i ne vraća** nego se odmah kaže koje je polje
     * bilo, pa se ide dalje. Da se stajalo na istom mestu, jedno pogrešno
     * prepoznato polje bi zaključalo krug; da se ćutke prelazilo dalje,
     * izgubio bi se korak i sve iza toga bi ispalo pogrešno iako se zna.
     * Ovako se posle greške i dalje zna gde si.
     */
    private fun onRetellSpoken(state: MovementUiState, square: Square) {
        val index = state.answer.size
        if (index > state.expected.lastIndex) return

        val truth = state.expected[index]
        val isCorrect = square == truth

        _uiState.update {
            it.copy(
                answer = it.answer + square,
                attempted = it.attempted + 1,
                solved = it.solved + if (isCorrect) 1 else 0,
                mistakes = it.mistakes + if (isCorrect) 0 else 1
            )
        }

        if (isCorrect) {
            speaker.say(square, interrupt = false)
        } else {
            speaker.say(interrupt = false) { retellWrong }
            speaker.say(truth, interrupt = false)
        }

        if (index == state.expected.lastIndex) endRetell()
    }

    /**
     * Kraj jednog prepričavanja.
     *
     * Putanja se pokaže **samo ako je promašena**. Ko ju je ispričao tačno je
     * već zna, pa bi mu prikaz bio čekanje; ko nije, njemu je prikaz jedino
     * mesto na kom vidi šta je zapravo bilo.
     */
    private fun endRetell() {
        val state = _uiState.value
        val piece = state.piece ?: return

        // Dubina je koliko je polja izgovoreno **tačno i po redu** pre prve
        // greške; posle nje se ne broji, jer se dalje ide uz pomoć.
        val held = state.expected.zip(state.answer).takeWhile { (truth, said) -> truth == said }.size
        bestHeld = maxOf(bestHeld ?: held, held)

        _uiState.update { it.copy(isBetweenRounds = true) }

        if (state.answer == state.expected) {
            speaker.say(interrupt = false) { allCorrect }
            roundJob?.cancel()
            roundJob = viewModelScope.launch {
                awaitSilence()
                nextRound()
            }
            return
        }

        showTruth(piece, state.expected)
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

        showTruth(walk.piece, walk.visited)
    }

    /**
     * Pokazuje putanju natrag, uz izgovaranje — odgovor, ne pitanje.
     *
     * Korak čeka da se prethodno polje **izgovori**, umesto da se pogađa koliko
     * izgovor traje. Otud i osećaj da prikaz ide onoliko brzo koliko se stiže
     * pratiti.
     */
    private fun showTruth(piece: PieceType, path: List<Square>) {
        _uiState.update { it.copy(replay = Replay(piece, path)) }

        roundJob?.cancel()
        roundJob = viewModelScope.launch {
            awaitSilence()
            speaker.say(interrupt = false) { walkReplay }

            path.indices.forEach { step ->
                if (_uiState.value.replay == null) return@launch
                _uiState.update { it.copy(replay = it.replay?.copy(step = step)) }
                speaker.say(path[step], interrupt = false)
                awaitSilence()
            }

            _uiState.update { it.copy(replay = it.replay?.copy(isDone = true)) }
        }
    }

    /**
     * Crta putanju koju treba zapamtiti — pitanje, ne odgovor.
     *
     * **Ćuti dok crta.** Ime polja bi odradilo baš onaj posao koji zadatak traži
     * od tebe, pa bi vežba merila slušanje umesto gledanja.
     *
     * Korak ide po satu, jer ovde nema govora koji bi ga vodio, i **zavisi od
     * figure** — vidi [promptStepFor].
     */
    private fun showPrompt(piece: PieceType, path: List<Square>) {
        _uiState.update {
            it.copy(replay = Replay(piece, path, isPrompt = true, trail = trailFor(support)))
        }

        roundJob?.cancel()
        roundJob = viewModelScope.launch {
            speaker.say(interrupt = false) { retellWatch(nameOf(piece)) }
            awaitSilence()

            path.indices.forEach { step ->
                _uiState.update { it.copy(replay = it.replay?.copy(step = step)) }
                delay(promptStepFor(piece))
            }

            // Tabla nestaje; odavde se prepričava.
            _uiState.update { it.copy(replay = null, isBetweenRounds = false) }
            speaker.say(interrupt = false) { retellNow }
        }
    }

    /**
     * Koliko jedan korak stoji, po figuri.
     *
     * Skakač dobija više vremena, i to **nije olakšica**. Topov sledeći potez
     * pada na liniju na kojoj već gledaš, pa se pročita usput; skakačev pada
     * pored nje i mora se potražiti. Isti sat bi za skakača značio manje
     * stvarnog vremena za čitanje, pa bi vežba merila brzinu oka umesto
     * pamćenja putanje.
     *
     * Sa **težinom** tempo i dalje nema veze: brže crtanje ne traži drugu
     * veštinu nego samo bolji vid. Težina skalira dužinu i figuru.
     */
    private fun promptStepFor(piece: PieceType): Long =
        if (piece == PieceType.KNIGHT) KNIGHT_STEP_MILLIS else PROMPT_STEP_MILLIS

    /**
     * Koliko traga ostaje iza figure, po prečki.
     *
     * Uz punu podršku putanja se pročita sa table i ništa se ne pamti; na
     * najtežoj se ne vidi ništa osim polja na kom figura stoji, pa se pamti sve.
     * [Support.PARTIAL] ostavlja dva polja — dovoljno da se vidi odakle se
     * došlo, premalo da se pročita oblik.
     */
    private fun trailFor(support: Support): Int = when (support) {
        Support.FULL -> Int.MAX_VALUE
        Support.PARTIAL -> 2
        else -> 0
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

        if (task == MovementTask.RETELL) {
            val path = randomWalkPath(piece, setup.moves)
            _uiState.update {
                it.copy(
                    roundNumber = it.roundNumber + 1,
                    piece = piece,
                    expected = path,
                    answer = emptyList(),
                    walk = null,
                    // Ulaz je zatvoren **dok se crta**: polje izgovoreno tada bi
                    // ušlo kao prvi odgovor, a putanja se još ni ne zna cela.
                    // Otvara ga [showPrompt] kad tabla nestane.
                    isBetweenRounds = true
                )
            }
            showPrompt(piece, path)
            return
        }

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
        RETELL_PATH.id -> MovementTask.RETELL
        WALK_PIECE.id -> MovementTask.WALK
        WALK_KNIGHT.id -> MovementTask.KNIGHT_WALK
        else -> MovementTask.REACH
    }

    private fun specOf(task: MovementTask): TaskSpec = when (task) {
        MovementTask.REACH -> REACH_ON_LINE
        MovementTask.RETELL -> RETELL_PATH
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
            support = support,
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
