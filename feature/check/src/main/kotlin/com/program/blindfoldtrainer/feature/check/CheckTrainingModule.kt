package com.program.blindfoldtrainer.feature.check

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

class CheckTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.CHECK
    override val titleRes = R.string.check_title
    override val descriptionRes = R.string.check_description
    override val iconRes = R.drawable.ic_check
    override val tasks = CHECK_TASKS
    override val defaultTaskId = CHECK_DEFAULT_TASK.id

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)


    override fun difficultyDetail(difficulty: Difficulty, taskId: String?): String? =
        difficultyDetailOf(difficulty)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        CheckScreen(
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
abstract class CheckModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindCheckModule(module: CheckTrainingModule): TrainingModule
}
