package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.PeopleRepository
import com.example.myapplication.ui.admin.AdminDashboardScreen
import com.example.myapplication.ui.admin.AdminPeopleScreen
import com.example.myapplication.ui.admin.AdminPinScreen
import com.example.myapplication.ui.admin.AdminSettingsScreen
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.PatientHomeScreen
import com.example.myapplication.ui.patient.UnknownPersonScreen
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import com.example.myapplication.util.CallCaregiver
import com.example.myapplication.util.CaregiverPrefs
import kotlinx.coroutines.launch

private enum class Screen {
    PATIENT_HOME,
    CAMERA,
    UNKNOWN,
    ADMIN_PIN,
    ADMIN_DASHBOARD,
    ADMIN_PEOPLE,
    ADMIN_ROUTINE,
    ADMIN_SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier) {

                    val routineVm: RoutineViewModel = viewModel()
                    val today by routineVm.todaysRoutines.collectAsState()
                    val all by routineVm.allRoutines.collectAsState()

                    val scope = rememberCoroutineScope()
                    val db = AppDb.get(this)
                    val peopleRepo = remember { PeopleRepository(db) }

                    var screen by remember { mutableStateOf(Screen.PATIENT_HOME) }
                    var lastCapturedPath by remember { mutableStateOf<String?>(null) }

                    // ---- Admin session ----
                    var adminAuthedAt by remember { mutableStateOf<Long?>(null) }
                    val ADMIN_TIMEOUT_MS = 2 * 60 * 1000L

                    fun isAdminExpired(): Boolean {
                        val t = adminAuthedAt ?: return true
                        return (System.currentTimeMillis() - t) > ADMIN_TIMEOUT_MS
                    }

                    fun touchAdminSession() {
                        adminAuthedAt = System.currentTimeMillis()
                    }

                    // ---- Snackbar ----
                    val snack = remember { SnackbarHostState() }

                    fun callCaregiver() {
                        val phone = CaregiverPrefs.getPhone(this@MainActivity)
                        if (phone.isNullOrBlank()) {
                            scope.launch {
                                snack.showSnackbar("Set caregiver number in Admin → Settings")
                            }
                        } else {
                            CallCaregiver.dial(this@MainActivity, phone)
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snack) }
                    ) { padding ->

                        Box(modifier = Modifier.padding(padding)) {

                            when (screen) {

                                Screen.PATIENT_HOME -> PatientHomeScreen(
                                    todayItems = today,
                                    onRecognizePerson = { screen = Screen.CAMERA },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    },
                                    onOpenAdminForNow = { screen = Screen.ADMIN_PIN }
                                )

                                Screen.CAMERA -> CameraScreen(
                                    onImageCaptured = { path ->
                                        lastCapturedPath = path
                                        screen = Screen.UNKNOWN
                                    },
                                    onCancel = { screen = Screen.PATIENT_HOME }
                                )

                                Screen.UNKNOWN -> UnknownPersonScreen(
                                    onHelpMeRemember = {
                                        lastCapturedPath?.let { path ->
                                            scope.launch {
                                                peopleRepo.createPendingFromPhotoPaths(listOf(path))
                                                screen = Screen.PATIENT_HOME
                                            }
                                        }
                                    },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    }
                                )

                                Screen.ADMIN_PIN -> AdminPinScreen(
                                    onSuccess = {
                                        touchAdminSession()
                                        screen = Screen.ADMIN_DASHBOARD
                                    },
                                    onCancel = { screen = Screen.PATIENT_HOME }
                                )

                                Screen.ADMIN_DASHBOARD -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        AdminDashboardScreen(
                                            onPeople = { screen = Screen.ADMIN_PEOPLE },
                                            onRoutine = { screen = Screen.ADMIN_ROUTINE },
                                            onSettings = { screen = Screen.ADMIN_SETTINGS },
                                            onExit = {
                                                adminAuthedAt = null
                                                screen = Screen.PATIENT_HOME
                                            }
                                        )
                                    }
                                }

                                Screen.ADMIN_PEOPLE -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        val pending by peopleRepo.pending()
                                            .collectAsState(initial = emptyList())
                                        AdminPeopleScreen(
                                            pending = pending,
                                            onApprove = { id, name, relation ->
                                                touchAdminSession()
                                                scope.launch {
                                                    peopleRepo.approvePending(id, name, relation)
                                                }
                                            },
                                            onBack = { screen = Screen.ADMIN_DASHBOARD }
                                        )
                                    }
                                }

                                Screen.ADMIN_ROUTINE -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        AdminRoutineScreen(
                                            allItems = all,
                                            onBack = { screen = Screen.ADMIN_DASHBOARD },
                                            onAdd = { label, time, rule, date ->
                                                touchAdminSession()
                                                routineVm.addQuick(label, time, rule, date)
                                            },
                                            onToggle = { item, enabled ->
                                                touchAdminSession()
                                                routineVm.toggleEnabled(item, enabled)
                                            },
                                            onDelete = { item ->
                                                touchAdminSession()
                                                routineVm.delete(item)
                                            }
                                        )
                                    }
                                }

                                Screen.ADMIN_SETTINGS -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        AdminSettingsScreen(
                                            onBack = { screen = Screen.ADMIN_DASHBOARD }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}