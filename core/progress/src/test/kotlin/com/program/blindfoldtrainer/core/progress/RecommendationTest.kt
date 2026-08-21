package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Put se pravi, ne crta: cilj iz onoga što se zna, korak iz onoga što se
 * poslednje dogodilo. Predlog, nikad prepreka.
 */
class RecommendationTest {

    private val coordinates = TaskSpec(
        id = "square_color",
        skills = listOf(Skill.COORDINATES),
        supports = listOf(Support.FULL, Support.NONE),
        benchmarks = mapOf(
            Support.FULL to Benchmark(3_000, 0.9f),
            Support.NONE to Benchmark(4_500, 0.9f)
        )
    )

    private val geometry = TaskSpec(
        id = "shortest_path",
        skills = listOf(Skill.PIECE_GEOMETRY),
        supports = listOf(Support.FULL, Support.NONE)
    )

    private val hold = TaskSpec(
        id = "reconstruct",
        skills = listOf(Skill.POSITION_HOLD),
        supports = listOf(Support.FULL)
    )

    private val tasks = listOf(coordinates, geometry, hold)

    private var clock = 1_000_000L

    private fun session(
        skill: Skill,
        taskId: String,
        attempted: Int,
        solved: Int,
        support: Support = Support.FULL,
        millis: Long = 10_000,
        difficulty: Difficulty = Difficulty.EASY
    ) = SessionResult(
        moduleId = ModuleId.GEOMETRY,
        difficulty = difficulty,
        attempted = attempted,
        solved = solved,
        mistakes = attempted - solved,
        elapsedMillis = millis,
        bySkill = mapOf(skill to SkillTally(attempted, solved, millis)),
        support = support,
        finishedAtMillis = clock++,
        taskId = taskId
    )

    /**
     * „Sad" je **odmah posle poslednje sesije**.
     *
     * Bez toga bi lažni sat iz ovih testova — koji kreće od miliona milisekundi,
     * dakle od 1970 — svaku savladanu veštinu prijavio kao zapuštenu, pa bi
     * održavanje progutalo svaki drugi predlog. Vreme ovde nije predmet testa
     * osim tamo gde piše da jeste.
     */
    private fun ProgressSnapshot.suggest(
        tasks: List<TaskSpec>,
        lastTaskId: String? = null,
        difficultiesFor: (String) -> List<Difficulty> = { Difficulty.entries }
    ) = recommend(
        tasks = tasks,
        lastTaskId = lastTaskId,
        nowMillis = clock,
        difficultiesFor = difficultiesFor
    )

    @Test
    fun `bez zadataka nema predloga`() {
        assertNull(ProgressSnapshot.EMPTY.suggest(emptyList()))
    }

    /**
     * O neprobanom se ne zna ništa, a to je vrednije saznanje od još jedne
     * potvrde da je nešto slabo.
     */
    @Test
    fun `neprobano ide pre slabog`() {
        val history = listOf(session(Skill.COORDINATES, "square_color", 10, 4))
        val recommendation = history.toProgressSnapshot().suggest(tasks)!!

        assertNotEquals("square_color", recommendation.taskId)
        assertEquals(Reason.NEVER_TRIED, recommendation.reason)
    }

    /** Zastoj se ne probija ponavljanjem. */
    @Test
    fun `isti zadatak se ne predlaze dvaput zaredom`() {
        val history = listOf(session(Skill.COORDINATES, "square_color", 10, 4))
        val snapshot = history.toProgressSnapshot()

        val recommendation = snapshot.suggest(tasks, lastTaskId = "shortest_path")!!
        assertNotEquals("shortest_path", recommendation.taskId)
    }

    /** Kad je zadatak jedini, pravilo o ponavljanju ustupa — bolje isti nego ništa. */
    @Test
    fun `jedini zadatak se predlaze i kad je bio poslednji`() {
        val recommendation = ProgressSnapshot.EMPTY
            .suggest(listOf(coordinates), lastTaskId = "square_color")!!

        assertEquals("square_color", recommendation.taskId)
    }

    /**
     * Veština čiji temelji nisu automatski se **ne zabranjuje** nego pomera
     * unazad — preporuka je predlog, ne kapija.
     */
    @Test
    fun `vestina bez temelja ide iza onih koje nista ne koci`() {
        // Sve tri su probane; držanje pozicije je najslabije, ali mu koordinate
        // nisu automatske, pa prvo ide ono što ništa ne koči.
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 9, millis = 60_000),
            session(Skill.PIECE_GEOMETRY, "shortest_path", 10, 8),
            session(Skill.POSITION_HOLD, "reconstruct", 10, 3)
        )

        val recommendation = history.toProgressSnapshot().suggest(tasks)!!

        assertNotEquals("reconstruct", recommendation.taskId)
    }

    /** Nov zadatak kreće od prečke sa najviše pomoći. */
    @Test
    fun `nov zadatak krece od pune podrske`() {
        val recommendation = ProgressSnapshot.EMPTY.suggest(listOf(coordinates))!!

        assertEquals(Support.FULL, recommendation.support)
    }

    /** Dva puta uspešno na istoj prečki — prečka niže. */
    @Test
    fun `dva uspeha spustaju precku`() {
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10),
            session(Skill.COORDINATES, "square_color", 10, 10)
        )

        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(coordinates))!!

        assertEquals(Support.NONE, recommendation.support)
    }

    /** Jedan uspeh nije dovoljan; ostaje se gde se bilo. */
    @Test
    fun `jedan uspeh ne spusta precku`() {
        val history = listOf(session(Skill.COORDINATES, "square_color", 10, 10))
        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(coordinates))!!

        assertEquals(Support.FULL, recommendation.support)
    }

    /** Promašaj vraća prečku nazad, bez kazne. */
    @Test
    fun `promasaj vraca precku nazad`() {
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10),
            session(Skill.COORDINATES, "square_color", 10, 10),
            session(Skill.COORDINATES, "square_color", 10, 4, support = Support.NONE)
        )

        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(coordinates))!!

        assertEquals(Support.FULL, recommendation.support)
    }

    /** Ispod najniže prečke se ne pada — zadatak nema šta dalje da ponudi. */
    @Test
    fun `promasaj na najlaksoj precki ostaje tu`() {
        val history = listOf(session(Skill.POSITION_HOLD, "reconstruct", 10, 2))
        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(hold))!!

        assertEquals(Support.FULL, recommendation.support)
    }

    /**
     * Razlog „temelj" govori o **ovoj** veštini, ne o onoj iznad nje:
     * „koordinate su temelj" je argument, „držanju pozicije fale temelji" je
     * prigovor. Zato se pali kad je izabrana veština temelj drugima, a još nije
     * automatska.
     */
    @Test
    fun `temelj se prijavljuje na samom temelju`() {
        // Koordinate su probane i tačne, ali spore — dakle nisu automatske, a
        // na njima stoje držanje i zapis.
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10, millis = 90_000)
        )

        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(coordinates), lastTaskId = null)!!

        assertEquals(Reason.FOUNDATION, recommendation.reason)
    }

    /** Neprobano je osnovnija činjenica od toga što je nešto temelj. */
    @Test
    fun `neprobano ide ispred temelja`() {
        val recommendation = ProgressSnapshot.EMPTY.suggest(listOf(coordinates))!!

        assertEquals(Reason.NEVER_TRIED, recommendation.reason)
    }

    /**
     * Preporuka koja uvek šalje na najgore je preporuka koja se prestane
     * otvarati — na svakih pet sesija dolazi ono što ide dobro.
     */
    @Test
    fun `povremeno se predlaze i ono sto ide dobro`() {
        val history = (1..4).map { session(Skill.COORDINATES, "square_color", 10, 3) } +
            listOf(session(Skill.PIECE_GEOMETRY, "shortest_path", 10, 10))

        val recommendation = history.toProgressSnapshot().suggest(tasks)!!

        assertEquals(Reason.STRENGTH, recommendation.reason)
    }

    /**
     * Do sada je predlog nosio prečku a težinu prećutkivao, pa je školjka
     * upisivala najlakšu. Ko je slušao predlog, dobijao je najlakšu trećinu
     * svakog modula i nikad ne bi izašao iz nje.
     */
    @Test
    fun `nov zadatak krece od najlakse tezine`() {
        val recommendation = ProgressSnapshot.EMPTY.suggest(listOf(hold))!!

        assertEquals(Difficulty.EASY, recommendation.difficulty)
    }

    /**
     * Kad prečka nema kuda dalje, težina preuzima posao — inače bi zadatak sa
     * jednom prečkom zauvek ostao na najlakšoj.
     */
    @Test
    fun `dva uspeha na najtezoj precki dizu tezinu`() {
        val history = listOf(
            session(Skill.POSITION_HOLD, "reconstruct", 10, 10),
            session(Skill.POSITION_HOLD, "reconstruct", 10, 10)
        )

        val recommendation = history.toProgressSnapshot().suggest(listOf(hold))!!

        assertEquals(Support.FULL, recommendation.support)
        assertEquals(Difficulty.MEDIUM, recommendation.difficulty)
    }

    /** Jedan uspeh nije dovoljan, isto kao ni za prečku. */
    @Test
    fun `jedan uspeh ne dize tezinu`() {
        val history = listOf(session(Skill.POSITION_HOLD, "reconstruct", 10, 10))
        val recommendation = history.toProgressSnapshot().suggest(listOf(hold))!!

        assertEquals(Difficulty.EASY, recommendation.difficulty)
    }

    /** Promašaj vraća težinu nazad, po istom pravilu kao prečku. */
    @Test
    fun `promasaj vraca tezinu nazad`() {
        val history = listOf(
            session(Skill.POSITION_HOLD, "reconstruct", 10, 10, difficulty = Difficulty.MEDIUM),
            session(Skill.POSITION_HOLD, "reconstruct", 10, 3, difficulty = Difficulty.MEDIUM)
        )

        val recommendation = history.toProgressSnapshot().suggest(listOf(hold))!!

        assertEquals(Difficulty.EASY, recommendation.difficulty)
    }

    /** Ispod najlakše težine se ne pada, isto kao ni ispod najniže prečke. */
    @Test
    fun `promasaj na najlaksoj tezini ostaje tu`() {
        val history = listOf(session(Skill.POSITION_HOLD, "reconstruct", 10, 2))
        val recommendation = history.toProgressSnapshot().suggest(listOf(hold))!!

        assertEquals(Difficulty.EASY, recommendation.difficulty)
    }

    /**
     * **Dve lestvice se ne penju uporedo.** Dva uspeha na punoj podršci spuštaju
     * prečku, a težina tada zatiče praznu istoriju na novoj prečki i vraća se na
     * najlakšu. Da se pomere obe, skok bi bio dvostruk.
     */
    @Test
    fun `precka i tezina se ne penju istovremeno`() {
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10),
            session(Skill.COORDINATES, "square_color", 10, 10)
        )

        val recommendation = history.toProgressSnapshot().suggest(listOf(coordinates))!!

        assertEquals(Support.NONE, recommendation.support)
        assertEquals(Difficulty.EASY, recommendation.difficulty)
    }

    /**
     * Težina se **broji po prečki**: uspeh uz tablu ne dokazuje ništa o tome
     * kako ista težina ide bez nje.
     */
    @Test
    fun `tezina se racuna na precki na koju se ide`() {
        val history = listOf(
            // Dva uspeha bez table dižu težinu tamo, a ne na punoj podršci.
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE),
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE)
        )

        val recommendation = history.toProgressSnapshot().suggest(listOf(coordinates))!!

        assertEquals(Support.NONE, recommendation.support)
        assertEquals(Difficulty.MEDIUM, recommendation.difficulty)
    }

    /** Modul koji težine ne nudi ih ne dobija ni na predlogu — izmišljati je značilo bi lagati. */
    @Test
    fun `modul bez tezina ne dobija tezinu`() {
        val recommendation = ProgressSnapshot.EMPTY
            .suggest(listOf(hold), difficultiesFor = { emptyList() })!!

        assertNull(recommendation.difficulty)
    }

    /**
     * **Savladano nije završeno.** Bez ovoga bi put uvek išao ka najslabijem i
     * tiho puštao da najjače propada — a to bi se otkrilo tek na pravoj partiji.
     */
    @Test
    fun `savladano a zapusteno se vraca na potvrdu`() {
        // Dve sesije bez table sa dostignutim orijentirom — koordinate su
        // savladane, jer je to najteža prečka koju taj zadatak nudi.
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE),
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE)
        )
        val snapshot = history.toProgressSnapshot()

        val month = clock + 30L * 24 * 60 * 60 * 1000
        val recommendation = snapshot.recommend(listOf(coordinates), nowMillis = month)!!

        assertEquals(Reason.UPKEEP, recommendation.reason)
        assertEquals(Skill.COORDINATES, recommendation.skill)
        // Potvrda ide na najtežu prečku: na lakšoj ne bi dokazala ništa o tvrdnji.
        assertEquals(Support.NONE, recommendation.support)
    }

    /** Sveže savladano se ne dira — inače bi predlog bio zvocanje. */
    @Test
    fun `sveze savladano se ne vraca na potvrdu`() {
        val history = listOf(
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE),
            session(Skill.COORDINATES, "square_color", 10, 10, support = Support.NONE)
        )

        val recommendation = history.toProgressSnapshot()
            .suggest(listOf(coordinates))!!

        assertNotEquals(Reason.UPKEEP, recommendation.reason)
    }

    /**
     * Održavanje se tiče samo **savladanog**. Veština koja tek raste se vraća
     * kroz redovan izbor, ne kroz potvrdu — nema šta da potvrdi.
     */
    @Test
    fun `zapusteno a nesavladano ne ide na potvrdu`() {
        val history = listOf(session(Skill.COORDINATES, "square_color", 10, 4))
        val month = clock + 30L * 24 * 60 * 60 * 1000

        val recommendation = history.toProgressSnapshot()
            .recommend(listOf(coordinates), nowMillis = month)!!

        assertNotEquals(Reason.UPKEEP, recommendation.reason)
    }
}
