package com.example.myapplication.data.repo

import com.example.myapplication.data.dao.RecognitionLogDao
import com.example.myapplication.data.entities.RecognitionLogEntity
import kotlinx.coroutines.flow.Flow

class LogsRepository(
    private val dao: RecognitionLogDao
) {
    fun observeLatest(limit: Int = 200): Flow<List<RecognitionLogEntity>> = dao.observeLatest(limit)
    suspend fun insert(log: RecognitionLogEntity) = dao.insert(log)
    suspend fun clearAll() = dao.clearAll()
}
