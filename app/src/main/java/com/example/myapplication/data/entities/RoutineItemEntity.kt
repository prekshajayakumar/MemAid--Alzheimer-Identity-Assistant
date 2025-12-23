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

    // What the patient will read (short + simple)
    val label: String,

    // Minutes from midnight (e.g., 9:30 AM = 9*60+30 = 570)
    val timeMinutes: Int,

    // Repeat behavior
    val repeatRule: RepeatRule = RepeatRule.NONE,

    // For one-time events (when repeatRule = NONE)
    // store as local date string "YYYY-MM-DD"
    val date: String? = null,

    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatRule {
    NONE,        // one-time (date required)
    DAILY,       // everyday
    WEEKDAYS     // Mon–Fri
}
