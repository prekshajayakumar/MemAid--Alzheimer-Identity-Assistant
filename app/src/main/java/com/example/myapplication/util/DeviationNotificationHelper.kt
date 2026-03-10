package com.example.myapplication.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.R

object DeviationNotificationHelper {

    private const val CHANNEL_ID = "deviation_monitor"
    private const val CHANNEL_NAME = "Deviation Monitor"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Routine-aware location deviation alerts"
        }

        manager.createNotificationChannel(channel)
    }

    fun notifyPatientPrompt(
        context: Context,
        activityLabel: String
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Routine reminder")
            .setContentText("You planned: $activityLabel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3001, notification)
    }

    fun notifyEscalation(
        context: Context,
        activityLabel: String,
        minutesAway: Long
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Caregiver attention needed")
            .setContentText("Away from '$activityLabel' for ${minutesAway} min")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3002, notification)
    }

    fun notifyRepeatEscalation(
        context: Context,
        activityLabel: String,
        minutesAway: Long
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Deviation continues")
            .setContentText("'$activityLabel' deviation ongoing for ${minutesAway} min")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3003, notification)
    }
}