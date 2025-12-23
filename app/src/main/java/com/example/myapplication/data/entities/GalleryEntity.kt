package com.example.myapplication.data.entities

import androidx.room.*

@Entity(
    tableName = "gallery",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["personId"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("personId")]
)
data class GalleryEntity(
    @PrimaryKey val galleryId: String = java.util.UUID.randomUUID().toString(),
    val personId: String,
    val imagePath: String,
    val pose: String?,
    val lighting: String?,
    val quality: Float,
    val ts: Long = System.currentTimeMillis()
)
