package com.program.blindfoldtrainer.feature.followgame

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

class FollowGameTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.FOLLOW_GAME
    override val titleRes = R.string.follow_game_title
    override val descriptionRes = R.string.follow_game_description
    override val iconRes = R.drawable.ic_follow_game
    override val tasks = listOf(FOLLOW_WHERE_IS_PIECE)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        FollowGameScreen(
            difficulty = args.difficulty,
            support = args.support,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowGameModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindFollowGameModule(module: FollowGameTrainingModule): TrainingModule
}
