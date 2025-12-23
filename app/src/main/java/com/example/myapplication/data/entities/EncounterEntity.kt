package com.example.myapplication.data.entities

import androidx.room.*

@Entity(tableName = "encounters", indices = [Index("personId")])
data class EncounterEntity(
    @PrimaryKey val encounterId: String = java.util.UUID.randomUUID().toString(),
    val personId: String?,  // null if unknown
    val conf: Float,
    val place: String?,
    val ts: Long = System.currentTimeMillis(),
    val summary: String? = null
)
