package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.entities.DeviationEventEntity

@Dao
interface DeviationEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DeviationEventEntity)

    @Query("SELECT * FROM deviation_events WHERE resolved = 0 AND ts >= :sinceTs ORDER BY ts ASC")
    suspend fun recentUnresolvedSince(sinceTs: Long): List<DeviationEventEntity>

    @Query("UPDATE deviation_events SET resolved = 1 WHERE eventId IN (:ids)")
    suspend fun markResolved(ids: List<String>)

    @Query("DELETE FROM deviation_events")
    suspend fun clearAll()
}