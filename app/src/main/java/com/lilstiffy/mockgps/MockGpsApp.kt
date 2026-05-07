package com.lilstiffy.mockgps

import android.app.Application
import android.preference.PreferenceManager
import com.lilstiffy.mockgps.service.VibratorService
import com.lilstiffy.mockgps.storage.StorageManager
import org.osmdroid.config.Configuration

class MockGpsApp : Application() {
    companion object {
        lateinit var shared: MockGpsApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        shared = this
        StorageManager.initialise(this)
        VibratorService.initialise(this)
        Configuration.getInstance().apply {
            load(this@MockGpsApp, PreferenceManager.getDefaultSharedPreferences(this@MockGpsApp))
            userAgentValue = packageName
        }
    }

}