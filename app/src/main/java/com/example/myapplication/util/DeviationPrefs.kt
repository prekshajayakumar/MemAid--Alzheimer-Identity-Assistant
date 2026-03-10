package com.example.myapplication.util

import android.content.Context

object DeviationPrefs {
    private const val FILE = "deviation_prefs"
    private const val KEY_ROUTINE_ID = "routine_id"
    private const val KEY_START_MS = "start_ms"
    private const val KEY_LAST_ESCALATION_MS = "last_escalation_ms"
    private const val KEY_LAST_LEVEL = "last_level"

    fun saveDeviationStart(context: Context, routineId: String, startMs: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTINE_ID, routineId)
            .putLong(KEY_START_MS, startMs)
            .apply()
    }

    fun getRoutineId(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_ROUTINE_ID, null)

    fun getStartMs(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(KEY_START_MS, 0L)

    fun saveEscalation(context: Context, level: Int, ts: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_LEVEL, level)
            .putLong(KEY_LAST_ESCALATION_MS, ts)
            .apply()
    }

    fun getLastLevel(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_LEVEL, 0)

    fun getLastEscalationMs(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_ESCALATION_MS, 0L)

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}