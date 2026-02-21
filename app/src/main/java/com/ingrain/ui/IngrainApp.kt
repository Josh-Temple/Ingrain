package com.ingrain.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ingrain.data.IngrainRepository
import com.ingrain.scheduler.SchedulerSettingsStore
import com.ingrain.ui.screens.BackupScreen
import com.ingrain.ui.screens.DeckDetailScreen
import com.ingrain.ui.screens.DeckListScreen
import com.ingrain.ui.screens.ImportScreen
import com.ingrain.ui.screens.SettingsScreen
import com.ingrain.ui.screens.StudyScreen

object Routes {
    const val DECKS = "decks"
    const val DETAIL = "detail/{deckId}"
    const val IMPORT = "import/{deckId}"
    const val IMPORT_GLOBAL = "import-global"
    const val STUDY = "study/{deckId}"
    const val BACKUP = "backup/{deckId}"
    const val SETTINGS = "settings/{deckId}"
}

@Composable
fun IngrainApp(repo: IngrainRepository, settingsStore: SchedulerSettingsStore) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DECKS) {
        composable(Routes.DECKS) {
            DeckListScreen(
                repo = repo,
                onStudyDeck = { nav.navigate("study/$it") },
                onEditDeck = { nav.navigate("detail/$it") },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: 0L
            DeckDetailScreen(
                deckId = deckId,
                repo = repo,
                onStudy = { nav.navigate("study/$deckId") },
                onImport = { nav.navigate("import/$deckId") },
                onSettings = { nav.navigate("settings/$deckId") },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.IMPORT, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            ImportScreen(deckId = entry.arguments?.getLong("deckId"), repo = repo)
        }
        composable(Routes.IMPORT_GLOBAL) {
            ImportScreen(deckId = null, repo = repo)
        }
        composable(Routes.STUDY, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            StudyScreen(
                deckId = entry.arguments?.getLong("deckId") ?: 0L,
                repo = repo,
                settingsStore = settingsStore,
            )
        }
        composable(Routes.SETTINGS, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            SettingsScreen(
                deckId = entry.arguments?.getLong("deckId") ?: 0L,
                repo = repo,
                store = settingsStore,
            )
        }
        composable(Routes.BACKUP, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            BackupScreen(deckId = entry.arguments?.getLong("deckId") ?: 0L, repo = repo)
        }
    }
}
