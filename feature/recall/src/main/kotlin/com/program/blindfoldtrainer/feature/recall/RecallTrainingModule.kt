package com.program.blindfoldtrainer.feature.recall

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

class RecallTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.RECALL
    override val titleRes = R.string.recall_title
    override val descriptionRes = R.string.recall_description
    override val iconRes = R.drawable.ic_recall
    override val tasks = listOf(RECALL_RECONSTRUCT)

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

    /**
     * Jedini modul bez režima za zatvorene oči. Rekonstrukcija ide vraćanjem
     * figura iz palete na tablu, a glasovni unos prepoznaje samo polja — figuru
     * nema čime da izgovori. Zone tu ne pomažu; odloženo dok se ne dogovori
     * drugačiji oblik pitanja.
     */

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        RecallScreen(
            difficulty = args.difficulty,
            support = args.support,
            onFinish = onFinish
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecallModuleBindings {

    @Binds
    @IntoSet
    abstract fun bindRecallModule(module: RecallTrainingModule): TrainingModule
}
