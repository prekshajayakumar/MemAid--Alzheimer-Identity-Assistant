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
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import com.example.myapplication.data.repo.LogsRepository
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
    ADMIN_SETTINGS,
    ADMIN_LOGS
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

                    val db = remember { AppDb.get(applicationContext) }
                    val peopleRepo = remember { PeopleRepository(db) }
                    val logsRepo = remember { LogsRepository(db.recognitionLogDao()) }

                    val recogEngine = remember { FaceRecognitionEngine(applicationContext) }

                    var recognizedName by remember { mutableStateOf<String?>(null) }
                    var recognizedRelation by remember { mutableStateOf<String?>(null) }

                    var screen by remember { mutableStateOf(Screen.PATIENT_HOME) }
                    var lastCapturedPath by remember { mutableStateOf<String?>(null) }

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

                    fun logRecognition(
                        outcome: RecognitionOutcome,
                        imagePath: String?,
                        bestScore: Float?,
                        personId: String?
                    ) {
                        scope.launch {
                            logsRepo.insert(
                                RecognitionLogEntity(
                                    outcome = outcome,
                                    bestScore = bestScore,
                                    threshold = 0.80f,
                                    personId = personId,
                                    imagePath = imagePath
                                )
                            )
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

                                        scope.launch(Dispatchers.Default) {
                                            try {
                                                val bmp = BitmapFactory.decodeFile(path)
                                                if (bmp == null) {
                                                    logRecognition(
                                                        outcome = RecognitionOutcome.IMAGE_DECODE_FAIL,
                                                        imagePath = path,
                                                        bestScore = null,
                                                        personId = null
                                                    )
                                                    withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                    return@launch
                                                }

                                                val rect = FaceCropper.detectLargestFace(bmp)
                                                val face = rect?.let { FaceCropper.crop(bmp, it) }

                                                if (face == null) {
                                                    logRecognition(
                                                        outcome = RecognitionOutcome.NO_FACE,
                                                        imagePath = path,
                                                        bestScore = null,
                                                        personId = null
                                                    )
                                                    withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                    return@launch
                                                }

                                                val result = recogEngine.recognize(face)

                                                if (result.personId == null) {
                                                    logRecognition(
                                                        outcome = RecognitionOutcome.UNKNOWN,
                                                        imagePath = path,
                                                        bestScore = result.bestScore,
                                                        personId = null
                                                    )
                                                    withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                    return@launch
                                                }

                                                val person = db.personDao().getById(result.personId)

                                                if (person?.name.isNullOrBlank()) {
                                                    logRecognition(
                                                        outcome = RecognitionOutcome.UNKNOWN,
                                                        imagePath = path,
                                                        bestScore = result.bestScore,
                                                        personId = result.personId
                                                    )
                                                    withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                    return@launch
                                                }

                                                logRecognition(
                                                    outcome = RecognitionOutcome.RECOGNIZED,
                                                    imagePath = path,
                                                    bestScore = result.bestScore,
                                                    personId = result.personId
                                                )

                                                withContext(Dispatchers.Main) {
                                                    recognizedName = person!!.name
                                                    recognizedRelation = person.relation
                                                    screen = Screen.RECOGNIZED
                                                }
                                            } catch (e: Exception) {
                                                logRecognition(
                                                    outcome = RecognitionOutcome.ERROR,
                                                    imagePath = path,
                                                    bestScore = null,
                                                    personId = null
                                                )
                                                withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                            }
                                        }
                                    },
                                    onCancel = { screen = Screen.PATIENT_HOME }
                                )

                                Screen.RECOGNIZED -> RecognizedPersonScreen(
                                    name = recognizedName ?: "Unknown",
                                    relation = recognizedRelation,
                                    onDone = {
                                        recognizedName = null
                                        recognizedRelation = null
                                        screen = Screen.PATIENT_HOME
                                    }
                                )

                                Screen.UNKNOWN -> UnknownPersonScreen(
                                    onHelpMeRemember = {
                                        val path = lastCapturedPath
                                        if (path == null) {
                                            scope.launch { snack.showSnackbar("No photo captured.") }
                                            screen = Screen.PATIENT_HOME
                                            return@UnknownPersonScreen
                                        }

                                        scope.launch {
                                            peopleRepo.createPendingFromPhotoPaths(listOf(path))
                                            lastCapturedPath = null
                                            screen = Screen.PATIENT_HOME
                                        }
                                    },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    },
                                    onTimeoutReturnHome = {
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
                                            },
                                            onLogs = { screen = Screen.ADMIN_LOGS }
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
                                                    val approved = peopleRepo.approvePendingWithEmbeddings(
                                                        appContext = this@MainActivity.applicationContext,
                                                        personId = id,
                                                        name = name,
                                                        relation = relation
                                                    )
                                                    if (!approved) {
                                                        snack.showSnackbar("Couldn’t create face vectors. Try clearer photo.")
                                                    }
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

                                Screen.ADMIN_LOGS -> {
                                    if (isAdminExpired()) {
                                        screen = Screen.ADMIN_PIN
                                    } else {
                                        touchAdminSession()
                                        val logsVm: LogsViewModel = viewModel()
                                        val logs by logsVm.logs.collectAsState(initial = emptyList())

                                        AdminLogsScreen(
                                            logs = logs,
                                            onClear = { logsVm.clearAll() },
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