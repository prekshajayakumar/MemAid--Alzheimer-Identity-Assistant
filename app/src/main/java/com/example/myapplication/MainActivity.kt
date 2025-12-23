package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.PatientHomeScreen
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import com.example.myapplication.ui.admin.AdminPinScreen

private enum class Screen {
    PATIENT_HOME,
    ADMIN_PIN,
    ADMIN_ROUTINE,
    CAMERA
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
                    var screen by remember { mutableStateOf(Screen.PATIENT_HOME) }

                    when (screen) {
                        Screen.PATIENT_HOME -> PatientHomeScreen(
                            todayItems = today,
                            onRecognizePerson = { screen = Screen.CAMERA },
                            onCallCaregiver = { /* Module 3 later */ },
                            onOpenAdminForNow = { screen = Screen.ADMIN_PIN }
                        )
                        Screen.ADMIN_PIN -> AdminPinScreen(
                            onSuccess = { screen = Screen.ADMIN_ROUTINE },
                            onCancel  = { screen = Screen.PATIENT_HOME }
                        )
                        Screen.ADMIN_ROUTINE -> AdminRoutineScreen(
                            allItems = all,
                            onBack = { screen = Screen.PATIENT_HOME },
                            onAdd = { label, timeMinutes, rule, date ->
                                routineVm.addQuick(label, timeMinutes, rule, date)
                            },
                            onToggle = { item, enabled -> routineVm.toggleEnabled(item, enabled) },
                            onDelete = { item -> routineVm.delete(item) }
                        )
                        Screen.CAMERA -> CameraScreen()
                    }
                }
            }
        }
    }
}
