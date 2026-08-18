package com.program.blindfoldtrainer.feature.pairs

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

class PairsTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.PAIRS
    override val titleRes = R.string.pairs_title
    override val descriptionRes = R.string.pairs_description
    override val iconRes = R.drawable.ic_pairs
    override val tasks = listOf(PAIRS_MEETING_SQUARE)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        PairsScreen(
            difficulty = args.difficulty,
            support = args.support,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PairsModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindPairsModule(module: PairsTrainingModule): TrainingModule
}
