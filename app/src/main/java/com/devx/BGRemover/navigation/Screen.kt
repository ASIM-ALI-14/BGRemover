package com.devx.BGRemover.navigation

sealed class Screen(val route: String) {
    object Home : `Screen.kt`("home")
    object RemoveBG : `Screen.kt`("remove_bg")
    object Export : `Screen.kt`("export")
    object Premium : `Screen.kt`("premium")   // ← add this

}
