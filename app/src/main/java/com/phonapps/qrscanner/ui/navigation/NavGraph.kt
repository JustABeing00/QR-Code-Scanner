package com.phonapps.qrscanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonapps.qrscanner.data.repo.ThemeMode
import com.phonapps.qrscanner.ui.history.HistoryScreen
import com.phonapps.qrscanner.ui.result.ResultScreen
import com.phonapps.qrscanner.ui.scanner.ScannerScreen
import com.phonapps.qrscanner.ui.settings.PrivacyScreen
import com.phonapps.qrscanner.ui.settings.SettingsScreen

object Routes {
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val RESULT = "result/{scanId}"

    fun result(scanId: Long) = "result/$scanId"
}

@Composable
fun AppNavGraph(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    externalResultId: Long?,
    onExternalResultConsumed: () -> Unit
) {
    val navController = rememberNavController()

    // Results arriving from shares / clipboard while the app runs.
    LaunchedEffect(externalResultId) {
        val id = externalResultId
        if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
            android.util.Log.d("QrScanner", "externalResult effect fired: $id")
        }
        if (id != null) {
            if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "QrScanner",
                    "navigating to result/$id from ${navController.currentDestination?.route}"
                )
            }
            // No launchSingleTop: external results must always open as a new
            // screen, even when the current screen is another result. Reusing
            // the same route entry would keep showing the previous scan.
            navController.navigate(Routes.result(id))
            if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "QrScanner",
                    "navigated, dest=${navController.currentDestination?.route}"
                )
            }
            onExternalResultConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.SCANNER) {
        composable(Routes.SCANNER) {
            ScannerScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onScanResult = { id, continuous ->
                    navController.navigate(Routes.result(id)) {
                        // Continuous mode stacks results so Back walks the session.
                        if (!continuous) popUpTo(Routes.SCANNER) { inclusive = false }
                    }
                }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenResult = { id -> navController.navigate(Routes.result(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { entry ->
            val scanId = entry.arguments?.getLong("scanId") ?: 0L
            ResultScreen(
                scanId = scanId,
                onBack = { navController.popBackStack() },
                onScanAgain = {
                    navController.navigate(Routes.SCANNER) {
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
