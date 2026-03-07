package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "routine_items",
    indices = [Index("enabled")]
)
data class RoutineItemEntity(
    @PrimaryKey val routineId: String = UUID.randomUUID().toString(),

    val label: String,

    // start time in minutes from midnight
    val timeMinutes: Int,

    // end time in minutes from midnight
    // if null, we will assume a default duration later
    val endTimeMinutes: Int? = null,

    val repeatRule: RepeatRule = RepeatRule.NONE,

    // for one-time events when repeatRule = NONE
    val date: String? = null,

    // optional expected place for routine-aware monitoring
    val expectedLocationLabel: String? = null,

    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatRule {
    NONE,
    DAILY,
    WEEKDAYS
}