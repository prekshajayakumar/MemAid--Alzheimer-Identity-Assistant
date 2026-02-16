package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "recognition_logs",
    indices = [Index("ts"), Index("outcome"), Index("personId")]
)
data class RecognitionLogEntity(
    @PrimaryKey val logId: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val outcome: RecognitionOutcome,
    val bestScore: Float? = null,
    val personId: String? = null,
    val imagePath: String? = null
)

enum class RecognitionOutcome {
    RECOGNIZED,
    UNKNOWN,
    NO_FACE,
    IMAGE_DECODE_FAIL
}
