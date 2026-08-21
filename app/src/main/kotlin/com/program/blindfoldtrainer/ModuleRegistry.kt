package com.program.blindfoldtrainer

import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Checkups
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.skillFloors
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Svi moduli koji su ugrađeni u aplikaciju.
 *
 * Hilt sakuplja skup preko `@IntoSet`, pa je dovoljno da feature modul bude na
 * spisku zavisnosti `:app`-a — meni i navigacija se odatle generišu. Nema
 * odvojenog `when` bloka koji bi se razišao sa stvarnošću, što je u staroj
 * aplikaciji progutalo ceo modul 3.
 */
@Singleton
class ModuleRegistry @Inject constructor(
    modules: Set<@JvmSuppressWildcards TrainingModule>
) {
    /**
     * Redosled u meniju: **od lakšeg ka težem, po stablu veština**.
     *
     * Ranije je to bio redosled deklaracije u [ModuleId], pa je Završnica —
     * najteži modul u aplikaciji — stajala prva. Spisak se ne slaže ručno nego
     * se **izvodi iz `skillFloors()`**, iz istog razloga iz kog se i slika
     * zavisnosti u uputstvu crta iz `requires`: raspored koji se prepisuje se
     * pre ili kasnije raziđe sa onim što opisuje.
     *
     * Dva ključa, i oba su potrebna:
     *
     * 1. **Odakle modul počinje** — najniži sprat među veštinama koje njegovi
     *    zadaci **mere**. Modul je lak koliko i njegov najlakši ulaz, jer se
     *    kroz njega i ulazi.
     * 2. **Dokle doseže** — najviši sprat među svim veštinama koje dodiruje.
     *    Bez ovog drugog bi Završnica stajala uz Parove: obe počinju od
     *    ažuriranja pozicije, ali Završnica traži i računanje naslepo.
     *
     * Pri izjednačenju odlučuje redosled deklaracije, da poredak bude stabilan.
     * Modul koji se još nije izjasnio zadacima ide na kraj — o njemu se ne zna
     * gde spada, a nagađanje bi ga stavilo među lake.
     *
     * [ModuleId.ordinal] se nigde ne čuva — u bazu ide `key` — pa promena
     * redosleda ne dira ničiji napredak.
     */
    val all: List<TrainingModule> = modules.sortedWith(
        compareBy({ it.entryFloor() }, { it.reachFloor() }, { it.id.ordinal })
    )

    private val byId: Map<ModuleId, TrainingModule> = all.associateBy { it.id }

    operator fun get(id: ModuleId): TrainingModule? = byId[id]

    fun byKey(key: String): TrainingModule? = ModuleId.fromKey(key)?.let { byId[it] }

    /**
     * Provere koje se u **ovoj** verziji zaista mogu ponuditi.
     *
     * `Checkups.ALL` je spisak namera: veština, modul, zadatak. Ako modul nije
     * ugrađen, ako je zadatak preimenovan, ili ako zadatak meri drugu veštinu
     * nego što provera tvrdi — merenje bi se **tiho** izvelo pogrešno. Modul bez
     * porudžbine odradi svoj zatečeni zadatak, upiše rezultat, i profil dobije
     * nivo za veštinu koja nije ni vežbana.
     *
     * Zato se provera propušta tek kad se poklope sve troje. Nesaglasna se ne
     * nudi — isto pravilo po kom ruta ka neugrađenom modulu vraća u meni umesto
     * da otvori prazan ekran.
     */
    val offerableCheckups: List<Checkup> = Checkups.ALL.filter { checkup ->
        val task = byId[checkup.moduleId]?.tasks?.find { it.id == checkup.taskId }
        task != null && task.measures == checkup.skill
    }
}

/** Na kom spratu stablo veština drži koju veštinu. Računa se jednom. */
private val SKILL_FLOORS: Map<Skill, Int> =
    skillFloors().flatMapIndexed { floor, skills -> skills.map { it to floor } }.toMap()

/** Najniži sprat koji modul **meri** — odakle se u njega ulazi. */
private fun TrainingModule.entryFloor(): Int =
    tasks.minOfOrNull { SKILL_FLOORS[it.measures] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

/** Najviši sprat koji modul **dodiruje** — dokle doseže ono što traži. */
private fun TrainingModule.reachFloor(): Int =
    skills.maxOfOrNull { SKILL_FLOORS[it] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
