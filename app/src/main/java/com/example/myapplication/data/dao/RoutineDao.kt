package com.example.myapplication.data.dao

import androidx.room.*
import com.example.myapplication.data.entities.RoutineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine_items ORDER BY timeMinutes ASC")
    fun observeAll(): Flow<List<RoutineItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RoutineItemEntity)

    @Delete
    suspend fun delete(item: RoutineItemEntity)

    @Query("UPDATE routine_items SET enabled = :enabled WHERE routineId = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
