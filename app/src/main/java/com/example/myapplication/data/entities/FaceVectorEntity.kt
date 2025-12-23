package com.example.myapplication.data.entities

import androidx.room.*

@Entity(
    tableName = "face_vectors",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["personId"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("personId")]
)
data class FaceVectorEntity(
    @PrimaryKey val vectorId: String = java.util.UUID.randomUUID().toString(),
    val personId: String,
    val embedding: ByteArray,
    val quality: Float,
    val ts: Long = System.currentTimeMillis()
)
