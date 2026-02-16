package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.entities.RecognitionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RecognitionLogEntity)

    @Query("SELECT * FROM recognition_logs ORDER BY ts DESC LIMIT :limit")
    fun observeLatest(limit: Int = 200): Flow<List<RecognitionLogEntity>>

    @Query("DELETE FROM recognition_logs")
    suspend fun clearAll()
}
