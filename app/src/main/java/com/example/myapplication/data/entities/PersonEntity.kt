package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val personId: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val relation: String? = null,
    val status: PersonStatus = PersonStatus.PENDING,
    val announcePolicy: AnnouncePolicy = AnnouncePolicy.NORMAL,
    val notesForPatient: String? = null,
    val consent: Consent = Consent.UNKNOWN,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PersonStatus { PENDING, ACTIVE }
enum class AnnouncePolicy { NORMAL, GENERIC }
enum class Consent { UNKNOWN, GRANTED, DECLINED }
