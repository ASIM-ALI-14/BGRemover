package com.devx.BGRemover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devx.BGRemover.navigation.AppNavigation
import com.devx.BGRemover.ui.theme.TestAppTheme
import com.devx.BGRemover.viewmodel.MainViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        MobileAds.initialize(this)
        enableEdgeToEdge()
        setContent {
            TestAppTheme {
                val viewModel: MainViewModel = viewModel()
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}
