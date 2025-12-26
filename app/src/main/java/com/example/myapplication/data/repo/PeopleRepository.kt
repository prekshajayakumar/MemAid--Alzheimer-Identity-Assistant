package com.example.myapplication.data.repo

import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import kotlinx.coroutines.flow.Flow

class PeopleRepository(
    private val db: AppDb
) {
    private val personDao = db.personDao()
    private val galleryDao = db.galleryDao()

    fun allPeople(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun pending(): Flow<List<PersonEntity>> = personDao.observeByStatus(PersonStatus.PENDING)

    // (Legacy / admin testing) - optional to keep
    suspend fun addPending(name: String, relation: String): String {
        val p = PersonEntity(name = name, relation = relation, status = PersonStatus.PENDING)
        personDao.upsert(p)
        return p.personId
    }

    suspend fun approve(personId: String) {
        val current = personDao.getById(personId) ?: return
        personDao.upsert(current.copy(status = PersonStatus.ACTIVE))
    }

    suspend fun addActive(name: String, relation: String) {
        personDao.upsert(PersonEntity(name = name, relation = relation, status = PersonStatus.ACTIVE))
    }

    // NEW: patient flow - face-only pending
    suspend fun createPendingFromPhotoPaths(imagePaths: List<String>): String {
        val person = PersonEntity(
            name = null,
            relation = null,
            status = PersonStatus.PENDING
        )

        personDao.upsert(person)

        val galleryItems = imagePaths.map { path ->
            GalleryEntity(
                personId = person.personId,
                imagePath = path,
                pose = null,
                lighting = null,
                quality = 0f
            )
        }

        galleryDao.insertAll(galleryItems)
        return person.personId
    }

    suspend fun approvePending(
        personId: String,
        name: String,
        relation: String
    ) {
        val current = db.personDao().getById(personId) ?: return
        db.personDao().upsert(
            current.copy(
                name = name,
                relation = relation,
                status = PersonStatus.ACTIVE
            )
        )
    }
}
