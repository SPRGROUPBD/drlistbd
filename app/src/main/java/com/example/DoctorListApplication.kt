package com.example

import android.app.Application
import com.example.ui.ads.StartIoConfig

class DoctorListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Start.io SDK at Application startup so config & Advertising ID
        // finish resolving during SplashScreen before the first ad request fires.
        StartIoConfig.init(this)
    }
}
