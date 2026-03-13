package com.example.myapplication.receiver

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.RoutineRepository
import com.example.myapplication.util.DeviationNotificationHelper
import com.example.myapplication.util.DeviationPrefs
import com.example.myapplication.util.RoutineContextResolver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class DeviationMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DeviationNotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            runMonitorLoop()
        }
        return START_STICKY
    }

    private suspend fun runMonitorLoop() {
        val db = AppDb.get(applicationContext)
        val repo = RoutineRepository(db.routineDao())

        while (true) {
            try {
                val all = repo.observeAll().first()
                val todayItems = repo.filterForToday(all, LocalDate.now()).sortedBy { it.timeMinutes }
                val active = RoutineContextResolver.activeRoutineForNow(todayItems)

                if (active == null || !RoutineContextResolver.hasExpectedLocation(active)) {
                    DeviationPrefs.clear(applicationContext)
                    delay(60_000)
                    continue
                }

                val currentLocation = getCurrentLocation()
                if (currentLocation == null) {
                    delay(60_000)
                    continue
                }

                val expectedLat = active.expectedLatitude
                val expectedLon = active.expectedLongitude
                if (expectedLat == null || expectedLon == null) {
                    delay(60_000)
                    continue
                }

                val radius = active.allowedRadiusMeters ?: 150f

                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    expectedLat,
                    expectedLon,
                    results
                )
                val distanceMeters = results[0]

                if (distanceMeters <= radius) {
                    DeviationPrefs.clear(applicationContext)
                } else {
                    handleDeviation(active.routineId, active.label)
                }
            } catch (_: Exception) {
            }

            delay(60_000)
        }
    }

    private fun handleDeviation(routineId: String, label: String) {
        val now = System.currentTimeMillis()
        val savedRoutineId = DeviationPrefs.getRoutineId(applicationContext)
        var startMs = DeviationPrefs.getStartMs(applicationContext)

        if (savedRoutineId != routineId || startMs == 0L) {
            startMs = now
            DeviationPrefs.saveDeviationStart(applicationContext, routineId, now)
            DeviationPrefs.saveEscalation(applicationContext, 0, 0L)
        }

        val elapsedMinutes = (now - startMs) / 60_000
        val lastLevel = DeviationPrefs.getLastLevel(applicationContext)
        val lastEscalationMs = DeviationPrefs.getLastEscalationMs(applicationContext)

        if (elapsedMinutes >= 2 && lastLevel < 1) {
            DeviationNotificationHelper.notifyPatientPrompt(applicationContext, label)
            DeviationPrefs.saveEscalation(applicationContext, 1, now)
            return
        }

        if (elapsedMinutes >= 5 && lastLevel < 2) {
            DeviationNotificationHelper.notifyEscalation(applicationContext, label, elapsedMinutes)
            DeviationPrefs.saveEscalation(applicationContext, 2, now)
            return
        }

        if (elapsedMinutes >= 10 && now - lastEscalationMs >= 10 * 60_000) {
            DeviationNotificationHelper.notifyRepeatEscalation(applicationContext, label, elapsedMinutes)
            DeviationPrefs.saveEscalation(applicationContext, 2, now)
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        val fine = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) return null

        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)
        val tokenSource = CancellationTokenSource()

        return try {
            val current = fused
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .await()

            current ?: fused.lastLocation.await()
        } catch (_: Exception) {
            try {
                fused.lastLocation.await()
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}