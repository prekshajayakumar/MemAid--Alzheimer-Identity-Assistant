package com.example.myapplication.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.myapplication.receiver.RoutineReminderReceiver
import com.example.myapplication.data.entities.RoutineItemEntity
import java.util.Calendar

object RoutineReminderScheduler {

    fun schedule(context: Context, item: RoutineItemEntity) {
        if (!item.enabled) return

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, RoutineReminderReceiver::class.java).apply {
            putExtra("label", item.label)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            item.routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.timeMinutes / 60)
            set(Calendar.MINUTE, item.timeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pending
        )
    }

    fun cancel(context: Context, item: RoutineItemEntity) {
        val intent = Intent(context, RoutineReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            item.routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
    }
}
