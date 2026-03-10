package com.example.myapplication.data.repo

import com.example.myapplication.data.dao.DeviationEventDao
import com.example.myapplication.data.entities.DeviationEventEntity

class DeviationEventRepository(
    private val dao: DeviationEventDao
) {
    suspend fun insert(event: DeviationEventEntity) = dao.insert(event)

    suspend fun recentUnresolvedSince(sinceTs: Long): List<DeviationEventEntity> =
        dao.recentUnresolvedSince(sinceTs)

    suspend fun markResolved(ids: List<String>) = dao.markResolved(ids)

    suspend fun clearAll() = dao.clearAll()
}