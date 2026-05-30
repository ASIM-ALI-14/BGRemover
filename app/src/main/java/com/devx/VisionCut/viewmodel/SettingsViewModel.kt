package com.devx.VisionCut.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    fun shareApp() {
        val packageName = getApplication<Application>().packageName
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this amazing app: https://play.google.com/store/apps/details?id=$packageName")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(shareIntent)
    }

    fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@devx.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Support Request - VisionCut")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun rateApp() {
        val app         = getApplication<Application>()
        val packageName = app.packageName
        try {
            // Open native Play Store app
            app.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: ActivityNotFoundException) {
            // Play Store not installed — fall back to browser
            Log.w(TAG, "Play Store not available, opening web fallback")
            app.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun openPrivacyPolicy() {
        getApplication<Application>().startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://devx.com/privacy-policy")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openTermsOfService() {
        getApplication<Application>().startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://devx.com/terms-of-service")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}