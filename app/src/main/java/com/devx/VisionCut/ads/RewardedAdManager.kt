package com.devx.VisionCut.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val activity: Activity) {

    companion object {
        // TODO: replace with real ad unit ID from AdMob console before publishing
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // Both callbacks stored so onAdDismissed is not lost during async load
    private var pendingOnRewarded : (() -> Unit)? = null
    private var pendingOnDismissed: (() -> Unit)? = null

    /** Clear any queued callbacks (e.g. when the screen is disposed). */
    fun cancel() {
        pendingOnRewarded  = null
        pendingOnDismissed = null
    }

    fun loadAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        RewardedAd.load(
            activity,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading    = false
                    rewardedAd   = ad

                    // If a showAd() call was waiting, replay it now
                    val onRewarded  = pendingOnRewarded
                    val onDismissed = pendingOnDismissed
                    if (onRewarded != null) {
                        pendingOnRewarded  = null
                        pendingOnDismissed = null
                        showAd(onRewarded, onDismissed)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading  = false
                    rewardedAd = null

                    // Unblock the user — treat failure as a dismiss
                    pendingOnDismissed?.invoke() ?: pendingOnRewarded?.invoke()
                    pendingOnRewarded  = null
                    pendingOnDismissed = null
                    loadAd() // retry for next use
                }
            }
        )
    }

    fun showAd(
        onRewarded   : () -> Unit,
        onAdDismissed: (() -> Unit)? = null
    ) {
        val ad = rewardedAd

        if (ad == null) {
            // Ad not ready — queue callbacks and start loading
            pendingOnRewarded  = onRewarded
            pendingOnDismissed = onAdDismissed
            if (!isLoading) loadAd()
            return
        }

        var rewarded = false // flipped only when the user earns the reward

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd()
                if (rewarded) {
                    onRewarded()            // user watched fully → trigger action
                } else {
                    onAdDismissed?.invoke() // user dismissed early → reset button only
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadAd()
                onAdDismissed?.invoke() ?: onRewarded() // unblock user
            }
        }

        ad.show(activity) { _ -> rewarded = true }
    }

    /** Pre-fetch an ad so it is ready when the user taps. */
    fun preload() = loadAd()
}