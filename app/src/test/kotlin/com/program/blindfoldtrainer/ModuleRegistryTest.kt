package com.program.blindfoldtrainer

import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Meni ide **od lakšeg ka težem, po stablu veština** — i to se izvodi, ne
 * prepisuje. Test drži baš to: da se poredak ne razilazi sa `skillFloors()`.
 */
class ModuleRegistryTest {

    private fun module(id: ModuleId, vararg tasks: TaskSpec) = object : TrainingModule {
        override val id = id
        override val titleRes = 0
        override val descriptionRes = 0
        override val iconRes = 0
        override val tasks = tasks.toList()

        @Composable
        override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) = Unit
    }

    private fun task(id: String, vararg skills: Skill) =
        TaskSpec(id = id, skills = skills.toList(), supports = listOf(Support.FULL))

    /** Koordinate su temelj, ažuriranje je dva sprata iznad. */
    @Test
    fun `nizi sprat ide pre viseg`() {
        val update = module(ModuleId.PAIRS, task("pairs", Skill.POSITION_UPDATE))
        val coordinates = module(ModuleId.GEOMETRY, task("color", Skill.COORDINATES))

        val order = ModuleRegistry(setOf(update, coordinates)).all.map { it.id }

        assertEquals(listOf(ModuleId.GEOMETRY, ModuleId.PAIRS), order)
    }

    /** Modul je lak koliko i njegov najlakši ulaz — kroz njega se i ulazi. */
    @Test
    fun `modul se svrstava po najlaksem zadatku koji meri`() {
        val onlyUpdate = module(ModuleId.PAIRS, task("pairs", Skill.POSITION_UPDATE))
        val alsoGeometry = module(
            ModuleId.CHECK,
            task("safe_path", Skill.SQUARE_CONTROL),
            task("no_capture", Skill.PIECE_GEOMETRY)
        )

        val order = ModuleRegistry(setOf(onlyUpdate, alsoGeometry)).all.map { it.id }

        assertEquals(listOf(ModuleId.CHECK, ModuleId.PAIRS), order)
    }

    /**
     * Bez drugog ključa bi Završnica stajala uz Parove: obe počinju od
     * ažuriranja pozicije, ali Završnica traži i računanje naslepo.
     */
    @Test
    fun `sa istog sprata prvo ide onaj koji manje doseze`() {
        val endgame = module(
            ModuleId.ENDGAME,
            task("play_out", Skill.POSITION_UPDATE, Skill.CALCULATION)
        )
        val pairs = module(ModuleId.PAIRS, task("pairs", Skill.POSITION_UPDATE))

        val order = ModuleRegistry(setOf(endgame, pairs)).all.map { it.id }

        assertEquals(listOf(ModuleId.PAIRS, ModuleId.ENDGAME), order)
    }

    /** O modulu bez zadataka se ne zna gde spada; nagađanje bi ga stavilo među lake. */
    @Test
    fun `modul bez zadataka ide na kraj`() {
        val silent = module(ModuleId.RECALL)
        val calculation = module(ModuleId.ENDGAME, task("play_out", Skill.CALCULATION))

        val order = ModuleRegistry(setOf(silent, calculation)).all.map { it.id }

        assertEquals(listOf(ModuleId.ENDGAME, ModuleId.RECALL), order)
    }

    /** Pri potpunom izjednačenju odlučuje deklaracija, da poredak bude stabilan. */
    @Test
    fun `izjednaceni moduli zadrzavaju redosled deklaracije`() {
        val later = module(ModuleId.DICTATION, task("dictation", Skill.NOTATION))
        val earlier = module(ModuleId.RECALL, task("recall", Skill.NOTATION))

        val order = ModuleRegistry(setOf(later, earlier)).all.map { it.id }

        assertEquals(listOf(ModuleId.RECALL, ModuleId.DICTATION), order)
    }
}
