package com.example.myapplication.data.db

import androidx.room.TypeConverter
import com.example.myapplication.data.entities.AnnouncePolicy
import com.example.myapplication.data.entities.Consent
import com.example.myapplication.data.entities.PersonStatus
import com.example.myapplication.data.entities.RepeatRule

class Converters {

    @TypeConverter
    fun fromStatus(value: PersonStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): PersonStatus = PersonStatus.valueOf(value)

    @TypeConverter
    fun fromPolicy(value: AnnouncePolicy): String = value.name

    @TypeConverter
    fun toPolicy(value: String): AnnouncePolicy = AnnouncePolicy.valueOf(value)

    @TypeConverter
    fun fromConsent(value: Consent): String = value.name

    @TypeConverter
    fun toConsent(value: String): Consent = Consent.valueOf(value)

    @TypeConverter
    fun fromRepeatRule(value: RepeatRule): String = value.name

    @TypeConverter
    fun toRepeatRule(value: String): RepeatRule = RepeatRule.valueOf(value)
}
