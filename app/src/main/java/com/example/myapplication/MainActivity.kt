package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.PeopleRepository
import com.example.myapplication.ui.admin.AdminDashboardScreen
import com.example.myapplication.ui.admin.AdminPeopleScreen
import com.example.myapplication.ui.admin.AdminPinScreen
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.PatientHomeScreen
import com.example.myapplication.ui.patient.UnknownPersonScreen
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

private enum class Screen {
    PATIENT_HOME,
    CAMERA,
    UNKNOWN,
    ADMIN_PIN,
    ADMIN_ROUTINE,
    ADMIN_DASHBOARD,
    ADMIN_PEOPLE
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

                    when (screen) {

                        Screen.PATIENT_HOME -> PatientHomeScreen(
                            todayItems = today,
                            onRecognizePerson = { screen = Screen.CAMERA },
                            onCallCaregiver = {},
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
                                screen = Screen.PATIENT_HOME
                            }
                        )

                        Screen.ADMIN_PIN -> AdminPinScreen(
                            onSuccess = { screen = Screen.ADMIN_DASHBOARD },
                            onCancel = { screen = Screen.PATIENT_HOME }
                        )

                        Screen.ADMIN_ROUTINE -> AdminRoutineScreen(
                            allItems = all,
                            onBack = { screen = Screen.PATIENT_HOME },
                            onAdd = { label, time, rule, date ->
                                routineVm.addQuick(label, time, rule, date)
                            },
                            onToggle = { item, enabled ->
                                routineVm.toggleEnabled(item, enabled)
                            },
                            onDelete = { item ->
                                routineVm.delete(item)
                            }
                        )
                        Screen.ADMIN_DASHBOARD -> AdminDashboardScreen(
                            onPeople = { screen = Screen.ADMIN_PEOPLE },
                            onRoutine = { screen = Screen.ADMIN_ROUTINE },
                            onExit = { screen = Screen.PATIENT_HOME }
                        )

                        Screen.ADMIN_PEOPLE -> {
                            val pending by peopleRepo.pending().collectAsState(initial = emptyList())
                            AdminPeopleScreen(
                                pending = pending,
                                onApprove = { id: String, name: String, relation: String ->
                                scope.launch {
                                        peopleRepo.approvePending(id, name, relation)
                                    }
                                },
                                onBack = { screen = Screen.ADMIN_DASHBOARD }
                            )
                        }
                    }
                }
            }
        }
    }
}
