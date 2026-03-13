package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "deviation_events",
    indices = [Index("ts"), Index("resolved"), Index("eventType")]
)
data class DeviationEventEntity(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val routineId: String? = null,
    val routineLabel: String? = null,
    val eventType: DeviationEventType,
    val details: String? = null,
    val resolved: Boolean = false
)

enum class DeviationEventType {
    UNKNOWN_PERSON_SAVED,
    DEVIATION_PROMPT,
    DEVIATION_ESCALATION,
    DEVIATION_REPEAT_ESCALATION,
    DEVIATION_SMS_5MIN,
    DEVIATION_SMS_15MIN,
    DEVIATION_SMS_20MIN,
    DEVIATION_SMS_30MIN,
    DEVIATION_SMS_REPEAT,
    CAREGIVER_CALL
}