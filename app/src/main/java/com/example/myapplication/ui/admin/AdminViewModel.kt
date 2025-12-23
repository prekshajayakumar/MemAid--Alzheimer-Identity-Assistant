package com.example.myapplication.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.PeopleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PeopleRepository(AppDb.get(app))

    val people = repo.allPeople().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addPending(name: String, relation: String) = viewModelScope.launch {
        repo.addPending(name, relation)
    }

    fun addActive(name: String, relation: String) = viewModelScope.launch {
        repo.addActive(name, relation)
    }

    fun approveFirstPending() = viewModelScope.launch {
        val firstPending = people.value.firstOrNull { it.status.name == "PENDING" } ?: return@launch
        repo.approve(firstPending.personId)
    }
}
