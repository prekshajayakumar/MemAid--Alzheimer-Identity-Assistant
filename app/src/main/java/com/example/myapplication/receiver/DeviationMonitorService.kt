package com.example.myapplication.receiver

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType
import com.example.myapplication.data.entities.RoutineItemEntity
import com.example.myapplication.data.repo.DeviationEventRepository
import com.example.myapplication.data.repo.RoutineRepository
import com.example.myapplication.util.CaregiverPrefs
import com.example.myapplication.util.DeviationNotificationHelper
import com.example.myapplication.util.DeviationPrefs
import com.example.myapplication.util.RoutineContextResolver
import com.example.myapplication.util.SmsHelper
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
        val eventRepo = DeviationEventRepository(db.deviationEventDao())

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
                    handleDeviation(active, eventRepo)
                }
            } catch (_: Exception) {
            }

            delay(60_000)
        }
    }

    private suspend fun handleDeviation(
        item: RoutineItemEntity,
        eventRepo: DeviationEventRepository
    ) {
        val now = System.currentTimeMillis()
        val savedRoutineId = DeviationPrefs.getRoutineId(applicationContext)
        var startMs = DeviationPrefs.getStartMs(applicationContext)

        if (savedRoutineId != item.routineId || startMs == 0L) {
            startMs = now
            DeviationPrefs.saveDeviationStart(applicationContext, item.routineId, now)
            DeviationPrefs.saveEscalation(applicationContext, 0, 0L)
        }

        val elapsedMinutes = (now - startMs) / 60_000
        val lastLevel = DeviationPrefs.getLastLevel(applicationContext)

        if (elapsedMinutes >= 2 && lastLevel < 1) {
            DeviationNotificationHelper.notifyPatientPrompt(applicationContext, item.label)
            eventRepo.insert(
                DeviationEventEntity(
                    routineId = item.routineId,
                    routineLabel = item.label,
                    eventType = DeviationEventType.DEVIATION_PROMPT,
                    details = "Patient prompt shown after 2 minutes of deviation."
                )
            )
            DeviationPrefs.saveEscalation(applicationContext, 1, now)
            return
        }

        if (elapsedMinutes >= 5 && lastLevel < 2) {
            DeviationNotificationHelper.notifyEscalation(applicationContext, item.label, elapsedMinutes)

            eventRepo.insert(
                DeviationEventEntity(
                    routineId = item.routineId,
                    routineLabel = item.label,
                    eventType = DeviationEventType.DEVIATION_ESCALATION,
                    details = "Deviation continued for 5 minutes."
                )
            )

            sendCaregiverSms(
                item = item,
                elapsedMinutes = elapsedMinutes,
                isRepeat = false,
                eventRepo = eventRepo
            )

            DeviationPrefs.saveEscalation(applicationContext, 2, now)
            return
        }

        if (elapsedMinutes >= 15 && lastLevel < 3) {
            DeviationNotificationHelper.notifyRepeatEscalation(applicationContext, item.label, elapsedMinutes)

            eventRepo.insert(
                DeviationEventEntity(
                    routineId = item.routineId,
                    routineLabel = item.label,
                    eventType = DeviationEventType.DEVIATION_REPEAT_ESCALATION,
                    details = "Deviation continued for 15 minutes."
                )
            )

            sendCaregiverSms(
                item = item,
                elapsedMinutes = elapsedMinutes,
                isRepeat = true,
                eventRepo = eventRepo
            )

            DeviationPrefs.saveEscalation(applicationContext, 3, now)
        }
    }

    private suspend fun sendCaregiverSms(
        item: RoutineItemEntity,
        elapsedMinutes: Long,
        isRepeat: Boolean,
        eventRepo: DeviationEventRepository
    ) {
        val caregiverPhone = CaregiverPrefs.getPhone(applicationContext)
        if (caregiverPhone.isNullOrBlank()) return

        val place = item.expectedLocationLabel?.takeIf { it.isNotBlank() } ?: "the expected place"
        val window = "${formatTime(item.timeMinutes)} to ${formatTime(item.endTimeMinutes ?: (item.timeMinutes + 60))}"

        val message = if (!isRepeat) {
            "MemAid alert: The patient was scheduled to be at $place from $window for '${item.label}', but they are not at the expected location. This has continued for $elapsedMinutes minutes. Please check on them."
        } else {
            "MemAid alert: The patient is still not at $place for scheduled activity '${item.label}' ($window). The deviation has now continued for $elapsedMinutes minutes. Please check on them urgently."
        }

        val sent = SmsHelper.sendText(
            context = applicationContext,
            phone = caregiverPhone,
            message = message
        )

        if (sent) {
            eventRepo.insert(
                DeviationEventEntity(
                    routineId = item.routineId,
                    routineLabel = item.label,
                    eventType = if (isRepeat) {
                        DeviationEventType.DEVIATION_SMS_15MIN
                    } else {
                        DeviationEventType.DEVIATION_SMS_5MIN
                    },
                    details = message
                )
            )
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

    private fun formatTime(minutes: Int): String {
        val h24 = (minutes / 60) % 24
        val m = minutes % 60
        val ampm = if (h24 < 12) "AM" else "PM"
        val h12 = when (val v = h24 % 12) {
            0 -> 12
            else -> v
        }
        val mm = if (m < 10) "0$m" else "$m"
        return "$h12:$mm $ampm"
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}