package com.example.myapplication.data.repo

import com.example.myapplication.data.dao.RoutineDao
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class RoutineRepository(
    private val dao: RoutineDao
) {
    fun observeAll(): Flow<List<RoutineItemEntity>> = dao.observeAll()

    suspend fun upsert(item: RoutineItemEntity) = dao.upsert(item)

    suspend fun delete(item: RoutineItemEntity) = dao.delete(item)

    /**
     * Patient view: today's filtered list.
     * date is LocalDate.now() usually.
     */
    fun filterForToday(all: List<RoutineItemEntity>, date: LocalDate): List<RoutineItemEntity> {
        val dayOfWeek = date.dayOfWeek // MON..SUN
        val dateStr = date.toString()  // "YYYY-MM-DD"

        return all
            .asSequence()
            .filter { it.enabled }
            .filter { item ->
                when (item.repeatRule) {
                    RepeatRule.DAILY -> true
                    RepeatRule.WEEKDAYS -> dayOfWeek.value in 1..5
                    RepeatRule.NONE -> item.date == dateStr
                }
            }
            .sortedBy { it.timeMinutes }
            .toList()
    }
}
