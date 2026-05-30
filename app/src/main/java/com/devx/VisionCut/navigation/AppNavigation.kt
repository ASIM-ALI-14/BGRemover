package com.devx.VisionCut.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devx.VisionCut.ui.screens.ExportScreen
import com.devx.VisionCut.ui.screens.HomeScreen
import com.devx.VisionCut.ui.screens.PremiumScreen
import com.devx.VisionCut.ui.screens.RemoveBGScreen
import com.devx.VisionCut.ui.screens.VideoDetailScreen
import com.devx.VisionCut.viewmodel.MainViewModel
import com.devx.VisionCut.viewmodel.PremiumViewModel
import com.devx.VisionCut.viewmodel.VideoLibraryViewModel

@Composable
fun AppNavigation(
    viewModel     : MainViewModel,
    navController : NavHostController = rememberNavController()
) {
    // All ViewModels obtained here — shared across all destinations
    val premiumViewModel      : PremiumViewModel       = viewModel()
    val videoLibraryViewModel : VideoLibraryViewModel  = viewModel()

    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route
    ) {

        composable(Screen.Home.route) {
            HomeScreen(
                vm                    = viewModel,
                premiumViewModel      = premiumViewModel,
                videoLibraryViewModel = videoLibraryViewModel,
                onVideoSelected       = { navController.navigate(Screen.RemoveBG.route) },
                onGoPremium           = { navController.navigate(Screen.Premium.route) },
                onOpenVideoDetail     = { navController.navigate(Screen.VideoDetail.route) }
            )
        }

        composable(Screen.RemoveBG.route) {
            RemoveBGScreen(
                viewModel        = viewModel,
                premiumViewModel = premiumViewModel,
                onBack           = {
                    viewModel.resetSelection()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onProcessed      = {
                    navController.navigate(Screen.Export.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                // Push Premium on top — back stack: Home → RemoveBG → Premium
                onGoPremium      = { navController.navigate(Screen.Premium.route) }
            )
        }

        composable(Screen.Export.route) {
            ExportScreen(
                viewModel        = viewModel,
                premiumViewModel = premiumViewModel,
                onBack           = {
                    viewModel.resetSelection()
                    viewModel.resetExport()
                    // Refresh library so the new video appears immediately
                    videoLibraryViewModel.loadVideos()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Premium.route) {
            PremiumScreen(
                premiumViewModel = premiumViewModel,
                // Safe: Premium always has at least Home below it
                onBack           = { navController.popBackStack() }
            )
        }

        composable(Screen.VideoDetail.route) {
            VideoDetailScreen(
                videoLibraryViewModel = videoLibraryViewModel,
                onBack                = {
                    videoLibraryViewModel.clearSelection()
                    navController.popBackStack()
                }
            )
        }
    }
}