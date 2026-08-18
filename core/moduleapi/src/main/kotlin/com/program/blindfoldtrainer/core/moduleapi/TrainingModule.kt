package com.program.blindfoldtrainer.core.moduleapi

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.TaskSpec

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
     * Vrste zadataka koje ovaj modul ume da izgeneriše.
     *
     * Odavde školjka zna **šta modul razvija** i **do koje prečke podrške ume**,
     * a put kasnije zna šta sme da poruči. Prazno znači „modul se još nije
     * izjasnio" — takav modul radi kao i pre, ali ne ulazi ni u profil ni u put.
     *
     * Veštine stoje na zadatku a ne na modulu jer isti modul sme da pita stvari
     * koje ne mere isto: „gde je skakač" je ažuriranje, „ko napada skakača" je
     * kontrola polja.
     */
    val tasks: List<TaskSpec>
        get() = emptyList()

    /** Sve veštine ovog modula — **unija zadataka**, da se ne prepisuje ručno. */
    val skills: Set<Skill>
        get() = tasks.flatMapTo(mutableSetOf()) { it.skills }

    /**
     * Da li modul ume da se odradi bez gledanja u ekran.
     *
     * Nije svaka vežba prevodiva na zone i glas: „Zapamti poziciju" se rešava
     * vraćanjem figura iz palete na tablu, a glasovni unos prepoznaje samo
     * polja — ne i figure. Meni na osnovu ovoga kaže korisniku koji modul mu
     * uz uključen režim **neće** raditi, umesto da to sazna tek unutra.
     */
    val supportsEyesFree: Boolean
        get() = true

    /**
     * Ekran modula.
     *
     * Modul **mora** pozvati [onFinish] kad sesija dođe do kraja — to je jedini
     * kanal kojim rezultat stiže do bodovanja i napretka.
     */
    @Composable
    fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit)
}

/**
 * Ono što školjka prosleđuje modulu pri ulasku.
 *
 * [taskId] i [support] su **porudžbina**: put traži baš određenu vrstu zadatka
 * na određenoj prečki. Kad ih nema — slobodno vežbanje iz menija — modul bira
 * sam, po [difficulty] i po podrazumevanoj prečki iz podešavanja.
 *
 * [difficulty] i [support] nisu isto i ne zamenjuju se: težina je **koliko i
 * koliko brzo**, podrška je **koliko pomoći**.
 */
data class ModuleArgs(
    val difficulty: Difficulty = Difficulty.EASY,
    val taskId: String? = null,
    val support: Support? = null
)
