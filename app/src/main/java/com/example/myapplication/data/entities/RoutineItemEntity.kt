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
    val timeMinutes: Int,
    val endTimeMinutes: Int? = null,

    val repeatRule: RepeatRule = RepeatRule.NONE,
    val date: String? = null,

    val expectedLocationLabel: String? = null,
    val expectedLatitude: Double? = null,
    val expectedLongitude: Double? = null,
    val allowedRadiusMeters: Float? = null,

    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatRule {
    NONE,
    DAILY,
    WEEKDAYS
}