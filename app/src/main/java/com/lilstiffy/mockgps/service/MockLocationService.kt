package com.lilstiffy.mockgps.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.lilstiffy.mockgps.MockGpsApp
import com.lilstiffy.mockgps.R
import com.lilstiffy.mockgps.model.LatLng
import com.lilstiffy.mockgps.storage.StorageManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    companion object {
        const val TAG = "MockLocationService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "mock_gps_channel"
        var instance: MockLocationService? = null
    }

    var isMocking = false
        private set

    lateinit var latLng: LatLng

    private val locationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return MockLocationBinder()
    }

    override fun onDestroy() {
        stopMockingLocation()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    fun toggleMocking() {
        if (isMocking) stopMockingLocation() else startMockingLocation()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private fun startMockingLocation() {
        StorageManager.addLocationToHistory(latLng)

        if (!isMocking) {
            isMocking = true

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MockGPS active")
                .setContentText("Mocking location: ${latLng.latitude}, ${latLng.longitude}")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            GlobalScope.launch(Dispatchers.IO) {
                mockLocation()
            }
            Log.d(TAG, "Mock location started")
        }
    }

    private fun stopMockingLocation() {
        if (isMocking) {
            isMocking = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "Mock location stopped")
        }
    }

    private fun addTestProvider(): Boolean {
        val providerName = LocationManager.GPS_PROVIDER

        try {
            locationManager.addTestProvider(
                providerName,
                true, false, false, false, false, false, false,
                ProviderProperties.POWER_USAGE_HIGH,
                ProviderProperties.ACCURACY_FINE
            )
            return true
        } catch (se: SecurityException) {
            val ctx = MockGpsApp.shared.applicationContext
            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    ctx,
                    "Mock location failed, you must set this app as your selected mock locations app.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
    }

    private suspend fun mockLocation() {
        val providerAdded = addTestProvider()
        if (!providerAdded)
            return

        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        while (isMocking) {
            val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                altitude = 12.5
                time = System.currentTimeMillis()
                accuracy = 2f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
            kotlinx.coroutines.delay(200L)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mock GPS",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while location mocking is active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    inner class MockLocationBinder : Binder() {
        fun getService(): MockLocationService {
            return this@MockLocationService
        }
    }
}
