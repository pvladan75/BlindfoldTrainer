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
import com.program.blindfoldtrainer.core.model.Checkups
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Skill
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.ui.GuideScreen
import com.program.blindfoldtrainer.ui.MainMenuScreen
import com.program.blindfoldtrainer.core.progress.recommend
import com.program.blindfoldtrainer.ui.PersistenceScreen
import com.program.blindfoldtrainer.ui.ProfilesScreen
import com.program.blindfoldtrainer.ui.ProfilesViewModel
import com.program.blindfoldtrainer.ui.ProgressScreen
import com.program.blindfoldtrainer.ui.ProgressViewModel
import com.program.blindfoldtrainer.ui.SessionSummaryDialog
import com.program.blindfoldtrainer.ui.SessionSummaryEyesFree
import com.program.blindfoldtrainer.ui.SnapshotScreen
import com.program.blindfoldtrainer.ui.SettingsScreen
import com.program.blindfoldtrainer.ui.SettingsViewModel
import com.program.blindfoldtrainer.ui.SummaryViewModel

private const val ROUTE_MENU = "menu"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PROGRESS = "progress"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_GUIDE = "guide"
private const val ROUTE_SNAPSHOT = "snapshot"
private const val ROUTE_PERSISTENCE = "persistence"
private const val ARG_MODULE = "module"
private const val ARG_DIFFICULTY = "difficulty"
private const val ARG_TASK = "task"
private const val ARG_SUPPORT = "support"
private const val ARG_ROUNDS = "rounds"
private const val ARG_CHECKUP = "checkup"

/**
 * Da li se posle sesije vraća u **presek** umesto u meni.
 *
 * Presek se skuplja iz više merenja; ko ga je otvorio da izmeri četiri
 * veštine ne želi da posle svake bude vraćen na početak.
 */
private const val ARG_FROM_SNAPSHOT = "fromSnapshot"

/**
 * Jedna ruta za sve module, sa **porudžbinom** kao neobaveznim delom.
 *
 * Bez porudžbine je slobodno vežbanje iz menija i modul bira sam. Sa njom put
 * ili provera traže određen zadatak na određenoj prečki.
 */
private const val ROUTE_MODULE =
    "module/{$ARG_MODULE}/{$ARG_DIFFICULTY}?$ARG_TASK={$ARG_TASK}" +
        "&$ARG_SUPPORT={$ARG_SUPPORT}&$ARG_CHECKUP={$ARG_CHECKUP}" +
        "&$ARG_FROM_SNAPSHOT={$ARG_FROM_SNAPSHOT}"

private fun moduleRoute(
    moduleKey: String,
    difficulty: Difficulty,
    taskId: String? = null,
    support: Support? = null,
    isCheckup: Boolean = false,
    rounds: Int? = null,
    fromSnapshot: Boolean = false
): String = buildString {
    append("module/$moduleKey/${difficulty.name}")
    append("?$ARG_TASK=${taskId.orEmpty()}")
    append("&$ARG_SUPPORT=${support?.key.orEmpty()}")
    append("&$ARG_CHECKUP=$isCheckup")
    // Nula znači „nije poručeno" — `NavType.IntType` ne ume `null`.
    append("&$ARG_ROUNDS=${rounds ?: 0}")
    append("&$ARG_FROM_SNAPSHOT=$fromSnapshot")
}

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
    // Koju proveru ponuditi: prvo neproverenu veštinu, pa onu najstariju.
    // Provera je predlog, ne obaveza — meni ostaje netaknut ispod nje.
    val nextCheckup = remember(progress, registry) {
        registry.offerableCheckups.minByOrNull { progress.lastCheckup(it.skill)?.atMillis ?: 0L }
    }

    // Predlog puta: cilj iz onoga što se zna, korak iz poslednje vežbe. Računa
    // se ovde, uz ostali napredak, jer mu treba i registar zadataka.
    val allTasks = remember(registry) { registry.all.flatMap { it.tasks } }

    // Težine deklariše modul, a put poznaje samo zadatke — pa se veza pravi
    // ovde, gde se registar ionako drži. Bez nje bi predlog nudio težinu koju
    // modul ne ume, što je isti tihi raskorak zbog kog se i provere prosejavaju.
    val difficultiesByTask = remember(registry) {
        registry.all
            .flatMap { module -> module.tasks.map { task -> task.id to module.difficulties } }
            .toMap()
    }

    val recommendation = remember(progress, allTasks, difficultiesByTask) {
        progress.recommend(
            tasks = allTasks,
            lastTaskId = progress.skillHistory.lastOrNull { !it.isCheckup }?.taskId,
            difficultiesFor = { taskId -> difficultiesByTask[taskId] ?: Difficulty.entries }
        )
    }

    // Ko vežba. Stoji izvan NavHost-a jer se menja retko a čita svuda.
    val activeProfile by hiltViewModel<ProfilesViewModel>().active.collectAsState()

    val summaryViewModel: SummaryViewModel = hiltViewModel()
    val eyesFree by summaryViewModel.eyesFree.collectAsState()

    NavHost(navController = navController, startDestination = ROUTE_MENU) {

        composable(ROUTE_MENU) {
            val settings by hiltViewModel<SettingsViewModel>().settings.collectAsState()

            MainMenuScreen(
                modules = registry.all,
                progress = progress,
                eyesFree = settings.eyesFree,
                onStart = { module, args ->
                    // Izbor iz menija je **izbor korisnika**, ne porudžbina puta:
                    // `null` znači „nisam dirao", pa modul odlučuje kao i pre.
                    navController.navigate(
                        moduleRoute(
                            moduleKey = module.id.key,
                            difficulty = args.difficulty,
                            taskId = args.taskId,
                            support = args.support
                        )
                    )
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                // Rang vodi na **istrajnost**, ne na Napredak: dotle je vodio
                // tamo i time tvrdio da su poeni i nivo ista stvar.
                onOpenProgress = { navController.navigate(ROUTE_PERSISTENCE) },
                onOpenGuide = { navController.navigate(ROUTE_GUIDE) },
                recommendation = recommendation,
                onStartRecommended = { suggestion ->
                    val module = registry.all.first { module ->
                        module.tasks.any { it.id == suggestion.taskId }
                    }
                    navController.navigate(
                        moduleRoute(
                            moduleKey = module.id.key,
                            // Modul koji težine ne nudi ih ionako ne gleda; ruta
                            // mora nešto da nosi, pa nosi najlakšu.
                            difficulty = suggestion.difficulty ?: Difficulty.EASY,
                            taskId = suggestion.taskId,
                            support = suggestion.support
                        )
                    )
                },
                profileName = activeProfile?.name,
                onOpenProfiles = { navController.navigate(ROUTE_PROFILES) },
                checkup = nextCheckup,
                onStartCheckup = { checkup ->
                    navController.navigate(
                        moduleRoute(
                            moduleKey = checkup.moduleId.key,
                            difficulty = checkup.difficulty,
                            taskId = checkup.taskId,
                            support = checkup.support,
                            isCheckup = true,
                            rounds = checkup.rounds
                        )
                    )
                }
            )
        }

        composable(ROUTE_PROGRESS) {
            // Zadaci stižu iz registra: modul prijavljuje svoje, a ekran iz
            // njih zna orijentire. Nigde se ne prepisuju.
            ProgressScreen(
                progress = progress,
                tasks = registry.all.flatMap { it.tasks }.associateBy { it.id },
                // Zadatak se u meniju ne vidi sam za sebe — kartice nose imena
                // modula. Bez ovoga bi Napredak upućivao na nešto što se ne
                // može naći.
                moduleTitleFor = { taskId ->
                    registry.all.find { module -> module.tasks.any { it.id == taskId } }?.titleRes
                },
                // Iz Napretka se ulazi **pravo u vežbu**, sa već izabranim
                // zadatkom i osloncem — isto kao sa kartice Predloga.
                difficultiesFor = { taskId -> difficultiesByTask[taskId] ?: Difficulty.entries },
                onPractice = { taskId, support, difficulty ->
                    registry.all.find { module -> module.tasks.any { it.id == taskId } }
                        ?.let { module ->
                            navController.navigate(
                                moduleRoute(
                                    moduleKey = module.id.key,
                                    difficulty = difficulty ?: Difficulty.EASY,
                                    taskId = taskId,
                                    support = support
                                )
                            )
                        }
                },
                checkupCount = registry.offerableCheckups.size,
                onOpenSnapshot = { navController.navigate(ROUTE_SNAPSHOT) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_GUIDE) {
            // Uputstvo dobija **registar**, ne prepisan spisak: veštine, zadaci i
            // prečke o kojima piše su iste one po kojima aplikacija radi.
            GuideScreen(
                modules = registry.all,
                checkups = registry.offerableCheckups,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SNAPSHOT) {
            SnapshotScreen(
                progress = progress,
                tasks = registry.all.flatMap { it.tasks }.associateBy { it.id },
                checkups = registry.offerableCheckups,
                onStartCheckup = { checkup ->
                    navController.navigate(
                        moduleRoute(
                            moduleKey = checkup.moduleId.key,
                            difficulty = checkup.difficulty,
                            taskId = checkup.taskId,
                            support = checkup.support,
                            isCheckup = true,
                            rounds = checkup.rounds,
                            fromSnapshot = true
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_PERSISTENCE) {
            PersistenceScreen(progress = progress, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_PROFILES) {
            ProfilesScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = ROUTE_MODULE,
            arguments = listOf(
                navArgument(ARG_MODULE) { type = NavType.StringType },
                navArgument(ARG_DIFFICULTY) { type = NavType.StringType },
                navArgument(ARG_TASK) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_SUPPORT) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_CHECKUP) { type = NavType.BoolType; defaultValue = false },
                navArgument(ARG_ROUNDS) { type = NavType.IntType; defaultValue = 0 },
                navArgument(ARG_FROM_SNAPSHOT) { type = NavType.BoolType; defaultValue = false }
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

            val taskId = backStackEntry.arguments?.getString(ARG_TASK)?.ifBlank { null }
            val support = backStackEntry.arguments
                ?.getString(ARG_SUPPORT)
                ?.let { key -> Support.entries.find { it.key == key } }
            val isCheckup = backStackEntry.arguments?.getBoolean(ARG_CHECKUP) == true
            val rounds = backStackEntry.arguments?.getInt(ARG_ROUNDS)?.takeIf { it > 0 }
            val fromSnapshot = backStackEntry.arguments?.getBoolean(ARG_FROM_SNAPSHOT) == true

            var result by remember { mutableStateOf<SessionResult?>(null) }

            module.Screen(
                args = ModuleArgs(
                    difficulty = difficulty,
                    taskId = taskId,
                    support = support,
                    rounds = rounds
                ),
                onFinish = { sessionResult ->
                    // Da je ovo bila provera zna **školjka**, ne modul: modul ne
                    // zna ni za poene ni za napredak, pa ne treba da zna ni za
                    // merenje. Ona je poručila, ona i obeležava.
                    val finished = sessionResult.copy(isCheckup = isCheckup)
                    result = finished
                    progressViewModel.record(finished)
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
                    // Ko meri presek se vraća u presek: merenja se skupljaju, pa
                    // bi povratak u meni posle svakog značio da se do sledećeg
                    // opet dolazi kroz tri ekrana.
                    val home = if (fromSnapshot) ROUTE_SNAPSHOT else ROUTE_MENU
                    navController.popBackStack(home, inclusive = false)
                    Unit
                }

                // Isti ishod, dva oblika: dijalog za onoga ko gleda, zone za
                // onoga ko ne gleda. Ishod i napredak su već upisani — ovo je
                // samo način da se do njih dođe.
                if (eyesFree) {
                    SessionSummaryEyesFree(
                        result = finished,
                        onAnnounce = summaryViewModel::announceZones,
                        onSay = { summaryViewModel.sayResult(finished, reward) },
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
