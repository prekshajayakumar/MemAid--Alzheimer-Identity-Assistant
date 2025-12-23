package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM people WHERE personId = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: PersonStatus): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)
}
