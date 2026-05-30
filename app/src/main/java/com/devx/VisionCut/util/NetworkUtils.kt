package com.devx.VisionCut.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Returns true only when the device has a validated internet connection. */
fun isNetworkAvailable(context: Context): Boolean {
    val cm      = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps    = cm.getNetworkCapabilities(network) ?: return false
    // VALIDATED ensures the network can actually reach the internet (not just connected)
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}