package com.program.blindfoldtrainer.feature.dictation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.audio.Speaker
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.ReconstructionGrade
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.chess.gradeReconstruction
import com.program.blindfoldtrainer.core.chess.randomSparsePosition
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Faza kroz koju prolazi svaki zadatak. */
enum class DictationPhase {
    /**
     * Pozicija se čita, a **table nema na ekranu**.
     *
     * Ne samo da se ne sme postavljati dok se čita — ne sme se ni videti gde bi
     * se postavljalo. Sa praznom tablom pred sobom, vežba se svede na
     * prepisivanje: čuješ figuru, spustiš je, i sliku u glavi nikad ne sastaviš.
     * Faza se završava tek kad korisnik kaže da zna gde je šta.
     */
    LISTENING,

    /** Pozicija je saslušana; tabla se popunjava iz palete. */
    PLACING,

    /** Poređenje sa zadatom pozicijom. */
    REVIEW
}

data class DictationUiState(
    /** Pozicija koja je izgovorena. Vidi se tek u pregledu. */
    val target: Board = Board.EMPTY,
    val placed: Map<Square, Piece> = emptyMap(),
    val palette: List<Piece> = emptyList(),
    val selectedIndex: Int? = null,
    val phase: DictationPhase = DictationPhase.LISTENING,
    val grade: ReconstructionGrade? = null,
    val taskNumber: Int = 0,
    val taskCount: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    /** Koliko je puta pozicija ponovo pročitana. Merilo, ne kazna. */
    val replays: Int = 0,
    val isFinished: Boolean = false
) {
    val selectedPiece: Piece? get() = selectedIndex?.let { palette.getOrNull(it) }

    val progress: Float
        get() = if (taskCount == 0) 0f else taskNumber.toFloat() / taskCount

    /**
     * Tabla koja se prikazuje.
     *
     * Dok se slaže, to je **samo ono što je korisnik postavio** — zadata pozicija
     * se u ovom modulu ne vidi nikad pre pregleda, jer bi se time izgubila cela
     * vežba. Dok se sluša, table nema uopšte.
     */
    val visibleBoard: Board
        get() = when (phase) {
            DictationPhase.LISTENING, DictationPhase.PLACING -> Board.of(placed)
            DictationPhase.REVIEW -> target
        }
}

/**
 * Podešavanja po težini.
 *
 * Ovde ne raste pritisak vremena nego **koliko se odjednom drži u glavi**: čitanje
 * se sme ponoviti koliko god puta, pa je jedina prava težina broj figura.
 */
private data class Setup(val taskCount: Int, val pieceCount: Int)

private fun setupFor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> Setup(taskCount = 5, pieceCount = 3)
    Difficulty.MEDIUM -> Setup(taskCount = 6, pieceCount = 5)
    Difficulty.HARD -> Setup(taskCount = 6, pieceCount = 7)
}

private const val REVIEW_PAUSE_MILLIS = 2_600L

/**
 * Pozicija se čuje, a slaže se na tabli.
 *
 * Jedini modul koji ide **od zapisa ka slici u glavi**; ostalih šest idu obrnuto,
 * od viđene pozicije ka zapisu. Zato je zaseban modul a ne još jedna težina u
 * „Zapamti poziciju": jedan modul — jedno uputstvo.
 */
@HiltViewModel
class DictationViewModel @Inject constructor(
    private val speaker: Speaker
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictationUiState())
    val uiState: StateFlow<DictationUiState> = _uiState.asStateFlow()

    private lateinit var setup: Setup
    private var difficulty: Difficulty = Difficulty.EASY
    private var startedAtMillis = 0L
    private var reviewJob: Job? = null
    private var isStarted = false

    /** Bezbedno je zvati više puta — pokreće sesiju samo prvi put. */
    /**
     * Prečka na kojoj je sesija odrađena. Ovaj zadatak zna samo punu podršku, pa
     * se svaka porudžbina svodi na nju — `nearestSupport` to kaže umesto da se
     * pravilo prepisuje ovde.
     */
    private var resolvedSupport = Support.FULL

    fun startOnce(
        difficulty: Difficulty,
        requestedSupport: Support? = null,
        rounds: Int? = null
    ) {
        resolvedSupport = DICTATION_PLACE_POSITION.nearestSupport(requestedSupport ?: Support.FULL)
        if (isStarted) return
        isStarted = true
        this.difficulty = difficulty
        setup = setupFor(difficulty).shortenedTo(rounds)
        startedAtMillis = System.currentTimeMillis()
        _uiState.value = DictationUiState(taskCount = setup.taskCount)
        nextTask()
    }

    /**
     * Ponovo čita zadatu poziciju. **Neograničeno je namerno** — kome ide teže,
     * taj sme da pita koliko god treba.
     *
     * Ali **ne košta isto u obe faze**:
     *
     * - dok se sluša, čitanje je sama vežba i slobodno je;
     * - dok se slaže, korisnik je već rekao „znam gde je šta". Ako se onda seti
     *   da ipak ne zna, to je **propust** i broji se kao greška.
     *
     * Granica nije kazna nego merilo. Broj čitanja i inače stoji na ekranu, a
     * kad vremenom padne sa pet na jedno, to je i ceo dokaz da vežba radi.
     */
    fun onReplay() {
        val state = _uiState.value
        if (state.phase == DictationPhase.REVIEW) return

        val isLapse = state.phase == DictationPhase.PLACING
        _uiState.update {
            it.copy(
                replays = it.replays + 1,
                mistakes = it.mistakes + if (isLapse) 1 else 0
            )
        }
        speaker.say(state.target)
    }

    /**
     * Korisnik kaže da zna gde je šta — tabla se pojavljuje i slaganje počinje.
     *
     * Čitanje se pri tom **prekida**, i to je ono što ispunjava pravilo da se ne
     * postavlja dok se čita: posle ovoga se čita samo ako se čitanje izričito
     * zatraži. Dva se nikad ne preklapaju sama od sebe.
     */
    fun onReady() {
        if (_uiState.value.phase != DictationPhase.LISTENING) return
        speaker.stop()
        _uiState.update { it.copy(phase = DictationPhase.PLACING) }
    }

    fun onPaletteClicked(index: Int) {
        val state = _uiState.value
        if (state.phase != DictationPhase.PLACING) return
        if (index !in state.palette.indices) return

        // Ponovni dodir na izabranu figuru je poništava — inače nema načina da
        // se odustane od izbora osim postavljanja.
        _uiState.update { it.copy(selectedIndex = if (it.selectedIndex == index) null else index) }
    }

    fun onSquareClicked(square: Square) {
        val state = _uiState.value
        if (state.phase != DictationPhase.PLACING) return

        val existing = state.placed[square]
        if (existing != null) {
            // Dodir na zauzeto polje vraća figuru u paletu — ispravka jednog
            // polja ne sme da traži poništavanje cele pozicije.
            _uiState.update {
                it.copy(
                    placed = it.placed - square,
                    palette = it.palette + existing,
                    selectedIndex = null
                )
            }
            return
        }

        val index = state.selectedIndex ?: return
        val piece = state.palette.getOrNull(index) ?: return

        val palette = state.palette.toMutableList().also { it.removeAt(index) }
        _uiState.update {
            it.copy(
                placed = it.placed + (square to piece),
                palette = palette,
                selectedIndex = null
            )
        }

        if (palette.isEmpty()) finishTask()
    }

    /** Provera pre nego što je paleta prazna — ono što je postavljeno se ocenjuje. */
    fun onCheck() {
        if (_uiState.value.phase != DictationPhase.PLACING) return
        finishTask()
    }

    private fun finishTask() {
        val state = _uiState.value
        val grade = gradeReconstruction(state.target, state.placed)

        _uiState.update {
            it.copy(
                phase = DictationPhase.REVIEW,
                grade = grade,
                selectedIndex = null,
                solved = it.solved + if (grade.isPerfect) 1 else 0,
                // Svako pogrešno i svako propušteno polje je po jedna greška.
                mistakes = it.mistakes + grade.wrong.size + grade.missed.size
            )
        }

        // Ishod se i izgovara: u modulu koji se sluša, oko je zauzeto tablom.
        speaker.say {
            if (grade.isPerfect) {
                allCorrect
            } else {
                correctOutOf(grade.correct.size, grade.correct.size + grade.missed.size)
            }
        }

        reviewJob?.cancel()
        reviewJob = viewModelScope.launch {
            delay(REVIEW_PAUSE_MILLIS)
            if (_uiState.value.taskNumber >= setup.taskCount) {
                _uiState.update { it.copy(isFinished = true) }
            } else {
                nextTask()
            }
        }
    }

    private fun nextTask() {
        val target = randomSparsePosition(setup.pieceCount)

        _uiState.update {
            it.copy(
                target = target,
                placed = emptyMap(),
                // Paleta se meša da redosled ne oda kojim su redom figure
                // izgovorene — inače bi se pozicija složila bez slušanja.
                palette = target.occupied().map { (_, piece) -> piece }.shuffled(),
                selectedIndex = null,
                phase = DictationPhase.LISTENING,
                grade = null,
                taskNumber = it.taskNumber + 1
            )
        }

        // Čeka svoj red, da ne preseče izgovor ishoda prethodnog zadatka.
        speaker.say(target, interrupt = false)
    }

    /** Ishod sesije — jedini kanal kojim rezultat stiže do bodovanja. */
    fun buildResult(): SessionResult {
        val state = _uiState.value
        return SessionResult(
            moduleId = ModuleId.DICTATION,
            difficulty = difficulty,
            // Ako je korisnik prekinuo, broji se samo dokle je stigao.
            attempted = state.taskNumber,
            solved = state.solved,
            mistakes = state.mistakes,
            elapsedMillis = System.currentTimeMillis() - startedAtMillis,
            completed = state.isFinished,
            support = resolvedSupport,
            taskId = DICTATION_PLACE_POSITION.id,
            bySkill = mapOf(
                DICTATION_PLACE_POSITION.measures to SkillTally(
                    attempted = state.taskNumber,
                    solved = state.solved,
                    // Vreme je deo mere: tačno a sporo znači da veština
                    // još nije automatska, pa se na njoj ne može graditi dalje.
                    millis = System.currentTimeMillis() - startedAtMillis
                )
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        reviewJob?.cancel()
        speaker.stop()
    }
}

/**
 * Pozicija se izgovori, tabla je prazna, ti je složiš.
 *
 * Meri **prevod zapisa u sliku** — jedini smer koji ostali zadaci nemaju, jer
 * svi idu od viđene pozicije ka zapisu. Držanje ide uz to, pošto se cela
 * pozicija nosi od slušanja do slaganja.
 *
 * Zna samo punu podršku: slaže se iz palete, a paleta traži oko.
 */
internal val DICTATION_PLACE_POSITION = TaskSpec(
    id = "place_position",
    skills = listOf(Skill.NOTATION, Skill.POSITION_HOLD),
    supports = listOf(Support.FULL),
    benchmarks = mapOf(
        Support.FULL to Benchmark(millisPerAttempt = 75_000, minAccuracy = 0.8f)
    )
)

/**
 * Kraća sesija na zahtev provere. `null` — koliko težina kaže.
 *
 * Skraćuje se **samo broj krugova**, ne i njihova težina: provera koja bi uz to
 * olakšala i sadržaj merila bi nešto drugo nego vežba.
 */
private fun Setup.shortenedTo(rounds: Int?): Setup =
    if (rounds == null || rounds <= 0) this else copy(taskCount = rounds)
