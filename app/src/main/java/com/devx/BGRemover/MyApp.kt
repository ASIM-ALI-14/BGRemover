package com.devx.BGRemover



import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Purchases.logLevel = LogLevel.DEBUG  // remove before publishing
        Purchases.configure(
            PurchasesConfiguration.Builder(
                context = this,
                apiKey  = "test_UTGrzuxnXkIfCAgNbIOHUIdjinl"
            ).build()
        )
    }
}