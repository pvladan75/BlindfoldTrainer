package com.program.blindfoldtrainer

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
}
