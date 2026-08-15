package com.program.blindfoldtrainer.core.moduleapi

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult

/**
 * Jedan modul za trening.
 *
 * Moduli se **ne** upisuju ručno u navigaciju. Svaki se prijavljuje u registar
 * preko Hilt-ovog `@IntoSet`, a školjka iz registra generiše i glavni meni i
 * rute. U staroj aplikaciji je modul 3 nestao zato što je iz `when` bloka u
 * navigaciji ispala jedna linija — modul je postojao, ViewModel je radio, ali
 * do njega se nije moglo doći i ništa to nije prijavilo. Sa registrom takav
 * raskorak nije moguć.
 */
interface TrainingModule {

    val id: ModuleId

    @get:StringRes
    val titleRes: Int

    @get:StringRes
    val descriptionRes: Int

    @get:DrawableRes
    val iconRes: Int

    /** Težine koje ovaj modul nudi. Prazna lista znači da modul nema težine. */
    val difficulties: List<Difficulty>
        get() = Difficulty.entries

    /**
     * Šta modulu treba od školjke. Školjka na osnovu ovoga traži dozvolu za
     * mikrofon i podiže Stockfish **pre** ulaska u modul, umesto da svaki modul
     * to petlja sam.
     */
    val needs: Set<Capability>
        get() = emptySet()

    /**
     * Ekran modula.
     *
     * Modul **mora** pozvati [onFinish] kad sesija dođe do kraja — to je jedini
     * kanal kojim rezultat stiže do bodovanja i napretka.
     */
    @Composable
    fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit)
}

/** Ono što školjka prosleđuje modulu pri ulasku. */
data class ModuleArgs(
    val difficulty: Difficulty = Difficulty.EASY
)
