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

    private val repo = PeopleRepository(AppDb.get(app.applicationContext))

    val people = repo.allPeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approvePending(personId: String, name: String, relation: String) = viewModelScope.launch {
        repo.approvePendingWithEmbeddings(
            appContext = getApplication<Application>().applicationContext,
            personId = personId,
            name = name,
            relation = relation
        )
    }
}
