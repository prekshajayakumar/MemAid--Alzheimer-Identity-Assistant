package com.example.myapplication

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.repo.PeopleRepository
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceRecognitionEngine
import com.example.myapplication.ui.admin.*
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.*
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import com.example.myapplication.util.CallCaregiver
import com.example.myapplication.util.CaregiverPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen {
    PATIENT_HOME,
    CAMERA,
    UNKNOWN,
    RECOGNIZED,
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

                    val db = AppDb.get(this)
                    val peopleRepo = remember { PeopleRepository(db) }

                    val recogEngine = remember {
                        FaceRecognitionEngine(applicationContext)
                    }

                    var recognizedName by remember { mutableStateOf<String?>(null) }
                    var recognizedRelation by remember { mutableStateOf<String?>(null) }

                    var screen by remember { mutableStateOf(Screen.PATIENT_HOME) }

                    val scope = rememberCoroutineScope()
                    val snack = remember { SnackbarHostState() }

                    // ---- Admin session control ----
                    var adminAuthedAt by remember { mutableStateOf<Long?>(null) }
                    val ADMIN_TIMEOUT_MS = 2 * 60 * 1000L

                    fun isAdminExpired(): Boolean {
                        val t = adminAuthedAt ?: return true
                        return (System.currentTimeMillis() - t) > ADMIN_TIMEOUT_MS
                    }

                    fun touchAdminSession() {
                        adminAuthedAt = System.currentTimeMillis()
                    }

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

                                // ---------------- PATIENT HOME ----------------
                                Screen.PATIENT_HOME -> PatientHomeScreen(
                                    todayItems = today,
                                    onRecognizePerson = { screen = Screen.CAMERA },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    },
                                    onOpenAdminForNow = { screen = Screen.ADMIN_PIN }
                                )

                                // ---------------- CAMERA ----------------
                                Screen.CAMERA -> CameraScreen(
                                    onImageCaptured = { path ->

                                        scope.launch(Dispatchers.Default) {

                                            val bmp = BitmapFactory.decodeFile(path)
                                            if (bmp == null) {
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            val rect = FaceCropper.detectLargestFace(bmp)
                                            val face = rect?.let {
                                                FaceCropper.crop(bmp, it)
                                            }

                                            if (face == null) {
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            val personId = recogEngine.recognize(face)

                                            if (personId == null) {
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            val person = db.personDao().getById(personId)

                                            if (person?.name.isNullOrBlank()) {
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            withContext(Dispatchers.Main) {
                                                recognizedName = person!!.name
                                                recognizedRelation = person.relation
                                                screen = Screen.RECOGNIZED
                                            }
                                        }
                                    },
                                    onCancel = { screen = Screen.PATIENT_HOME }
                                )

                                // ---------------- RECOGNIZED ----------------
                                Screen.RECOGNIZED -> RecognizedPersonScreen(
                                    name = recognizedName ?: "Unknown",
                                    relation = recognizedRelation,
                                    onDone = { screen = Screen.PATIENT_HOME }
                                )

                                // ---------------- UNKNOWN ----------------
                                Screen.UNKNOWN -> UnknownPersonScreen(
                                    onHelpMeRemember = {
                                        scope.launch {
                                            // Photo already saved via CameraScreen
                                            // Add as pending
                                            // You can extend to pass multiple frames later
                                            screen = Screen.PATIENT_HOME
                                        }
                                    },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    }
                                )

                                // ---------------- ADMIN PIN ----------------
                                Screen.ADMIN_PIN -> AdminPinScreen(
                                    onSuccess = {
                                        touchAdminSession()
                                        screen = Screen.ADMIN_DASHBOARD
                                    },
                                    onCancel = { screen = Screen.PATIENT_HOME }
                                )

                                // ---------------- ADMIN DASHBOARD ----------------
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

                                // ---------------- ADMIN PEOPLE ----------------
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
                                                        applicationContext,
                                                        id,
                                                        name,
                                                        relation
                                                    )
                                                }
                                            },
                                            onBack = { screen = Screen.ADMIN_DASHBOARD }
                                        )
                                    }
                                }

                                // ---------------- ADMIN ROUTINE ----------------
                                Screen.ADMIN_ROUTINE -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        AdminRoutineScreen(
                                            allItems = all,
                                            onBack = { screen = Screen.ADMIN_DASHBOARD },
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
                                    }
                                }

                                // ---------------- ADMIN SETTINGS ----------------
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