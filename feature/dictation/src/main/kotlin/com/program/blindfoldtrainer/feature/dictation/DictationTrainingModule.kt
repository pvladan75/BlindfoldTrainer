package com.program.blindfoldtrainer.feature.dictation

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

class DictationTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.DICTATION
    override val titleRes = R.string.dictation_title
    override val descriptionRes = R.string.dictation_description
    override val iconRes = R.drawable.ic_dictation
    override val tasks = listOf(DICTATION_PLACE_POSITION)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

    /** Samo govor: pozicija se izgovara, a odgovara se dodirom po tabli. */
    override val needs = setOf(Capability.SPEECH_OUTPUT)

    /**
     * Tabla je ovde **odgovor**, ne prikaz — figure se na nju spuštaju. Bez nje
     * vežbe nema, pa režim bez ekrana ovaj modul ne podnosi.
     */

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        DictationScreen(
            difficulty = args.difficulty,
            support = args.support,
            rounds = args.rounds,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DictationModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindDictationModule(module: DictationTrainingModule): TrainingModule
}
