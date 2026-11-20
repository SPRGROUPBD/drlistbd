package com.example

import android.app.Application
import com.example.ui.ads.ConnectivityMonitor
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

/**
 * Application entry point.
 * Monitors real internet reachability so:
 * - ONLINE:  Start.io Ads + Unity Ads + Custom Direct Sponsor Ads are active.
 * - OFFLINE: Network SDKs are strictly disabled and cached Direct Sponsor Ads ONLY are shown.
 * Google AdMob is strictly removed.
 */
class DoctorListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            StartAppSDK.setTestAdsEnabled(false)
            StartAppAd.disableSplash()
        }
        ConnectivityMonitor.start(this)
    }
}
