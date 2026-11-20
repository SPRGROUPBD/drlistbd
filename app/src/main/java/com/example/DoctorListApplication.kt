package com.example

import android.app.Application
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.StartIoConfig

class DoctorListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start real internet reachability monitoring before initializing online ad SDKs
        ConnectivityMonitor.start(this)
        if (ConnectivityMonitor.isOnline.value) {
            // Initialize Start.io SDK at Application startup only when connectivity is present
            StartIoConfig.init(this)
        }
    }
}
