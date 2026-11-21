package com.example

import android.app.Application
import android.os.Build
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.HybridNetworkAdController
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
        val useTestAds = BuildConfig.DEBUG && HybridNetworkAdController.isDebugOrEmulator()
        runCatching {
            StartAppSDK.setTestAdsEnabled(useTestAds)
            StartAppAd.disableSplash()
        }
        ConnectivityMonitor.start(this)
    }

    override fun getSystemService(name: String): Any? {
        if (BuildConfig.DEBUG &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            HybridNetworkAdController.isDebugOrEmulator() &&
            (name.contains("adservices", ignoreCase = true) ||
                name.contains("measurement", ignoreCase = true))
        ) {
            return null
        }
        return super.getSystemService(name)
    }
}
