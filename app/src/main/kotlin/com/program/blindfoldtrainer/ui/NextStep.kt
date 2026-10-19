package com.program.blindfoldtrainer.ui

import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.Recommendation

/**
 * Šta kartica **„Sledeće"** nudi: jedno merenje ili jednu vežbu.
 *
 * Dotle su na početnom ekranu stajale **dve** kartice jedna ispod druge —
 * Predlog i Provera — svaka sa svojim dugmetom. Dve ponude su dva pitanja, a
 * čovek koji otvori aplikaciju na minut hoće jedno: šta sad. Zato se ovde bira
 * jedna, a druga ostaje kao red teksta ispod nje — da se ne izgubi, ali ni da
 * se ne nameće.
 *
 * Čist Kotlin, bez Androida — pravilo se testira bez emulatora.
 */
sealed interface NextStep {
    data class Measure(val checkup: Checkup) : NextStep
    data class Practise(val recommendation: Recommendation) : NextStep
}

/** Glavna ponuda i, ako je ima, ona druga — kao tiha alternativa. */
data class NextOffer(val primary: NextStep, val alternative: NextStep?)

/**
 * Bira šta ide prvo.
 *
 * Pravilo je **smena**: provera ide prva dok veština koju meri nije nijednom
 * izmerena, ali ne dva puta zaredom — između dve provere stane vežba. Tako
 * prva stvar u aplikaciji ostaje kratka provera (profil mora odnekud da
 * počne), a čovek koji je šest puta zaredom pozvan da „meri" ne dobije utisak
 * da se ovde samo meri. Kad su sve merljive veštine jednom izmerene, vežba je
 * uvek prva, a ponovno merenje čeka u drugom redu.
 */
fun chooseNext(
    recommendation: Recommendation?,
    checkup: Checkup?,
    progress: ProgressSnapshot
): NextOffer? {
    val measure = checkup?.let { NextStep.Measure(it) }
    val practise = recommendation?.let { NextStep.Practise(it) }

    if (measure == null && practise == null) return null
    if (measure == null) return NextOffer(practise!!, null)
    if (practise == null) return NextOffer(measure, null)

    val neverMeasured = progress.lastCheckup(checkup!!.skill) == null
    val justMeasured = progress.skillHistory.lastOrNull()?.isCheckup == true

    return if (neverMeasured && !justMeasured) {
        NextOffer(primary = measure, alternative = practise)
    } else {
        NextOffer(primary = practise, alternative = measure)
    }
}

/**
 * Sa čime modul **kreće** kad se otvori sa spiska: zadatak, oslonac i težina.
 *
 * [support] je `null` kad se oslonac na stranici ne bira — tada odlučuje
 * podešavanje, kao i dotad. [shownSupport] je ono što će se u tom slučaju
 * zaista dogoditi, da red na spisku ne bude prazan.
 */
data class ModuleChoice(
    val taskId: String?,
    val support: Support?,
    val shownSupport: Support?,
    val difficulty: Difficulty
)

/**
 * **Nastavlja se gde se stalo.** Modul kreće sa zadatkom, osloncem i težinom
 * poslednje vežbe u njemu; ko ga otvara prvi put dobija zatečeni zadatak,
 * najviše pomoći (ili najmanje, ako obično vežba bez ekrana) i najlakšu težinu.
 *
 * Dotle je svaki ulaz kretao od početka, pa je čovek koji je juče stigao do
 * „samo tragovi" danas opet gledao „najviše pomoći" — i birao iznova, svaki put.
 */
fun TrainingModule.startingChoice(progress: ProgressSnapshot, eyesFree: Boolean): ModuleChoice {
    val taskIds = tasks.map { it.id }.toSet()
    val last = progress.skillHistory.lastOrNull { !it.isCheckup && it.taskId in taskIds }

    val taskId = last?.taskId ?: defaultTaskId ?: tasks.firstOrNull()?.id
    val task = tasks.find { it.id == taskId }

    val rungs = task?.supports?.sortedBy { it.ordinal }
        ?: tasks.flatMap { it.supports }.distinct().sortedBy { it.ordinal }

    // Oslonac se **nudi** samo kad je lestvica. Gde su to samo dva načina rada
    // — gledaš ili ne gledaš — odlučuje podešavanje, jednom za sve module.
    val isLadder = task?.supportIsLadder != false
    val pickable = rungs.size > 1 && isLadder

    val shown = last?.support?.takeIf { pickable && it in rungs }
        ?: if (eyesFree) rungs.lastOrNull() else rungs.firstOrNull()

    val difficulty = last?.difficulty?.takeIf { it in difficulties }
        ?: difficulties.firstOrNull()
        ?: Difficulty.EASY

    return ModuleChoice(
        taskId = taskId,
        support = shown.takeIf { pickable },
        shownSupport = shown,
        difficulty = difficulty
    )
}
