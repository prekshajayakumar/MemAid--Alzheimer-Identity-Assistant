package com.example.myapplication.data.dao

import androidx.room.*
import com.example.myapplication.data.entities.FaceVectorEntity

@Dao
interface FaceVectorDao {
    @Query("SELECT * FROM face_vectors WHERE personId = :pid")
    suspend fun vectorsForPerson(pid: String): List<FaceVectorEntity>

    @Query("SELECT * FROM face_vectors")
    suspend fun allVectors(): List<FaceVectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<FaceVectorEntity>)

    @Query("DELETE FROM face_vectors WHERE personId = :pid")
    suspend fun deleteForPerson(pid: String)

    @Query("UPDATE face_vectors SET personId = :toPersonId WHERE personId = :fromPersonId")
    suspend fun moveAllVectors(fromPersonId: String, toPersonId: String)
}