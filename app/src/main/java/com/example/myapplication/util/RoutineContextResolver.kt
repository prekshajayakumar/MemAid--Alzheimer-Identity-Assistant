package com.example.myapplication.util

import com.example.myapplication.data.entities.RoutineItemEntity
import java.time.LocalTime

object RoutineContextResolver {

    fun activeRoutineForNow(
        todaysRoutines: List<RoutineItemEntity>,
        now: LocalTime = LocalTime.now()
    ): RoutineItemEntity? {
        val nowMinutes = now.hour * 60 + now.minute

        return todaysRoutines.firstOrNull { item ->
            val start = item.timeMinutes
            val end = item.endTimeMinutes ?: (start + 60)
            nowMinutes in start until end
        }
    }

    fun hasExpectedLocation(item: RoutineItemEntity): Boolean {
        return item.expectedLatitude != null &&
                item.expectedLongitude != null &&
                item.allowedRadiusMeters != null
    }
}