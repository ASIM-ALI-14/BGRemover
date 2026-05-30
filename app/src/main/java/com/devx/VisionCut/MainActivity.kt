package com.devx.VisionCut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devx.VisionCut.navigation.AppNavigation
import com.devx.VisionCut.ui.theme.VisionCutTheme
import com.devx.VisionCut.viewmodel.MainViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge handles WindowCompat.setDecorFitsSystemWindows internally
        enableEdgeToEdge()
        MobileAds.initialize(this)
        setContent {
            VisionCutTheme {
                val viewModel: MainViewModel = viewModel()
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}