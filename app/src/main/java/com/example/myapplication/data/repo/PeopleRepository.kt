package com.example.myapplication.data.repo


import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.PersonStatus
import kotlinx.coroutines.flow.Flow

class PeopleRepository(private val db: AppDb) {
    fun allPeople(): Flow<List<PersonEntity>> = db.personDao().observeAll()
    fun pending(): Flow<List<PersonEntity>> = db.personDao().observeByStatus(PersonStatus.PENDING)

    suspend fun addPending(name: String, relation: String): String {
        val p = PersonEntity(name = name, relation = relation, status = PersonStatus.PENDING)
        db.personDao().upsert(p)
        return p.personId
    }

    suspend fun approve(personId: String) {
        val current = db.personDao().getById(personId) ?: return
        db.personDao().upsert(current.copy(status = PersonStatus.ACTIVE))
    }

    suspend fun addActive(name: String, relation: String) {
        db.personDao().upsert(PersonEntity(name = name, relation = relation, status = PersonStatus.ACTIVE))
    }
}
