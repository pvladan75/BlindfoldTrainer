package com.program.blindfoldtrainer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.program.blindfoldtrainer.ModuleRegistry
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.ui.MainMenuScreen
import com.program.blindfoldtrainer.ui.ProgressViewModel
import com.program.blindfoldtrainer.ui.SessionSummaryDialog
import com.program.blindfoldtrainer.ui.VoiceModelViewModel

private const val ROUTE_MENU = "menu"
private const val ARG_MODULE = "module"
private const val ARG_DIFFICULTY = "difficulty"
private const val ROUTE_MODULE = "module/{$ARG_MODULE}/{$ARG_DIFFICULTY}"

private fun moduleRoute(moduleKey: String, difficulty: Difficulty) =
    "module/$moduleKey/${difficulty.name}"

/**
 * Ceo graf navigacije se sastoji od menija i **jedne** rute za module.
 *
 * Modul se ne pominje poimence nigde — koji će se ekran prikazati određuje
 * registar na osnovu ključa iz rute. Dodavanje modula zato ne dira ovaj fajl.
 */
@Composable
fun AppNavigation(registry: ModuleRegistry) {
    val navController = rememberNavController()

    // Traži se izvan NavHost-a, pa je vezan za aktivnost a ne za rutu —
    // napredak preživljava prelaz iz modula nazad u meni.
    val progressViewModel: ProgressViewModel = hiltViewModel()
    val progress by progressViewModel.snapshot.collectAsState()

    val voiceModelViewModel: VoiceModelViewModel = hiltViewModel()
    val voiceModel by voiceModelViewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = ROUTE_MENU) {

        composable(ROUTE_MENU) {
            MainMenuScreen(
                modules = registry.all,
                progress = progress,
                voiceModel = voiceModel,
                onDownloadVoiceModel = voiceModelViewModel::download,
                onCancelVoiceModel = voiceModelViewModel::cancel,
                onDeleteVoiceModel = voiceModelViewModel::delete,
                onStart = { module, difficulty ->
                    navController.navigate(moduleRoute(module.id.key, difficulty))
                }
            )
        }

        composable(
            route = ROUTE_MODULE,
            arguments = listOf(
                navArgument(ARG_MODULE) { type = NavType.StringType },
                navArgument(ARG_DIFFICULTY) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val moduleKey = backStackEntry.arguments?.getString(ARG_MODULE)
            val module = moduleKey?.let { registry.byKey(it) }

            val difficulty = backStackEntry.arguments
                ?.getString(ARG_DIFFICULTY)
                ?.let { name -> Difficulty.entries.find { it.name == name } }
                ?: Difficulty.EASY

            if (module == null) {
                // Ruta pokazuje na modul koji nije ugrađen u ovu verziju.
                // Umesto praznog ekrana, vraćamo korisnika u meni.
                navController.popBackStack(ROUTE_MENU, inclusive = false)
                return@composable
            }

            var result by remember { mutableStateOf<SessionResult?>(null) }

            module.Screen(
                args = ModuleArgs(difficulty = difficulty),
                onFinish = { sessionResult ->
                    result = sessionResult
                    progressViewModel.record(sessionResult)
                }
            )

            result?.let { finished ->
                val reward by progressViewModel.lastReward.collectAsState()

                SessionSummaryDialog(
                    result = finished,
                    reward = reward,
                    onRepeat = {
                        result = null
                        progressViewModel.onSummaryClosed()
                        navController.navigate(moduleRoute(module.id.key, difficulty)) {
                            popUpTo(ROUTE_MENU) { inclusive = false }
                        }
                    },
                    onBackToMenu = {
                        result = null
                        progressViewModel.onSummaryClosed()
                        navController.popBackStack(ROUTE_MENU, inclusive = false)
                    }
                )
            }
        }
    }
}
