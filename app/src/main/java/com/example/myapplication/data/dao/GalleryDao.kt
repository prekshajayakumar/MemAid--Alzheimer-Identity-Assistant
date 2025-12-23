package com.example.myapplication.data.dao

import androidx.room.*
import com.example.myapplication.data.entities.GalleryEntity

@Dao
interface GalleryDao {
    @Query("SELECT * FROM gallery WHERE personId = :pid")
    suspend fun listForPerson(pid: String): List<GalleryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GalleryEntity>)
}
