package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
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
import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import com.example.myapplication.data.repo.DeviationEventRepository
import com.example.myapplication.data.repo.LogsRepository
import com.example.myapplication.data.repo.PeopleRepository
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceRecognitionEngine
import com.example.myapplication.receiver.DeviationMonitorService
import com.example.myapplication.ui.admin.AdminDashboardScreen
import com.example.myapplication.ui.admin.AdminLogsScreen
import com.example.myapplication.ui.admin.AdminPeopleScreen
import com.example.myapplication.ui.admin.AdminPinScreen
import com.example.myapplication.ui.admin.AdminSettingsScreen
import com.example.myapplication.ui.admin.LogsViewModel
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.PatientHomeScreen
import com.example.myapplication.ui.patient.PostEventSummaryScreen
import com.example.myapplication.ui.patient.RecognizedPersonScreen
import com.example.myapplication.ui.patient.RememberSavedScreen
import com.example.myapplication.ui.patient.UnknownPersonScreen
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import com.example.myapplication.util.CallCaregiver
import com.example.myapplication.util.CaregiverPrefs
import com.example.myapplication.util.PostEventSummaryBuilder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class Screen {
    PATIENT_HOME,
    CAMERA,
    UNKNOWN,
    REMEMBER_SAVED,
    RECOGNIZED,
    POST_EVENT_SUMMARY,
    ADMIN_PIN,
    ADMIN_DASHBOARD,
    ADMIN_PEOPLE,
    ADMIN_ROUTINE,
    ADMIN_SETTINGS,
    ADMIN_LOGS,
    ADMIN_CAPTURE_MORE_PHOTOS,
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier) {

                    val routineVm: RoutineViewModel = viewModel()
                    val all by routineVm.allRoutines.collectAsState()
                    val currentRoutine by routineVm.currentRoutine.collectAsState()
                    val nextRoutine by routineVm.nextRoutine.collectAsState()

                    val db = remember { AppDb.get(applicationContext) }
                    val peopleRepo = remember { PeopleRepository(db) }
                    val logsRepo = remember { LogsRepository(db.recognitionLogDao()) }
                    val deviationEventRepo = remember { DeviationEventRepository(db.deviationEventDao()) }

                    val recogEngine = remember { FaceRecognitionEngine(applicationContext) }

                    var recognizedName by remember { mutableStateOf<String?>(null) }
                    var recognizedRelation by remember { mutableStateOf<String?>(null) }

                    var postEventSummary by remember { mutableStateOf<String?>(null) }
                    var postEventSummaryIds by remember { mutableStateOf<List<String>>(emptyList()) }

                    var screen by remember { mutableStateOf(Screen.PATIENT_HOME) }

                    var lastCapturedPath by remember { mutableStateOf<String?>(null) }
                    var lastFaceCropPath by remember { mutableStateOf<String?>(null) }

                    val scope = rememberCoroutineScope()
                    val snack = remember { SnackbarHostState() }

                    var adminAuthedAt by remember { mutableStateOf<Long?>(null) }
                    val ADMIN_TIMEOUT_MS = 2 * 60 * 1000L

                    val locationPermissions = rememberMultiplePermissionsState(
                        permissions = listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                    LaunchedEffect(Unit) {
                        locationPermissions.launchMultiplePermissionRequest()
                    }

                    val allLocationGranted = locationPermissions.permissions.any { it.status.isGranted }

                    LaunchedEffect(allLocationGranted) {
                        val intent = Intent(this@MainActivity, DeviationMonitorService::class.java)
                        if (allLocationGranted) {
                            startService(intent)
                        } else {
                            stopService(intent)
                        }
                    }
                    var pendingCapturePersonId by remember { mutableStateOf<String?>(null) }

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
                            scope.launch {
                                deviationEventRepo.insert(
                                    DeviationEventEntity(
                                        eventType = DeviationEventType.CAREGIVER_CALL,
                                        details = "Caregiver call started from app"
                                    )
                                )
                            }
                            CallCaregiver.dial(this@MainActivity, phone)
                        }
                    }

                    fun saveFaceCrop(bitmap: Bitmap): String? {
                        return try {
                            val dir = File(applicationContext.filesDir, "face_crops")
                            if (!dir.exists()) dir.mkdirs()

                            val file = File(dir, "face_${System.currentTimeMillis()}.jpg")

                            FileOutputStream(file).use {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                            }

                            file.absolutePath
                        } catch (_: Exception) {
                            null
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
//                                    threshold = 0.80f,
                                    personId = personId,
                                    imagePath = imagePath
                                )
                            )
                        }
                    }

                    LaunchedEffect(screen) {
                        if (screen == Screen.PATIENT_HOME) {
                            val since = System.currentTimeMillis() - 12 * 60 * 60 * 1000L
                            val events = deviationEventRepo.recentUnresolvedSince(since)
                            if (events.isNotEmpty()) {
                                postEventSummary = PostEventSummaryBuilder.build(events)
                                postEventSummaryIds = events.map { it.eventId }
                                screen = Screen.POST_EVENT_SUMMARY
                            }
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snack) }
                    ) { padding ->

                        Box(modifier = Modifier.padding(padding)) {

                            when (screen) {

                                Screen.PATIENT_HOME -> PatientHomeScreen(
                                    currentRoutine = currentRoutine,
                                    nextRoutine = nextRoutine,
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
                                            val bmp = BitmapFactory.decodeFile(path)
                                            if (bmp == null) {
                                                withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                return@launch
                                            }

                                            val rect = FaceCropper.detectLargestFace(bmp)
                                            val face = rect?.let { FaceCropper.crop(bmp, it) }

                                            lastFaceCropPath = face?.let { saveFaceCrop(it) }

                                            if (face == null) {
                                                withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                return@launch
                                            }

                                            val result = recogEngine.recognize(face)
                                            android.util.Log.d("FaceRecognition", "MainActivity result personId=${result.personId} score=${result.bestScore}")

                                            if (result.personId == null) {
                                                withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
                                                return@launch
                                            }

                                            val person = db.personDao().getById(result.personId)

                                            if (person?.name.isNullOrBlank()) {
                                                withContext(Dispatchers.Main) { screen = Screen.UNKNOWN }
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
                                        val path = lastFaceCropPath ?: lastCapturedPath

                                        if (path == null) {
                                            scope.launch {
                                                snack.showSnackbar("No photo captured.")
                                            }
                                            screen = Screen.PATIENT_HOME
                                            return@UnknownPersonScreen
                                        }

                                        scope.launch {
                                            peopleRepo.createPendingFromPhotoPaths(listOf(path))
                                            deviationEventRepo.insert(
                                                DeviationEventEntity(
                                                    eventType = DeviationEventType.UNKNOWN_PERSON_SAVED,
                                                    details = "Unknown person saved for caregiver review"
                                                )
                                            )
                                            lastCapturedPath = null
                                            lastFaceCropPath = null
                                            screen = Screen.REMEMBER_SAVED
                                        }
                                    },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    }
                                )

                                Screen.REMEMBER_SAVED -> RememberSavedScreen(
                                    onBackHome = { screen = Screen.PATIENT_HOME },
                                    onCallCaregiver = {
                                        callCaregiver()
                                        screen = Screen.PATIENT_HOME
                                    }
                                )

                                Screen.POST_EVENT_SUMMARY -> PostEventSummaryScreen(
                                    summaryText = postEventSummary ?: "Everything went as planned.",
                                    onDone = {
                                        scope.launch {
                                            if (postEventSummaryIds.isNotEmpty()) {
                                                deviationEventRepo.markResolved(postEventSummaryIds)
                                            }
                                            postEventSummary = null
                                            postEventSummaryIds = emptyList()
                                            screen = Screen.PATIENT_HOME
                                        }
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
                                    val pending by peopleRepo.pending()
                                        .collectAsState(initial = emptyList())

                                    val photoPathByPersonId by produceState<Map<String, String>>(
                                        initialValue = emptyMap(),
                                        key1 = pending
                                    ) {
                                        value = pending.associate { person ->
                                            val path = db.galleryDao()
                                                .listForPerson(person.personId)
                                                .firstOrNull()
                                                ?.imagePath
                                                .orEmpty()
                                            person.personId to path
                                        }
                                    }

                                    val photoCountByPersonId by produceState<Map<String, Int>>(
                                        initialValue = emptyMap(),
                                        key1 = pending
                                    ) {
                                        value = pending.associate { person ->
                                            person.personId to db.galleryDao().listForPerson(person.personId).size
                                        }
                                    }

                                    AdminPeopleScreen(
                                        pending = pending,
                                        photoPathByPersonId = photoPathByPersonId,
                                        photoCountByPersonId = photoCountByPersonId,
                                        onCaptureMorePhotos = { personId ->
                                            pendingCapturePersonId = personId
                                            screen = Screen.ADMIN_CAPTURE_MORE_PHOTOS
                                        },
                                        onApprove = { id, name, relation ->
                                            scope.launch {
                                                val approved = peopleRepo.approvePendingWithEmbeddings(
                                                    appContext = applicationContext,
                                                    personId = id,
                                                    name = name,
                                                    relation = relation
                                                )

                                                if (!approved) {
                                                    snack.showSnackbar("Couldn’t create face vectors. Try clearer photos.")
                                                }
                                            }
                                        },
                                        onBack = { screen = Screen.ADMIN_DASHBOARD }
                                    )
                                }

                                Screen.ADMIN_CAPTURE_MORE_PHOTOS -> CameraScreen(
                                    onImageCaptured = { path ->
                                        val targetId = pendingCapturePersonId
                                        if (targetId == null) {
                                            screen = Screen.ADMIN_PEOPLE
                                            return@CameraScreen
                                        }

                                        scope.launch {
                                            peopleRepo.addPhotosToPending(targetId, listOf(path))
                                            snack.showSnackbar("Photo added.")
                                            screen = Screen.ADMIN_PEOPLE
                                        }
                                    },
                                    onCancel = { screen = Screen.ADMIN_PEOPLE }
                                )

                                Screen.ADMIN_ROUTINE -> {
                                    AdminRoutineScreen(
                                        allItems = all,
                                        onBack = { screen = Screen.ADMIN_DASHBOARD },
                                        onAdd = { label, time, rule, date, endTimeMinutes, expectedLocationLabel, expectedLatitude, expectedLongitude, allowedRadiusMeters ->
                                            routineVm.addQuick(
                                                label = label,
                                                timeMinutes = time,
                                                repeatRule = rule,
                                                date = date,
                                                endTimeMinutes = endTimeMinutes,
                                                expectedLocationLabel = expectedLocationLabel,
                                                expectedLatitude = expectedLatitude,
                                                expectedLongitude = expectedLongitude,
                                                allowedRadiusMeters = allowedRadiusMeters
                                            )
                                        },
                                        onToggle = { item, enabled ->
                                            routineVm.toggleEnabled(item, enabled)
                                        },
                                        onDelete = { item ->
                                            routineVm.delete(item)
                                        }
                                    )
                                }

                                Screen.ADMIN_SETTINGS -> {
                                    AdminSettingsScreen(
                                        onBack = { screen = Screen.ADMIN_DASHBOARD }
                                    )
                                }

                                Screen.ADMIN_LOGS -> {
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