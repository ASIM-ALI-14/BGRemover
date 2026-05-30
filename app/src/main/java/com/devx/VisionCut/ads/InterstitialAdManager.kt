package com.devx.VisionCut.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class InterstitialAdManager(private val activity: Activity) {

    companion object {
        // TODO: replace with real ad unit ID from AdMob console before publishing
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    }

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    // Both callbacks stored so onAdDismissed is not lost during async load
    private var pendingOnFinished : (() -> Unit)? = null
    private var pendingOnDismissed: (() -> Unit)? = null

    /** Clear any queued callbacks (e.g. when the screen is disposed). */
    fun cancel() {
        pendingOnFinished  = null
        pendingOnDismissed = null
    }

    fun loadAd() {
        if (isLoading || rewardedInterstitialAd != null) return
        isLoading = true

        RewardedInterstitialAd.load(
            activity,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    isLoading              = false
                    rewardedInterstitialAd = ad

                    // If a showAd() call was waiting, replay it now
                    val onFinished  = pendingOnFinished
                    val onDismissed = pendingOnDismissed
                    if (onFinished != null) {
                        pendingOnFinished  = null
                        pendingOnDismissed = null
                        showAd(onFinished, onDismissed)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading              = false
                    rewardedInterstitialAd = null

                    // Unblock the user — treat failure as a dismiss
                    pendingOnDismissed?.invoke() ?: pendingOnFinished?.invoke()
                    pendingOnFinished  = null
                    pendingOnDismissed = null
                    loadAd() // retry for next use
                }
            }
        )
    }

    fun showAd(
        onAdFinished : () -> Unit,
        onAdDismissed: (() -> Unit)? = null
    ) {
        val ad = rewardedInterstitialAd

        if (ad == null) {
            // Ad not ready — queue callbacks and start loading
            pendingOnFinished  = onAdFinished
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
                    onAdFinished()          // user watched fully → start processing
                } else {
                    onAdDismissed?.invoke() // user dismissed early → reset button only
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedInterstitialAd = null
                loadAd()
                onAdDismissed?.invoke() ?: onAdFinished() // unblock user
            }
        }

        ad.show(activity) { _ -> rewarded = true }
    }
}