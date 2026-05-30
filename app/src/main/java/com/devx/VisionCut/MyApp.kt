package com.devx.VisionCut

import android.app.Application
import androidx.media3.effect.BuildConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Debug logging only — automatically excluded from release builds
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG

        // TODO: move API key out of source code before release.
        //       Store in secrets.properties + BuildConfig, or use a Gradle
        //       plugin like gradle-secrets-plugin to avoid committing it to git.
        Purchases.configure(
            PurchasesConfiguration.Builder(
                context = this,
                apiKey  = "test_UTGrzuxnXkIfCAgNbIOHUIdjinl"
            ).build()
        )
    }
}