package com.program.blindfoldtrainer.feature.knightpath

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

class KnightPathTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.KNIGHT_PATH
    override val titleRes = R.string.knight_path_title
    override val descriptionRes = R.string.knight_path_description
    override val iconRes = R.drawable.ic_knight_path
    override val tasks = listOf(KNIGHT_SHORTEST_PATH)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        KnightPathScreen(
            difficulty = args.difficulty,
            support = args.support,
            rounds = args.rounds,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class KnightPathModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindKnightPathModule(module: KnightPathTrainingModule): TrainingModule
}
