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
import com.program.blindfoldtrainer.ui.SessionSummaryEyesFree
import com.program.blindfoldtrainer.ui.SettingsScreen
import com.program.blindfoldtrainer.ui.SettingsViewModel
import com.program.blindfoldtrainer.ui.SummaryViewModel

private const val ROUTE_MENU = "menu"
private const val ROUTE_SETTINGS = "settings"
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

    // I ovaj stoji izvan NavHost-a, ali iz drugog razloga: podešavanje mora biti
    // pročitano **pre** nego što sažetak zatreba. Da se traži tek uz sažetak,
    // prvi kadar bi dobio zatečenu vrednost i bez ekrana bi bljesnuo dijalog.
    val summaryViewModel: SummaryViewModel = hiltViewModel()
    val eyesFree by summaryViewModel.eyesFree.collectAsState()

    NavHost(navController = navController, startDestination = ROUTE_MENU) {

        composable(ROUTE_MENU) {
            val settings by hiltViewModel<SettingsViewModel>().settings.collectAsState()

            MainMenuScreen(
                modules = registry.all,
                progress = progress,
                eyesFree = settings.eyesFree,
                onStart = { module, difficulty ->
                    navController.navigate(moduleRoute(module.id.key, difficulty))
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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

                val repeatSession = {
                    result = null
                    progressViewModel.onSummaryClosed()
                    navController.navigate(moduleRoute(module.id.key, difficulty)) {
                        popUpTo(ROUTE_MENU) { inclusive = false }
                    }
                }
                val backToMenu = {
                    result = null
                    progressViewModel.onSummaryClosed()
                    navController.popBackStack(ROUTE_MENU, inclusive = false)
                    Unit
                }

                // Isti ishod, dva oblika: dijalog za onoga ko gleda, zone za
                // onoga ko ne gleda. Ishod i napredak su već upisani — ovo je
                // samo način da se do njih dođe.
                if (eyesFree) {
                    SessionSummaryEyesFree(
                        result = finished,
                        reward = reward,
                        onAnnounce = summaryViewModel::announce,
                        onSay = summaryViewModel::sayNow,
                        onRepeat = repeatSession,
                        onBackToMenu = backToMenu
                    )
                } else {
                    SessionSummaryDialog(
                        result = finished,
                        reward = reward,
                        onRepeat = repeatSession,
                        onBackToMenu = backToMenu
                    )
                }
            }
        }
    }
}
