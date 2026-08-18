package com.program.blindfoldtrainer.core.progress

import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.SkillTally
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.requires

/** Napredak u jednom modulu. */
data class ModuleProgress(
    val sessions: Int = 0,
    val attempted: Int = 0,
    val solved: Int = 0,
    val mistakes: Int = 0,
    val perfectSessions: Int = 0,
    val xp: Int = 0,
    val timeMillis: Long = 0,
    /** Najbolji rezultat po težini, u poenima. Za „lični rekord" u meniju. */
    val bestXpByDifficulty: Map<Difficulty, Int> = emptyMap()
) {
    val accuracy: Float
        get() = if (attempted == 0) 0f else solved.toFloat() / attempted

    operator fun plus(result: SessionResult): ModuleProgress {
        val gained = Xp.forSession(result)
        val best = bestXpByDifficulty[result.difficulty] ?: 0
        return ModuleProgress(
            sessions = sessions + 1,
            attempted = attempted + result.attempted,
            solved = solved + result.solved,
            mistakes = mistakes + result.mistakes,
            perfectSessions = perfectSessions + if (result.isPerfect) 1 else 0,
            xp = xp + gained,
            timeMillis = timeMillis + result.elapsedMillis,
            bestXpByDifficulty = bestXpByDifficulty + (result.difficulty to maxOf(best, gained))
        )
    }
}

/**
 * Ceo napredak korisnika, sabran iz istorije sesija.
 *
 * Ovo je izvedena vrednost, ne zapis: čuva se samo sirova istorija, a snimak se
 * računa iz nje. Zato promena pravila bodovanja ne ostavlja nesaglasne podatke.
 */
data class ProgressSnapshot(
    val totalXp: Int = 0,
    val sessions: Int = 0,
    val attempted: Int = 0,
    val solved: Int = 0,
    val perfectSessions: Int = 0,
    val timeMillis: Long = 0,
    val byModule: Map<ModuleId, ModuleProgress> = emptyMap(),
    /** Besprekorne sesije po težini — dostignuća razlikuju lako od teškog. */
    val perfectByDifficulty: Map<Difficulty, Int> = emptyMap(),
    /** Koliko besprekornih sesija traje u nizu upravo sada. */
    val perfectStreak: Int = 0,
    /** Najduži takav niz do sada. */
    val bestPerfectStreak: Int = 0,

    /**
     * Sirovi zapisi o veštinama, hronološki.
     *
     * **Čuva se sirovo, izvodi se sve ostalo** — isto pravilo po kom se poeni ne
     * pamte nego računaju. Zbir sabijen u jedan broj ne ume da kaže „nekad si
     * radio ovako, sad ovako", a baš to je ono što korisniku pokazuje napredak.
     */
    val skillHistory: List<SkillEntry> = emptyList()
) {
    val rank: Rank get() = Rank.forXp(totalXp)
    val rankProgress: RankProgress get() = RankProgress.forXp(totalXp)

    /** Moduli koje je korisnik bar jednom probao. */
    val startedModules: Set<ModuleId> get() = byModule.keys

    /** Osvojena dostignuća. Izvedena su iz stanja, pa se nigde ne pamte. */
    val achievements: Set<Achievement> get() = Achievement.earnedIn(this)

    /**
     * Profil: koliko je svaka veština vežbana, sa kojim uspehom i na kojoj
     * prečki. Izvedeno iz [skillHistory].
     *
     * Veština koje ovde nema **nije merena** — ne znači nula, nego da o njoj još
     * ništa ne znamo.
     */
    val bySkill: Map<Skill, SkillProfile> by lazy {
        skillHistory.filterNot { it.isCheckup }.fold(emptyMap()) { profiles, entry ->
            val current = profiles[entry.skill] ?: SkillProfile()
            profiles + (entry.skill to current.plus(entry.taskId, entry.support, entry.tally))
        }
    }

    /** Veštine o kojima uopšte ima podatka. Ostale stoje kao „nije mereno". */
    val measuredSkills: Set<Skill> get() = bySkill.keys

    /**
     * Da li je veština **automatska**, a ne samo tačna.
     *
     * Traži se oboje: prečka koju drži i brzina na njoj. Tačan ali spor odgovor
     * znači da veština još troši pažnju — a na takvoj se ne može graditi
     * sledeća, jer je radna memorija jedna.
     */
    fun isAutomatic(skill: Skill): Boolean {
        val task = bySkill[skill]?.furthest ?: return false
        val rung = task.heldRung() ?: return false
        val perAttempt = task.at(rung)?.millisPerAttempt ?: return false

        return perAttempt <= automaticMillisFor(skill)
    }

    /**
     * Temelji koji ovoj veštini fale — preduslovi koji još nisu automatski.
     *
     * Prazan skup ne znači da je veština laka, nego da je ništa ne koči.
     */
    fun foundationsMissing(skill: Skill): Set<Skill> =
        skill.requires.filterNotTo(mutableSetOf()) { isAutomatic(it) }

    /**
     * Kako je veština stajala **ranije** naspram toga kako stoji **sada**.
     *
     * Prozor je po **broju pokušaja, ne po danima**: ko vežba dvaput nedeljno
     * nema šta da vidi u „poslednja tri dana", a baš njemu trend najviše treba.
     * Datum se prikazuje uz to, kao podatak a ne kao mera.
     *
     * Gleda se **unutar jednog zadatka**. Trend preko modula bi poredio dve
     * sekunde po pitanju sa tri minuta po poziciji.
     */
    /**
     * Sesije jednog zadatka na jednoj prečki, hronološki — građa za grafik.
     *
     * Prečka je deo ključa, ne filter preko koga se prelazi: kriva koja meša
     * prečke ponovila bi grešku zbog koje se prečka uopšte i upisuje.
     */
    fun sessionsFor(skill: Skill, taskId: String, support: Support): List<SkillEntry> =
        skillHistory.filter {
            !it.isCheckup && it.skill == skill && it.taskId == taskId && it.support == support
        }

    /**
     * Sve provere jedne veštine, hronološki — merenja koja se smeju porediti.
     *
     * Odvojene su od vežbi jer mere drugu stvar: vežba kaže koliko si radio i
     * kuda ideš, provera kaže **gde stojiš**. Sabrati ih značilo bi razblažiti
     * jedino merenje koje je svima jednako.
     */
    fun checkupsFor(skill: Skill): List<SkillEntry> =
        skillHistory.filter { it.isCheckup && it.skill == skill }

    /**
     * Dokle se izdržalo pre prve greške — **poslednji put i najbolje do sada**.
     *
     * Tačnost od 70% ne kaže kako izgleda partija: neko greši ravnomerno, a
     * nekome se slika raspadne u šestom potezu pa dalje pogađa nasumično. Prvo
     * se popravlja vežbom, drugo je granica onoga što glava trenutno drži.
     */
    fun depthFor(taskId: String): Depth? {
        val measured = skillHistory.filter { !it.isCheckup && it.taskId == taskId }
            .mapNotNull { entry -> entry.heldUntil }

        if (measured.isEmpty()) return null

        return Depth(last = measured.last(), best = measured.max())
    }

    /** Poslednja provera veštine, ako je bilo ijedne. */
    fun lastCheckup(skill: Skill): SkillEntry? = checkupsFor(skill).lastOrNull()

    /** Veštine koje su ikad proverene — one za koje se zna **nivo**, ne samo obim. */
    val checkedSkills: Set<Skill>
        get() = skillHistory.filterTo(mutableSetOf()) { it.isCheckup }.mapTo(mutableSetOf()) { it.skill }

    fun trendFor(skill: Skill, taskId: String, window: Int = TREND_WINDOW): SkillTrend? {
        val entries = skillHistory.filter {
            !it.isCheckup && it.skill == skill && it.taskId == taskId
        }
        if (entries.isEmpty()) return null

        val recent = mutableListOf<SkillEntry>()
        var counted = 0
        for (entry in entries.asReversed()) {
            if (counted >= window) break
            recent += entry
            counted += entry.tally.attempted
        }

        val earlier = entries.dropLast(recent.size)
        return SkillTrend(
            recent = recent.fold(SkillTally(0, 0)) { sum, entry -> sum + entry.tally },
            earlier = earlier.fold(SkillTally(0, 0)) { sum, entry -> sum + entry.tally },
            recentSinceMillis = recent.minOfOrNull { it.atMillis }
        )
    }

    /**
     * Veština sa najslabijim učinkom, među **merenima**.
     *
     * Odavde kasnije kreće preporuka. Nemerena veština se namerno ne vraća: o
     * njoj se ne zna da je slaba, nego se ne zna ništa, a to su dve različite
     * stvari i ne smeju da se pomešaju.
     *
     * Poredi se učinak **na prečki koju veština drži** — inače bi onaj ko sve
     * radi uz punu podršku izgledao jači od onoga ko se muči bez table.
     */
    val weakestSkill: Skill?
        get() = bySkill.entries
            .filter { (_, profile) -> profile.attempted > 0 }
            .minByOrNull { (_, profile) -> profile.furthest?.standing ?: 0f }
            ?.key

    operator fun plus(result: SessionResult): ProgressSnapshot {
        val module = byModule[result.moduleId] ?: ModuleProgress()
        val streak = if (result.isPerfect) perfectStreak + 1 else 0
        return ProgressSnapshot(
            totalXp = totalXp + Xp.forSession(result),
            sessions = sessions + 1,
            attempted = attempted + result.attempted,
            solved = solved + result.solved,
            perfectSessions = perfectSessions + if (result.isPerfect) 1 else 0,
            timeMillis = timeMillis + result.elapsedMillis,
            byModule = byModule + (result.moduleId to (module + result)),
            perfectByDifficulty = if (result.isPerfect) {
                perfectByDifficulty + (result.difficulty to (perfectByDifficulty[result.difficulty] ?: 0) + 1)
            } else {
                perfectByDifficulty
            },
            perfectStreak = streak,
            bestPerfectStreak = maxOf(bestPerfectStreak, streak),
            // Sesija bez razlaganja, bez prečke ili bez vremena ne ulazi u
            // profil. Svako od to troje je deo podatka, ne ukras: bez njih se ne
            // zna koliko uspeh vredi, pa je bolje ne znati nego znati pogrešno.
            skillHistory = skillHistory + result.toSkillEntries()
        )
    }

    companion object {
        val EMPTY = ProgressSnapshot()
    }
}

/**
 * Koliko sme da traje jedan pokušaj da bi se veština smatrala **automatskom**.
 *
 * Brojevi su **prvi predlog**, kao i pragovi rangova — menjaju se na jednom
 * mestu i istorija se sama preračuna. Razlikuju se po veštini jer se razlikuje i
 * ono što se broji kao pokušaj: jedno pitanje o boji polja naspram cele
 * odigrane završnice.
 */
private fun automaticMillisFor(skill: Skill): Long = when (skill) {
    Skill.COORDINATES -> 2_500
    Skill.PIECE_GEOMETRY -> 8_000
    Skill.NOTATION -> 25_000
    Skill.POSITION_HOLD -> 25_000
    Skill.POSITION_UPDATE -> 20_000
    Skill.RECOVERY -> 25_000
    Skill.SQUARE_CONTROL -> 12_000
    Skill.CALCULATION -> 60_000
}

/**
 * Jedan zapis: šta je jedna sesija donela jednoj veštini.
 *
 * Ovo je najsitniji podatak koji se čuva. Sve iznad — profil, prečke, trend —
 * izvodi se iz spiska ovakvih zapisa.
 */
data class SkillEntry(
    val atMillis: Long,
    val skill: Skill,
    val taskId: String,
    val support: Support,
    val tally: SkillTally,
    /** Provera daje **nivo**, vežba daje **napredak** — ne mešaju se. */
    val isCheckup: Boolean = false,
    /** Dokle je izdržano pre prve greške, gde se to meri. */
    val heldUntil: Int? = null
)

/** Dokle se izdržalo pre prve greške: poslednji put i najbolje do sada. */
data class Depth(val last: Int, val best: Int)

/** Kako veština stoji sada naspram toga kako je stajala ranije. */
data class SkillTrend(
    val recent: SkillTally,
    val earlier: SkillTally,
    /** Kad je počeo skorašnji prozor — za prikaz, ne za meru. */
    val recentSinceMillis: Long?
) {
    /** Ima li se sa čim porediti; jedan prozor bez drugog nije trend. */
    val hasComparison: Boolean get() = earlier.attempted > 0 && recent.attempted > 0
}

private const val TREND_WINDOW = 20

/**
 * Zapisi iz jedne sesije, ili prazno ako sesija nije merljiva.
 *
 * Traži se i razlaganje i prečka i vreme završetka — bez ijednog od to troje
 * zapis ne bi umeo da odgovori na pitanje zbog kog postoji.
 */
private fun SessionResult.toSkillEntries(): List<SkillEntry> {
    val support = support ?: return emptyList()
    val at = finishedAtMillis ?: return emptyList()
    val task = taskId ?: return emptyList()

    return bySkill.map { (skill, tally) ->
        SkillEntry(at, skill, task, support, tally, isCheckup, heldUntil)
    }
}

/**
 * Šta se o jednoj veštini zna **u jednom zadatku**, po prečkama.
 *
 * Zašto po zadatku: „jedan pokušaj" nije ista stvar u dva modula — pitanje u
 * Geometriji traje dve sekunde, pozicija u Završnici tri minuta, a oba su jedan
 * pokušaj. Prosek preko toga ne meri ništa, a tačnost pada čim se pređe na teži
 * modul, pa merilo kažnjava baš ono što treba da nagradi.
 *
 * Zašto po prečkama: uspeh uz punu podršku i uspeh bez table nisu isti dokaz.
 */
data class TaskProfile(val bySupport: Map<Support, SkillTally> = emptyMap()) {

    val attempted: Int get() = bySupport.values.sumOf { it.attempted }
    val solved: Int get() = bySupport.values.sumOf { it.solved }

    fun at(support: Support): SkillTally? = bySupport[support]

    /** Prečke na kojima je veština uopšte probana, od najlakše ka najtežoj. */
    val triedRungs: List<Support> get() = bySupport.keys.sortedBy { it.ordinal }

    /**
     * Najteža prečka koju veština **drži**.
     *
     * Drži je kad je na njoj bilo dovoljno pokušaja i dovoljno tačno. Jedan
     * srećan pogodak bez table nije dokaz, pa prag postoji — a jeste nizak, jer
     * je ovo merilo napretka a ne ispit.
     */
    fun heldRung(minAttempts: Int = 8, minAccuracy: Float = 0.8f): Support? =
        bySupport.entries
            .filter { (_, tally) ->
                tally.attempted >= minAttempts &&
                    tally.solved.toFloat() / tally.attempted >= minAccuracy
            }
            .maxByOrNull { (support, _) -> support.ordinal }
            ?.key

    /**
     * Jedan broj za poređenje veština, u kom **prečka vredi više od procenta**.
     *
     * Bez toga bi onaj ko sve radi uz punu podršku izgledao jači od onoga ko se
     * muči bez table — a upravo je ovaj drugi dalje odmakao.
     */
    val standing: Float
        get() {
            if (attempted == 0) return 0f
            return bySupport.entries.sumOf { (support, tally) ->
                val accuracy = if (tally.attempted == 0) 0.0 else tally.solved.toDouble() / tally.attempted
                (accuracy * (support.ordinal + 1) * tally.attempted)
            }.toFloat() / bySupport.values.sumOf { it.attempted }
        }

    /**
     * Da li je orijentir na ovoj prečki **dostignut**.
     *
     * Traži se i vreme i tačnost — da stoji samo vreme, merilo bi pozivalo na
     * žurbu. Traži se i dovoljno pokušaja, iz istog razloga iz kog se traži i za
     * držanu prečku: jedna dobra večer nije dokaz.
     */
    fun hasReached(support: Support, benchmark: Benchmark, minAttempts: Int = 8): Boolean {
        val tally = bySupport[support] ?: return false
        if (tally.attempted < minAttempts) return false

        val perAttempt = tally.millisPerAttempt ?: return false
        val accuracy = tally.solved.toFloat() / tally.attempted

        return perAttempt <= benchmark.millisPerAttempt && accuracy >= benchmark.minAccuracy
    }

    internal fun plus(support: Support, tally: SkillTally): TaskProfile =
        TaskProfile(bySupport + (support to ((bySupport[support] ?: SkillTally(0, 0)) + tally)))
}

/**
 * Šta se o veštini zna — **razloženo po zadacima**, bez zbira preko njih.
 *
 * Jedan broj za celu veštinu ovde namerno **ne postoji**. Ažuriranje pozicije u
 * Parovima je nekoliko poteza, u Prati partiju desetine, u Završnici uz
 * protivnika koji se brani — tri stepena iste veštine, a zbir ih sakrije.
 *
 * Poređivi nivo daće **provera**: kratka, uvek ista i svima jednaka. Dotle je
 * ovo procena, i tako se i prikazuje.
 */
data class SkillProfile(val byTask: Map<String, TaskProfile> = emptyMap()) {

    /** Koliko je ukupno vežbano — obim se sme sabrati, učinak ne. */
    val attempted: Int get() = byTask.values.sumOf { it.attempted }

    /** Zadaci u kojima je veština merena, od najdalje odmaklog. */
    val tasks: List<Pair<String, TaskProfile>>
        get() = byTask.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, TaskProfile>> {
                    it.value.heldRung()?.ordinal ?: -1
                }.thenByDescending { it.value.standing }
            )
            .map { it.key to it.value }

    /** Zadatak u kom je veština najdalje stigla — merodavan za procenu. */
    val furthest: TaskProfile? get() = tasks.firstOrNull()?.second

    internal fun plus(taskId: String, support: Support, tally: SkillTally): SkillProfile {
        val current = byTask[taskId] ?: TaskProfile()
        return SkillProfile(byTask + (taskId to current.plus(support, tally)))
    }
}

/**
 * Sabira istoriju u snimak. **Redosled je bitan** — niz besprekornih sesija se
 * prekida prvom u kojoj ima greške, pa istorija mora stizati hronološki.
 */
fun Iterable<SessionResult>.toProgressSnapshot(): ProgressSnapshot =
    fold(ProgressSnapshot.EMPTY) { snapshot, result -> snapshot + result }
