package com.program.blindfoldtrainer.feature.movement

import androidx.compose.runtime.Composable
import com.program.blindfoldtrainer.core.model.Capability
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.ModuleId
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.moduleapi.TrainingModule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * **Kretanje figura** — jedna figura po praznoj tabli, u oba smera.
 *
 * „Reci pa vidi" su šetnje: sam biraš polja, pa ti se posle pokaže kuda si
 * prošao. „Vidi pa reci" je prepričavanje: tabla nacrta putanju, pa je ti
 * ispričaš.
 *
 * Tabla se u šetnjama **ne vidi dok se radi** — uz nju bi se odgovor pročitao
 * umesto izračunao. U prepričavanju je tabla samo pitanje, pa tamo i stoji.
 */
class MovementTrainingModule @Inject constructor() : TrainingModule {

    override val id = ModuleId.MOVEMENT
    override val titleRes = R.string.movement_title
    override val descriptionRes = R.string.movement_description
    override val iconRes = R.drawable.ic_movement
    override val tasks = MOVEMENT_TASKS

    override val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
    override val needs = setOf(Capability.SPEECH_OUTPUT, Capability.VOICE_INPUT)

    override fun difficultyDetail(difficulty: Difficulty, taskId: String?): String =
        difficultyDetailOf(difficulty, taskId ?: defaultTaskId)

    /**
     * Oslonac ovde znači **koliko traga ostaje iza figure** dok se putanja crta,
     * a ne stoji li tabla pred tobom. Tabla u „Prepričaj putanju" i jeste samo
     * pitanje; u šetnjama je nema dok se radi, pa tamo nema ni šta da se kaže.
     */
    override fun supportDetail(support: Support, taskId: String?): String? {
        if (taskId != RETELL_PATH.id) return null

        return when (support) {
            Support.FULL -> "ceo trag ostaje — putanja se pročita sa table"
            Support.PARTIAL -> "iza figure ostaju dva polja"
            else -> "vidi se samo polje na kom figura stoji"
        }
    }

    @Composable
    override fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit) {
        MovementScreen(
            difficulty = args.difficulty,
            taskId = args.taskId,
            support = args.support,
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
