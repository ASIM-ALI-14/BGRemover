package com.devx.testapp.ui.components



import android.app.Activity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class InterstitialAdManager(private val activity: Activity) {

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    // ✅ FIX: store BOTH callbacks so onAdDismissed is never lost
    private var pendingOnFinished: (() -> Unit)? = null
    private var pendingOnDismissed: (() -> Unit)? = null

    private val adUnitId = "ca-app-pub-3940256099942544/5354046379"

    fun cancel() {
        pendingOnFinished = null
        pendingOnDismissed = null
    }

    fun loadAd() {
        if (isLoading || rewardedInterstitialAd != null) return
        isLoading = true

        RewardedInterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    isLoading = false
                    rewardedInterstitialAd = ad

                    // ✅ Pass BOTH stored callbacks — not just onFinished
                    val onFinished = pendingOnFinished
                    val onDismissed = pendingOnDismissed
                    if (onFinished != null) {
                        pendingOnFinished = null
                        pendingOnDismissed = null
                        showAd(onFinished, onDismissed)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedInterstitialAd = null

                    // Ad unavailable — unblock user (treat as dismissed)
                    pendingOnDismissed?.invoke() ?: pendingOnFinished?.invoke()
                    pendingOnFinished = null
                    pendingOnDismissed = null
                    loadAd()
                }
            }
        )
    }

    fun showAd(
        onAdFinished: () -> Unit,
        onAdDismissed: (() -> Unit)? = null
    ) {
        val ad = rewardedInterstitialAd

        if (ad == null) {
            // ✅ Store both so they survive the async load
            pendingOnFinished = onAdFinished
            pendingOnDismissed = onAdDismissed
            if (!isLoading) loadAd()
            return
        }

        var rewarded = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedInterstitialAd = null
                loadAd()
                if (rewarded) {
                    onAdFinished()       // watched fully → start processing
                } else {
                    onAdDismissed?.invoke() // back-pressed → reset button only
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedInterstitialAd = null
                loadAd()
                onAdDismissed?.invoke() ?: onAdFinished() // unblock user
            }
        }

        ad.show(activity) { _ ->
            rewarded = true
        }
    }
}