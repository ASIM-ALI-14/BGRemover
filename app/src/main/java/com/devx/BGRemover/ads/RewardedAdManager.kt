package com.devx.BGRemover.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val activity: Activity) {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // ✅ Store BOTH callbacks so onAdDismissed is never lost on async load
    private var pendingOnRewarded: (() -> Unit)? = null
    private var pendingOnDismissed: (() -> Unit)? = null

    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"

    fun cancel() {
        pendingOnRewarded = null
        pendingOnDismissed = null
    }

    fun loadAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad

                    // ✅ Pass BOTH stored callbacks — not just onRewarded
                    val onRewarded  = pendingOnRewarded
                    val onDismissed = pendingOnDismissed
                    if (onRewarded != null) {
                        pendingOnRewarded  = null
                        pendingOnDismissed = null
                        showAd(onRewarded, onDismissed)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null

                    // Ad unavailable — unblock user (treat as dismissed)
                    pendingOnDismissed?.invoke() ?: pendingOnRewarded?.invoke()
                    pendingOnRewarded  = null
                    pendingOnDismissed = null

                    loadAd() // retry
                }
            }
        )
    }

    fun showAd(
        onRewarded: () -> Unit,
        onAdDismissed: (() -> Unit)? = null   // ✅ renamed from onAdFailed for clarity
    ) {
        val ad = rewardedAd

        if (ad == null) {
            // ✅ Store both so they survive the async load
            pendingOnRewarded  = onRewarded
            pendingOnDismissed = onAdDismissed
            if (!isLoading) loadAd()
            return
        }

        var rewarded = false   // ✅ track whether the user actually earned the reward

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd()
                if (rewarded) {
                    onRewarded()          // watched fully → trigger action
                } else {
                    onAdDismissed?.invoke() // back-pressed → reset button only
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadAd()
                onAdDismissed?.invoke() ?: onRewarded() // unblock user
            }
        }

        ad.show(activity) { _ ->
            rewarded = true  // ✅ only flipped if user watches fully
        }
    }

    fun preload() = loadAd()
}