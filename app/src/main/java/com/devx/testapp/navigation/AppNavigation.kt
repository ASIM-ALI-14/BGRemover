package com.devx.testapp.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devx.testapp.ui.screens.ExportScreen
import com.devx.testapp.ui.screens.HomeScreen
import com.devx.testapp.ui.screens.PremiumScreen
import com.devx.testapp.ui.screens.RemoveBGScreen
import com.devx.testapp.viewmodel.MainViewModel
import com.devx.testapp.viewmodel.PremiumViewModel

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val premiumViewModel: PremiumViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                vm = viewModel,
                premiumViewModel = premiumViewModel,
                onVideoSelected = {
                    navController.navigate(Screen.RemoveBG.route)
                },
                onGoPremium = {
                    // FIX: No popUpTo here — just push Premium on top of Home.
                    // popUpTo was corrupting the back stack so popBackStack()
                    // had nothing left and closed the app.
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        composable(Screen.RemoveBG.route) {
            RemoveBGScreen(
                viewModel = viewModel,
                premiumViewModel = premiumViewModel,
                onBack = {
                    viewModel.resetSelection()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onProcessed = {
                    navController.navigate(Screen.Export.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onGoPremium = {
                    // FIX: No popUpTo — just push Premium on top cleanly.
                    // Back stack: Home → RemoveBG → Premium.
                    // After purchase popBackStack() returns to RemoveBG correctly.
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        composable(Screen.Export.route) {
            ExportScreen(
                viewModel = viewModel,
                premiumViewModel = premiumViewModel,
                onBack = {
                    viewModel.resetSelection()
                    viewModel.resetExport()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Premium.route) {
            PremiumScreen(
                premiumViewModel = premiumViewModel,
                onBack = {
                    // popBackStack() is safe here because Premium always has
                    // at least Home behind it (no popUpTo removed it)
                    navController.popBackStack()
                }
            )
        }
    }
}