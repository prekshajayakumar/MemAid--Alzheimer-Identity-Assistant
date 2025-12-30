package com.example.myapplication.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val ROUTINE_CHANNEL_ID = "routine_reminders"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            ROUTINE_CHANNEL_ID,
            "Routine reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for daily routines"
        }

        manager.createNotificationChannel(channel)
    }
}
