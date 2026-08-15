package com.program.blindfoldtrainer.feature.followgame

import androidx.compose.runtime.Composable
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
    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        FollowGameScreen(difficulty = args.difficulty, onFinish = onFinish)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowGameModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindFollowGameModule(module: FollowGameTrainingModule): TrainingModule
}
