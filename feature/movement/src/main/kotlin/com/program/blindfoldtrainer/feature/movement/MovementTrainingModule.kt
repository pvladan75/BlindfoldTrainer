package com.program.blindfoldtrainer.feature.movement

import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * **Kretanje figura** — jedna figura po praznoj tabli.
 *
 * Jedini modul bez pozicije, i jedini koji nema nijednu prečku sa tablom: uz
 * tablu bi se odgovor pročitao umesto izračunao.
 */
class MovementTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.MOVEMENT
    override val titleRes = R.string.movement_title
    override val descriptionRes = R.string.movement_description
    override val iconRes = R.drawable.ic_movement
    override val tasks = MOVEMENT_TASKS

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        MovementScreen(
            difficulty = args.difficulty,
            taskId = args.taskId,
            rounds = args.rounds,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MovementModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindMovementModule(module: MovementTrainingModule): TrainingModule
}
