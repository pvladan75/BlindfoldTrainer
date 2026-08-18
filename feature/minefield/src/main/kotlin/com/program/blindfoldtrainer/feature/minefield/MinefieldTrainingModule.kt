package com.program.blindfoldtrainer.feature.minefield

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

class MinefieldTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.MINEFIELD
    override val titleRes = R.string.minefield_title
    override val descriptionRes = R.string.minefield_description
    override val iconRes = R.drawable.ic_minefield
    override val tasks = MINEFIELD_TASKS

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        MinefieldScreen(
            difficulty = args.difficulty,
            onFinish = onFinish,
            support = args.support,
            taskId = args.taskId
        )
    }
}

/** Prijava u registar; meni i navigacija se dalje popune sami. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MinefieldModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindMinefieldModule(module: MinefieldTrainingModule): TrainingModule
}
