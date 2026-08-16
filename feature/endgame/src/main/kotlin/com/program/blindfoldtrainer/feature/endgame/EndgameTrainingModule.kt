package com.program.blindfoldtrainer.feature.endgame

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

class EndgameTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.ENDGAME
    override val titleRes = R.string.endgame_title
    override val descriptionRes = R.string.endgame_description
    override val iconRes = R.drawable.ic_endgame
    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.ENGINE, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        EndgameScreen(difficulty = args.difficulty, onFinish = onFinish)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EndgameModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindEndgameModule(module: EndgameTrainingModule): TrainingModule
}
