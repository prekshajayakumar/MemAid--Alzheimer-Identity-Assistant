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
import com.example.myapplication.util.NotificationChannels
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import com.example.myapplication.ml.FaceRecognitionEngine
import com.example.myapplication.ui.patient.RecognizedPersonScreen

private enum class Screen {
    PATIENT_HOME,
    CAMERA,
    UNKNOWN,
    ADMIN_PIN,
    ADMIN_DASHBOARD,
    ADMIN_PEOPLE,
    ADMIN_ROUTINE,
    ADMIN_SETTINGS,
    RECOGNIZED,
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.myapplication.ml.ModelInspector.log(this, "mobilefacenet.tflite")

        setContent {
            MaterialTheme {
                Surface(Modifier) {

                    val routineVm: RoutineViewModel = viewModel()
                    val today by routineVm.todaysRoutines.collectAsState()
                    val all by routineVm.allRoutines.collectAsState()
                    val recogEngine = remember { FaceRecognitionEngine(this@MainActivity.applicationContext) }
                    var recognizedName by remember { mutableStateOf<String?>(null) }
                    var recognizedRelation by remember { mutableStateOf<String?>(null) }


                    val scope = rememberCoroutineScope()
                    val db = AppDb.get(this)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        com.example.myapplication.ml.ThresholdExperiment.run(db)
                    }

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

                                Screen.RECOGNIZED -> RecognizedPersonScreen(
                                    name = recognizedName ?: "Unknown",
                                    relation = recognizedRelation,
                                    onDone = { screen = Screen.PATIENT_HOME }
                                )

                                Screen.CAMERA -> CameraScreen(
                                    onImageCaptured = { path ->
                                        lastCapturedPath = path

                                        scope.launch {
                                            // 1) Load bitmap
                                            val bmp = BitmapFactory.decodeFile(path)
                                            if (bmp == null) {
                                                screen = Screen.UNKNOWN
                                                return@launch
                                            }

                                            // 2) Detect + crop face
                                            val rect = com.example.myapplication.ml.FaceCropper.detectLargestFace(bmp)
                                            val face = rect?.let { com.example.myapplication.ml.FaceCropper.crop(bmp, it) }

                                            if (face == null) {
                                                screen = Screen.UNKNOWN
                                                return@launch
                                            }

                                            // 3) Recognize
                                            val personId = recogEngine.recognize(face)
                                            if (personId == null) {
                                                screen = Screen.UNKNOWN
                                                return@launch
                                            }

                                            // 4) Fetch identity
                                            val person = db.personDao().getById(personId)
                                            if (person?.name.isNullOrBlank()) {
                                                screen = Screen.UNKNOWN
                                                return@launch
                                            }

                                            recognizedName = person!!.name
                                            recognizedRelation = person.relation
                                            screen = Screen.RECOGNIZED
                                        }
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
                                                    peopleRepo.approvePendingWithEmbeddings(
                                                        appContext = this@MainActivity.applicationContext,
                                                        personId = id,
                                                        name = name,
                                                        relation = relation
                                                    )
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