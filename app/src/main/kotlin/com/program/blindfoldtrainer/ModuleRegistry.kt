package com.program.blindfoldtrainer

import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Checkups
import com.program.blindfoldtrainer.core.model.ModuleId
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
    /** Redosled je redosled deklaracije u [ModuleId] — ne slučajan poredak skupa. */
    val all: List<TrainingModule> = modules.sortedBy { it.id.ordinal }

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
