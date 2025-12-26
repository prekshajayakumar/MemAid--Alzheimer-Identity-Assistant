package com.example.myapplication.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.data.dao.EncounterDao
import com.example.myapplication.data.dao.FaceVectorDao
import com.example.myapplication.data.dao.PersonDao
import com.example.myapplication.data.dao.RoutineDao
import com.example.myapplication.data.dao.GalleryDao
import com.example.myapplication.data.entities.EncounterEntity
import com.example.myapplication.data.entities.FaceVectorEntity
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.RoutineItemEntity
import com.example.myapplication.util.KeyStoreHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        PersonEntity::class,
        GalleryEntity::class,
        FaceVectorEntity::class,
        EncounterEntity::class,
        RoutineItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun galleryDao(): GalleryDao
    abstract fun vectorDao(): FaceVectorDao
    abstract fun encounterDao(): EncounterDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context)

                val passphrase: ByteArray = KeyStoreHelper.getOrCreateSqlcipherPass(context)
                val factory = SupportFactory(passphrase)

                Room.databaseBuilder(context, AppDb::class.java, "people_mem.db")
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
