package com.example.myapplication.data.dao

import androidx.room.*
import com.example.myapplication.data.entities.EncounterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(enc: EncounterEntity)

    @Query("SELECT * FROM encounters ORDER BY ts DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<EncounterEntity>>
}
