package com.program.blindfoldtrainer.feature.geometry

import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class GeometryTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.GEOMETRY
    override val titleRes = R.string.geometry_title
    override val descriptionRes = R.string.geometry_description
    override val iconRes = R.drawable.ic_geometry
    override val tasks = listOf(SQUARE_COLOR)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT)


    override fun difficultyDetail(difficulty: Difficulty, taskId: String?): String? =
        difficultyDetailOf(difficulty)

    /**
     * Ovde „uz tablu" **ne znači da tabla stoji dok odgovaraš** — boja polja se
     * zna napamet ili se ne zna. Tabla dolazi posle odgovora, da se vidi gde je
     * polje bilo; to je razlika između testa i vežbe.
     */
    override fun supportDetail(support: Support, taskId: String?): String = when (support) {
        Support.NONE -> "polje se izgovara, istina takođe"
        else -> "tabla se pokaže tek posle odgovora"
    }

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        GeometryScreen(
            difficulty = args.difficulty,
            onFinish = onFinish,
            rounds = args.rounds,
            support = args.support
        )
    }
}

/**
 * Prijava u registar. Ovim je modul dostupan iz menija i navigacije — nema
 * zasebnog spiska koji bi neko zaboravio da dopuni.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GeometryModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindGeometryModule(module: GeometryTrainingModule): TrainingModule
}
