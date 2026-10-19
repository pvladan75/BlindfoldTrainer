package com.program.blindfoldtrainer.ui

import com.program.blindfoldtrainer.core.model.Benchmark
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SkillLevel
import com.program.blindfoldtrainer.core.progress.levelOf

/**
 * **Šta je ova sesija pomerila** — za veštinu koju je zadatak merio.
 *
 * Sažetak je dotle govorio koliko je rešeno i koliko je poena stiglo; to kaže
 * kako je prošla jedna sesija, ne dokle si stigao. Ovo je ono drugo: nivo
 * veštine pre i posle, i da li je orijentir na ovoj prečki ispunjen — jedini
 * trenutak u kom se profil vidi a da se ne otvori nijedan ekran.
 *
 * Čist Kotlin, bez Androida — računica se testira bez emulatora.
 */
data class SessionShift(
    val skill: Skill,
    val before: SkillLevel,
    val after: SkillLevel,
    /** Orijentir na prečki ove sesije; `null` gde ga nema ili prečka nije javljena. */
    val benchmark: Benchmark?,
    /** Koliko je u proseku trajao jedan pokušaj; `null` bez ijednog pokušaja. */
    val millisPerAttempt: Long?,
    /** Tačnost ove sesije, da stoji uz orijentir. */
    val accuracy: Float,
    /** Da li je ova sesija, sama za sebe, ispunila orijentir — i vreme i tačnost. */
    val metBenchmark: Boolean
) {
    val levelChanged: Boolean get() = before != after
}

/**
 * Izračunaj pomak sesije: [before] je napredak pre upisa, [after] posle.
 *
 * `null` kad sesija ne nosi zadatak koji registar poznaje — tada se ne zna ni
 * koja je veština merena, pa se ne tvrdi ništa.
 */
fun shiftOf(
    result: SessionResult,
    before: ProgressSnapshot,
    after: ProgressSnapshot,
    tasks: List<TaskSpec>
): SessionShift? {
    val task = tasks.find { it.id == result.taskId } ?: return null
    val skill = task.measures

    val benchmark = result.support?.let { task.benchmarkFor(it) }
    val perAttempt = if (result.attempted > 0) result.elapsedMillis / result.attempted else null

    // **Par, a ne broj**: orijentir je i vreme i tačnost, i priznaje se tek kad
    // su ispunjena oba — inače bi merilo pozivalo na žurbu.
    val met = benchmark != null && perAttempt != null &&
        perAttempt <= benchmark.millisPerAttempt &&
        result.accuracy >= benchmark.minAccuracy

    return SessionShift(
        skill = skill,
        before = before.levelOf(skill, tasks),
        after = after.levelOf(skill, tasks),
        benchmark = benchmark,
        millisPerAttempt = perAttempt,
        accuracy = result.accuracy,
        metBenchmark = met
    )
}
