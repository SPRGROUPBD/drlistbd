package com.example

import android.app.Application
import android.util.Log
import com.example.notifications.PushNotificationManager
import com.example.ui.ads.ConnectivityMonitor
import com.google.firebase.FirebaseApp

/** Application entry point for Doctor List BD. Custom Direct Sponsor Ads are managed by Firebase. */
class DoctorListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }.onFailure { err ->
            Log.w("DoctorListApp", "FirebaseApp init fallback warning: ${err.message}")
        }
        runCatching {
            PushNotificationManager.ensureNotificationChannel(this)
        }.onFailure { err ->
            Log.w("DoctorListApp", "Notification channel init warning: ${err.message}")
        }
        runCatching {
            ConnectivityMonitor.start(this)
        }.onFailure { err ->
            Log.w("DoctorListApp", "ConnectivityMonitor start warning: ${err.message}")
        }
    }
}
