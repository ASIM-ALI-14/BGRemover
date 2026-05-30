package com.devx.VisionCut.navigation

/** Defines all navigation destinations and their route strings. */
sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object RemoveBG    : Screen("remove_bg")
    object Export      : Screen("export")
    object Premium     : Screen("premium")
    object VideoDetail : Screen("video_detail")  // fullscreen player for library videos
}