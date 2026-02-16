package com.example.myapplication.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.LogsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDb.get(app.applicationContext)
    private val repo = LogsRepository(db.recognitionLogDao())

    val logs = repo.observeLatest(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() = viewModelScope.launch {
        repo.clearAll()
    }
}
