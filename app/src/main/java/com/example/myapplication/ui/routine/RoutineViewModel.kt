package com.example.myapplication.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.RepeatRule
import com.example.myapplication.data.entities.RoutineItemEntity
import com.example.myapplication.data.repo.RoutineRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class RoutineViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDb.get(app)
    private val repo = RoutineRepository(db.routineDao())

    val allRoutines: StateFlow<List<RoutineItemEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaysRoutines: StateFlow<List<RoutineItemEntity>> =
        allRoutines
            .map { repo.filterForToday(it, LocalDate.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addQuick(label: String, timeMinutes: Int, repeatRule: RepeatRule, date: String?) {
        viewModelScope.launch {
            repo.upsert(
                RoutineItemEntity(
                    label = label.trim(),
                    timeMinutes = timeMinutes,
                    repeatRule = repeatRule,
                    date = date
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
}
