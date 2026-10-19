package com.program.blindfoldtrainer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.program.blindfoldtrainer.ModuleRegistry
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Checkup
import com.program.blindfoldtrainer.core.model.Difficulty
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.model.Support
import com.program.blindfoldtrainer.core.moduleapi.ModuleArgs
import com.program.blindfoldtrainer.core.progress.recommend
import com.program.blindfoldtrainer.ui.GuideScreen
import com.program.blindfoldtrainer.ui.ModuleSetupScreen
import com.program.blindfoldtrainer.ui.PersistenceScreen
import com.program.blindfoldtrainer.ui.ProfilesScreen
import com.program.blindfoldtrainer.ui.ProfilesViewModel
import com.program.blindfoldtrainer.ui.ProgressScreen
import com.program.blindfoldtrainer.ui.ProgressViewModel
import com.program.blindfoldtrainer.ui.SessionSummaryDialog
import com.program.blindfoldtrainer.ui.shiftOf
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.ui.SessionSummaryEyesFree
import com.program.blindfoldtrainer.ui.SettingsScreen
import com.program.blindfoldtrainer.ui.SettingsViewModel
import com.program.blindfoldtrainer.ui.SummaryViewModel
import com.program.blindfoldtrainer.ui.TrainScreen
import com.program.blindfoldtrainer.ui.YouScreen

/**
 * **Tri kartice u dnu**: Vežbaj, Napredak, Ja.
 *
 * Dotle je sve polazilo sa jednog ekrana — predlog, provera, rang, devet
 * modula i četiri ikone u traci. To je bio spisak, ne mesto: nije se videlo ni
 * gde si ni dokle si stigao. Tri kartice odgovaraju na tri pitanja koja se
 * postavljaju odvojeno: *šta sad*, *gde stojim* i *ko sam i kako je podešeno*.
 */
private const val ROUTE_TRAIN = "train"
private const val ROUTE_PROGRESS = "progress"
private const val ROUTE_YOU = "you"

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_GUIDE = "guide"
private const val ROUTE_PERSISTENCE = "persistence"
private const val ARG_MODULE = "module"
private const val ARG_DIFFICULTY = "difficulty"
private const val ARG_TASK = "task"
private const val ARG_SUPPORT = "support"
private const val ARG_ROUNDS = "rounds"
private const val ARG_CHECKUP = "checkup"

/**
 * Stranica modula — bira se zadatak, oslonac i težina, pa se kreće.
 *
 * Ne sme da počinje sa `module/`: ruta modula ima `{difficulty}` na istom
 * mestu, pa bi `module/geometry/setup` pročitala „setup" kao težinu i otvorila
 * vežbu umesto stranice.
 */
private const val ROUTE_SETUP = "setup/{$ARG_MODULE}"

/**
 * Da li se posle sesije vraća na **Napredak** umesto na Vežbaj.
 *
 * Ko je sa Napretka krenuo da meri ili vežba jednu veštinu hoće posle sesije
 * da vidi šta se pomerilo — a ne da do toga opet dolazi kroz karticu.
 */
private const val ARG_FROM_PROGRESS = "fromProgress"

/**
 * Jedna ruta za sve module, sa **porudžbinom** kao neobaveznim delom.
 *
 * Bez porudžbine je slobodno vežbanje sa stranice modula i modul bira sam. Sa
 * njom put ili provera traže određen zadatak na određenoj prečki.
 */
private const val ROUTE_MODULE =
    "module/{$ARG_MODULE}/{$ARG_DIFFICULTY}?$ARG_TASK={$ARG_TASK}" +
        "&$ARG_SUPPORT={$ARG_SUPPORT}&$ARG_CHECKUP={$ARG_CHECKUP}" +
        "&$ARG_FROM_PROGRESS={$ARG_FROM_PROGRESS}"

private fun moduleRoute(
    moduleKey: String,
    difficulty: Difficulty,
    taskId: String? = null,
    support: Support? = null,
    isCheckup: Boolean = false,
    rounds: Int? = null,
    fromProgress: Boolean = false
): String = buildString {
    append("module/$moduleKey/${difficulty.name}")
    append("?$ARG_TASK=${taskId.orEmpty()}")
    append("&$ARG_SUPPORT=${support?.key.orEmpty()}")
    append("&$ARG_CHECKUP=$isCheckup")
    // Nula znači „nije poručeno" — `NavType.IntType` ne ume `null`.
    append("&$ARG_ROUNDS=${rounds ?: 0}")
    append("&$ARG_FROM_PROGRESS=$fromProgress")
}

/** Kartica u dnu: ruta, ime i ikona. */
private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(ROUTE_TRAIN, R.string.tab_train, Icons.Default.PlayArrow),
    Tab(ROUTE_PROGRESS, R.string.progress_title, Icons.AutoMirrored.Filled.ShowChart),
    Tab(ROUTE_YOU, R.string.tab_you, Icons.Default.Person)
)

/**
 * Prelazak na karticu: **jedna kopija svake**, sa sačuvanim stanjem.
 *
 * Bez `popUpTo` bi svaki dodir kartice gurao novu kopiju na stek, pa bi se
 * „nazad" vrtelo kroz sve dodirnute kartice; bez `saveState` bi se pri
 * svakom prelasku gubilo dokle je spisak doskrolovan.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Ceo graf navigacije: tri kartice, nekoliko ekrana iznad njih i **jedna**
 * ruta za module.
 *
 * Modul se ne pominje poimence nigde — koji će se ekran prikazati određuje
 * registar na osnovu ključa iz rute. Dodavanje modula zato ne dira ovaj fajl.
 */
@Composable
fun AppNavigation(registry: ModuleRegistry) {
    val navController = rememberNavController()

    // Traži se izvan NavHost-a, pa je vezan za aktivnost a ne za rutu —
    // napredak preživljava prelaz iz modula nazad na karticu.
    val progressViewModel: ProgressViewModel = hiltViewModel()
    val progress by progressViewModel.snapshot.collectAsState()

    // I ovaj stoji izvan NavHost-a, ali iz drugog razloga: podešavanje mora biti
    // pročitano **pre** nego što sažetak zatreba. Da se traži tek uz sažetak,
    // prvi kadar bi dobio zatečenu vrednost i bez ekrana bi bljesnuo dijalog.
    val settings by hiltViewModel<SettingsViewModel>().settings.collectAsState()

    // Koju proveru ponuditi: prvo neproverenu veštinu, pa onu najstariju.
    // Provera je predlog, ne obaveza — spisak ostaje netaknut ispod nje.
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = TABS.any { it.route == currentRoute }

    val startCheckup = { checkup: Checkup, fromProgress: Boolean ->
        navController.navigate(
            moduleRoute(
                moduleKey = checkup.moduleId.key,
                difficulty = checkup.difficulty,
                taskId = checkup.taskId,
                support = checkup.support,
                isCheckup = true,
                rounds = checkup.rounds,
                fromProgress = fromProgress
            )
        )
    }

    // Iz Napretka se ulazi **pravo u vežbu**, sa već izabranim zadatkom i
    // osloncem — isto kao sa kartice „Sledeće" — i posle nje se vraća tamo.
    val practiseTask = { taskId: String, support: Support?, difficulty: Difficulty? ->
        registry.all.find { module -> module.tasks.any { it.id == taskId } }
            ?.let { module ->
                navController.navigate(
                    moduleRoute(
                        moduleKey = module.id.key,
                        difficulty = difficulty ?: Difficulty.EASY,
                        taskId = taskId,
                        support = support,
                        fromProgress = true
                    )
                )
            }
        Unit
    }

    Scaffold(
        // Traka u dnu stoji samo na tri kartice. Modul je ekran za sebe:
        // zone bez ekrana računaju na celu visinu, a traka bi im uzela deo.
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.route == currentRoute,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        },
        // Sistemske trake ostaju ekranima unutra: svaki ima svoju gornju
        // traku koja zna za sat, a modul se sklanja sam (vidi niže).
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
    NavHost(
        navController = navController,
        startDestination = ROUTE_TRAIN,
        // Visina trake u dnu se **potroši** kao inset: ekran kartice inače
        // ne zna da je traka tu i ostavlja još jedan razmak za navigacionu
        // traku sistema, koju traka već pokriva.
        modifier = Modifier.padding(padding).consumeWindowInsets(padding)
    ) {

        composable(ROUTE_TRAIN) {
            TrainScreen(
                modules = registry.all,
                progress = progress,
                eyesFree = settings.eyesFree,
                profileName = activeProfile?.name,
                recommendation = recommendation,
                checkup = nextCheckup,
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
                onStartCheckup = { checkup -> startCheckup(checkup, false) },
                onOpenModule = { module -> navController.navigate("setup/${module.id.key}") }
            )
        }

        composable(ROUTE_PROGRESS) {
            // Zadaci stižu iz registra: modul prijavljuje svoje, a ekran iz
            // njih zna orijentire. Nigde se ne prepisuju.
            ProgressScreen(
                progress = progress,
                tasks = allTasks.associateBy { it.id },
                // Zadatak se na spisku ne vidi sam za sebe — redovi nose imena
                // modula. Bez ovoga bi Napredak upućivao na nešto što se ne
                // može naći.
                moduleTitleFor = { taskId ->
                    registry.all.find { module -> module.tasks.any { it.id == taskId } }?.titleRes
                },
                difficultiesFor = { taskId -> difficultiesByTask[taskId] ?: Difficulty.entries },
                onPractice = practiseTask,
                checkups = registry.offerableCheckups,
                onStartCheckup = { checkup -> startCheckup(checkup, true) },
                onBack = null
            )
        }

        composable(ROUTE_YOU) {
            YouScreen(
                progress = progress,
                profileName = activeProfile?.name,
                onOpenProfiles = { navController.navigate(ROUTE_PROFILES) },
                // Rang vodi na **istrajnost**, ne na Napredak: poeni mere
                // uloženo, a nivo po veštini je druga stvar i stoji drugde.
                onOpenPersistence = { navController.navigate(ROUTE_PERSISTENCE) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenGuide = { navController.navigate(ROUTE_GUIDE) }
            )
        }

        composable(
            route = ROUTE_SETUP,
            arguments = listOf(navArgument(ARG_MODULE) { type = NavType.StringType })
        ) { entry ->
            val module = entry.arguments?.getString(ARG_MODULE)?.let { registry.byKey(it) }
            if (module == null) {
                navController.popBackStack(ROUTE_TRAIN, inclusive = false)
                return@composable
            }
            ModuleSetupScreen(
                module = module,
                progress = progress,
                eyesFree = settings.eyesFree,
                onStart = { args ->
                    // Izbor sa stranice je **izbor korisnika**, ne porudžbina
                    // puta: `null` znači „nisam dirao", pa modul odlučuje sam.
                    navController.navigate(
                        moduleRoute(
                            moduleKey = module.id.key,
                            difficulty = args.difficulty,
                            taskId = args.taskId,
                            support = args.support
                        )
                    )
                },
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
                navArgument(ARG_FROM_PROGRESS) { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            val moduleKey = entry.arguments?.getString(ARG_MODULE)
            val module = moduleKey?.let { registry.byKey(it) }

            val difficulty = entry.arguments
                ?.getString(ARG_DIFFICULTY)
                ?.let { name -> Difficulty.entries.find { it.name == name } }
                ?: Difficulty.EASY

            if (module == null) {
                // Ruta pokazuje na modul koji nije ugrađen u ovu verziju.
                // Umesto praznog ekrana, vraćamo korisnika na Vežbaj.
                navController.popBackStack(ROUTE_TRAIN, inclusive = false)
                return@composable
            }

            val taskId = entry.arguments?.getString(ARG_TASK)?.ifBlank { null }
            val support = entry.arguments
                ?.getString(ARG_SUPPORT)
                ?.let { key -> Support.entries.find { it.key == key } }
            val isCheckup = entry.arguments?.getBoolean(ARG_CHECKUP) == true
            val rounds = entry.arguments?.getInt(ARG_ROUNDS)?.takeIf { it > 0 }
            val fromProgress = entry.arguments?.getBoolean(ARG_FROM_PROGRESS) == true

            var result by remember { mutableStateOf<SessionResult?>(null) }

            // Napredak **pre upisa**, da sažetak ume da kaže od čega do čega se
            // nivo pomerio. Posle upisa `progress` već nosi novo stanje, pa se
            // staro mora zapamtiti u trenutku kraja.
            var before by remember { mutableStateOf<ProgressSnapshot?>(null) }

            /**
             * Da li je ishod ove sesije **već upisan**.
             *
             * `rememberSaveable`, jer jedini razlog zbog kog postoji jeste da
             * preživi ponovo pravljenje `Activity`-ja.
             *
             * Ekran modula javlja kraj iz `LaunchedEffect(isFinished)`. `ViewModel`
             * preživi rotaciju sa `isFinished = true`, ali sastav ne — pa se posle
             * rotacije efekat pokreće **ponovo** i ista sesija bi se upisala dvaput.
             * Poeni, sesije i profil bi se time tiho udvostručili.
             *
             * Danas je aplikacija zaključana na portret pa se ne može ni okrenuti;
             * ova zaštita stoji zato što je zaključavanje **odluka o izgledu**, a
             * ovo je greška u računu. Kad se pejzaž jednom dopusti, ne sme da se
             * osloni na to da se neko setio i ovoga.
             */
            var recorded by rememberSaveable(moduleKey, taskId) { mutableStateOf(false) }

            // **Sistemske trake se poštuju ovde, jednom za sve module.**
            //
            // `enableEdgeToEdge()` pušta sadržaj ispod sata i ispod navigacione
            // trake, a to je dobro — pozadina ide do ivice. Ali ekran koji nema
            // `Scaffold` mora sam da se skloni, a ekrani modula ga nemaju: brojači
            // su ulazili u sat i ikonice, a dugmad u navigacionu traku.
            //
            // Stoji na jednom mestu jer je i propust bio jedan: da svaki modul
            // dodaje svoje, sledeći napisani bi ga opet zaboravio.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
            module.Screen(
                args = ModuleArgs(
                    difficulty = difficulty,
                    taskId = taskId,
                    support = support,
                    rounds = rounds
                ),
                onFinish = { sessionResult ->
                    if (recorded) return@Screen
                    recorded = true

                    // Da je ovo bila provera zna **školjka**, ne modul: modul ne
                    // zna ni za poene ni za napredak, pa ne treba da zna ni za
                    // merenje. Ona je poručila, ona i obeležava.
                    val finished = sessionResult.copy(isCheckup = isCheckup)
                    before = progress
                    result = finished
                    progressViewModel.record(finished)
                }
            )

            result?.let { finished ->
                val reward by progressViewModel.lastReward.collectAsState()

                // Računa se iznova kad `progress` stigne sa upisom: prvi kadar
                // pokazuje „bez promene", sledeći ono što se pomerilo.
                val shift = remember(finished, before, progress) {
                    before?.let { shiftOf(finished, it, progress, allTasks) }
                }

                // **„Još jednom" ponavlja istu sesiju** — isti zadatak, isti
                // oslonac, ista provera. Dotle je nosio samo težinu, pa je drugi
                // zadatak modula posle „još jednom" ćutke postajao prvi.
                val repeatSession = {
                    result = null
                    progressViewModel.onSummaryClosed()
                    navController.navigate(
                        moduleRoute(
                            moduleKey = module.id.key,
                            difficulty = difficulty,
                            taskId = taskId,
                            support = support,
                            isCheckup = isCheckup,
                            rounds = rounds,
                            fromProgress = fromProgress
                        )
                    ) {
                        popUpTo(ROUTE_TRAIN) { inclusive = false }
                    }
                }
                val backToMenu = {
                    result = null
                    progressViewModel.onSummaryClosed()
                    // Ko je krenuo sa Napretka vraća se na Napredak: tamo se
                    // vidi šta se sesijom pomerilo, a merenja se i skupljaju.
                    val home = if (fromProgress) ROUTE_PROGRESS else ROUTE_TRAIN
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
                        shift = shift,
                        next = recommendation,
                        onRepeat = repeatSession,
                        onBackToMenu = backToMenu
                    )
                }
            }
            }
        }
    }
    }
}
