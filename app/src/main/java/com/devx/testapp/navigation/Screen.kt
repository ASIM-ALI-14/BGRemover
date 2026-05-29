package com.devx.testapp.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object RemoveBG : Screen("remove_bg")
    object Export : Screen("export")
    object Premium : Screen("premium")   // ← add this

}
