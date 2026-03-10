package com.example.myapplication.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import com.example.myapplication.data.repo.RoutineRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RoutineViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDb.get(app)
    private val repo = RoutineRepository(db.routineDao())

    val allRoutines: StateFlow<List<RoutineItemEntity>> =
        repo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val nowTicker: StateFlow<LocalTime> =
        flow {
            while (true) {
                emit(LocalTime.now())
                delay(30_000)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalTime.now())

    private val todayDate: StateFlow<LocalDate> =
        flow {
            while (true) {
                emit(LocalDate.now())
                delay(60_000)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    val todaysRoutines: StateFlow<List<RoutineItemEntity>> =
        combine(allRoutines, todayDate) { items, date ->
            repo.filterForToday(items, date).sortedBy { it.timeMinutes }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentRoutine: StateFlow<RoutineItemEntity?> =
        combine(todaysRoutines, nowTicker) { items, now ->
            val nowMinutes = now.hour * 60 + now.minute
            items.firstOrNull { item ->
                val start = item.timeMinutes
                val end = item.endTimeMinutes ?: (start + 60)
                nowMinutes in start until end
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val nextRoutine: StateFlow<RoutineItemEntity?> =
        combine(todaysRoutines, nowTicker) { items, now ->
            val nowMinutes = now.hour * 60 + now.minute
            items.firstOrNull { item ->
                item.timeMinutes > nowMinutes
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addQuick(
        label: String,
        timeMinutes: Int,
        repeatRule: RepeatRule,
        date: String?,
        endTimeMinutes: Int? = null,
        expectedLocationLabel: String? = null,
        expectedLatitude: Double? = null,
        expectedLongitude: Double? = null,
        allowedRadiusMeters: Float? = null
    ) {
        viewModelScope.launch {
            repo.upsert(
                RoutineItemEntity(
                    label = label.trim(),
                    timeMinutes = timeMinutes,
                    endTimeMinutes = endTimeMinutes,
                    repeatRule = repeatRule,
                    date = date,
                    expectedLocationLabel = expectedLocationLabel,
                    expectedLatitude = expectedLatitude,
                    expectedLongitude = expectedLongitude,
                    allowedRadiusMeters = allowedRadiusMeters
                )
            )
        }
    }

    fun toggleEnabled(item: RoutineItemEntity, enabled: Boolean) {
        viewModelScope.launch {
            db.routineDao().setEnabled(item.routineId, enabled)
        }
    }

    fun delete(item: RoutineItemEntity) {
        viewModelScope.launch {
            repo.delete(item)
        }
    }

    fun isRoutineActiveNow(item: RoutineItemEntity): Boolean {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val start = item.timeMinutes
        val end = item.endTimeMinutes ?: (start + 60)
        return nowMinutes in start until end
    }

    fun isWeekday(date: LocalDate): Boolean {
        return date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }
}