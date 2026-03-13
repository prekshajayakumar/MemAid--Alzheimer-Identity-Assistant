package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.db.AppDb
import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType
import com.example.myapplication.data.entities.GalleryEntity
import com.example.myapplication.data.entities.PersonEntity
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import com.example.myapplication.data.entities.RoutineItemEntity
import com.example.myapplication.data.repo.DeviationEventRepository
import com.example.myapplication.data.repo.LogsRepository
import com.example.myapplication.data.repo.PeopleRepository
import com.example.myapplication.ml.FaceCropper
import com.example.myapplication.ml.FaceRecognitionEngine
import com.example.myapplication.receiver.DeviationMonitorService
import com.example.myapplication.ui.admin.AdminDashboardScreen
import com.example.myapplication.ui.admin.AdminLibraryScreen
import com.example.myapplication.ui.admin.AdminLogsScreen
import com.example.myapplication.ui.admin.AdminPeopleScreen
import com.example.myapplication.ui.admin.AdminPersonDetailScreen
import com.example.myapplication.ui.admin.AdminPinScreen
import com.example.myapplication.ui.admin.AdminSettingsScreen
import com.example.myapplication.ui.admin.LogsViewModel
import com.example.myapplication.ui.assist.CameraScreen
import com.example.myapplication.ui.patient.PatientHomeScreen
import com.example.myapplication.ui.patient.PostEventSummaryScreen
import com.example.myapplication.ui.patient.RecognizedPersonScreen
import com.example.myapplication.ui.patient.RememberSavedScreen
import com.example.myapplication.ui.patient.UnknownPersonScreen
import com.example.myapplication.ui.routine.AdminRoutineDetailScreen
import com.example.myapplication.ui.routine.AdminRoutineScreen
import com.example.myapplication.ui.routine.RoutineViewModel
import com.example.myapplication.util.CallCaregiver
import com.example.myapplication.util.CaregiverPrefs
import com.example.myapplication.util.ImageBitmapUtils
import com.example.myapplication.util.PostEventSummaryBuilder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
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
    ADMIN_LIBRARY,
    ADMIN_PERSON_DETAIL,
    ADMIN_ROUTINE,
    ADMIN_ROUTINE_DETAIL,
    ADMIN_SETTINGS,
    ADMIN_LOGS,
    ADMIN_CAPTURE_MORE_PHOTOS,
}

private data class BurstShotResult(
    val sourcePath: String,
    val cropPath: String?,
    val personId: String?,
    val score: Float
)

private data class BestBurstMatch(
    val personId: String,
    val bestScore: Float,
    val strongCropPaths: List<String>
)

private fun chooseBestBurstMatch(results: List<BurstShotResult>): BestBurstMatch? {
    val recognized = results.filter { it.personId != null }
    if (recognized.isEmpty()) return null

    val best = recognized.maxByOrNull { it.score } ?: return null
    val bestPersonId = best.personId ?: return null

    val strongCrops = recognized
        .filter { it.personId == bestPersonId && it.cropPath != null && it.score >= 0.80f }
        .sortedByDescending { it.score }
        .mapNotNull { it.cropPath }
        .distinct()
        .take(2)

    return BestBurstMatch(
        personId = bestPersonId,
        bestScore = best.score,
        strongCropPaths = strongCrops
    )
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
                    val smsPermission = rememberPermissionState(Manifest.permission.SEND_SMS)

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

                    var selectedLibraryPersonId by remember { mutableStateOf<String?>(null) }
                    var selectedRoutineId by remember { mutableStateOf<String?>(null) }

                    val scope = rememberCoroutineScope()
                    val snack = remember { SnackbarHostState() }

                    var adminAuthedAt by remember { mutableStateOf<Long?>(null) }
                    val ADMIN_TIMEOUT_MS = 2 * 60 * 1000L

                    var captureTargetPersonId by remember { mutableStateOf<String?>(null) }
                    var captureIntoActiveLibrary by remember { mutableStateOf(false) }

                    val locationPermissions = rememberMultiplePermissionsState(
                        permissions = listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                    LaunchedEffect(Unit) {
                        locationPermissions.launchMultiplePermissionRequest()

                        if (!smsPermission.status.isGranted) {
                            smsPermission.launchPermissionRequest()
                        }
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
                                    burstCount = 3,
                                    onImagesCaptured = { paths ->
                                        lastCapturedPath = paths.firstOrNull()

                                        scope.launch(Dispatchers.Default) {
                                            val shotResults = mutableListOf<BurstShotResult>()

                                            for (path in paths) {
                                                val bmp = ImageBitmapUtils.decodeUprightBitmap(path) ?: continue

                                                val previewRect = FaceCropper.detectLargestFace(bmp)
                                                val previewFace = previewRect?.let { FaceCropper.cropSquare(bmp, it) }
                                                val cropPath = previewFace?.let { saveFaceCrop(it) }

                                                val result = recogEngine.recognizeFromPhoto(bmp)

                                                shotResults += BurstShotResult(
                                                    sourcePath = path,
                                                    cropPath = cropPath,
                                                    personId = result.personId,
                                                    score = result.bestScore
                                                )
                                            }

                                            val bestCropOverall = shotResults
                                                .filter { it.cropPath != null }
                                                .maxByOrNull { it.score }
                                                ?.cropPath

                                            lastFaceCropPath = bestCropOverall

                                            val winner = chooseBestBurstMatch(shotResults)

                                            if (winner == null) {
                                                logRecognition(
                                                    outcome = RecognitionOutcome.UNKNOWN,
                                                    imagePath = lastFaceCropPath ?: lastCapturedPath,
                                                    bestScore = shotResults.maxOfOrNull { it.score },
                                                    personId = null
                                                )
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            val person = db.personDao().getById(winner.personId)
                                            if (person == null || person.name.isNullOrBlank()) {
                                                logRecognition(
                                                    outcome = RecognitionOutcome.UNKNOWN,
                                                    imagePath = lastFaceCropPath ?: lastCapturedPath,
                                                    bestScore = winner.bestScore,
                                                    personId = null
                                                )
                                                withContext(Dispatchers.Main) {
                                                    screen = Screen.UNKNOWN
                                                }
                                                return@launch
                                            }

                                            if (winner.strongCropPaths.isNotEmpty()) {
                                                peopleRepo.appendConfirmedFaceCrops(
                                                    appContext = applicationContext,
                                                    personId = winner.personId,
                                                    cropPaths = winner.strongCropPaths
                                                )
                                            }

                                            logRecognition(
                                                outcome = RecognitionOutcome.RECOGNIZED,
                                                imagePath = winner.strongCropPaths.firstOrNull() ?: lastFaceCropPath ?: lastCapturedPath,
                                                bestScore = winner.bestScore,
                                                personId = winner.personId
                                            )

                                            val safeName = person.name ?: "Unknown"
                                            val safeRelation = person.relation

                                            withContext(Dispatchers.Main) {
                                                recognizedName = safeName
                                                recognizedRelation = safeRelation
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
                                            logRecognition(
                                                outcome = RecognitionOutcome.UNKNOWN,
                                                imagePath = path,
                                                bestScore = null,
                                                personId = null
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
                                            onLibrary = { screen = Screen.ADMIN_LIBRARY },
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
                                    val pending by peopleRepo.pending().collectAsState(initial = emptyList())
                                    val activePeople by peopleRepo.active().collectAsState(initial = emptyList())

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
                                        activePeople = activePeople,
                                        photoPathByPersonId = photoPathByPersonId,
                                        photoCountByPersonId = photoCountByPersonId,
                                        onCaptureMorePhotos = { personId ->
                                            captureTargetPersonId = personId
                                            captureIntoActiveLibrary = false
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

                                                if (approved) {
                                                    snack.showSnackbar("Person approved.")
                                                } else {
                                                    snack.showSnackbar("Couldn’t create face vectors. Try clearer photos.")
                                                }
                                            }
                                        },
                                        onMergeIntoExisting = { pendingId, existingId ->
                                            scope.launch {
                                                val merged = peopleRepo.mergePendingIntoExisting(
                                                    appContext = applicationContext,
                                                    pendingPersonId = pendingId,
                                                    existingPersonId = existingId
                                                )
                                                if (merged) {
                                                    snack.showSnackbar("Pending person added to existing person.")
                                                } else {
                                                    snack.showSnackbar("Could not link to existing person.")
                                                }
                                            }
                                        },
                                        onReject = { personId ->
                                            scope.launch {
                                                val deleted = peopleRepo.deletePendingPerson(personId)
                                                if (deleted) {
                                                    snack.showSnackbar("Pending person removed.")
                                                } else {
                                                    snack.showSnackbar("Could not remove person.")
                                                }
                                            }
                                        },
                                        onBack = { screen = Screen.ADMIN_DASHBOARD }
                                    )
                                }

                                Screen.ADMIN_LIBRARY -> {
                                    val activePeople by peopleRepo.active()
                                        .collectAsState(initial = emptyList())

                                    val photoCountByPersonId by produceState<Map<String, Int>>(
                                        initialValue = emptyMap(),
                                        key1 = activePeople
                                    ) {
                                        value = activePeople.associate { person ->
                                            person.personId to db.galleryDao().listForPerson(person.personId).size
                                        }
                                    }

                                    val vectorCountByPersonId by produceState<Map<String, Int>>(
                                        initialValue = emptyMap(),
                                        key1 = activePeople
                                    ) {
                                        value = activePeople.associate { person ->
                                            person.personId to db.vectorDao().vectorsForPerson(person.personId).size
                                        }
                                    }

                                    AdminLibraryScreen(
                                        people = activePeople,
                                        photoCountByPersonId = photoCountByPersonId,
                                        vectorCountByPersonId = vectorCountByPersonId,
                                        onOpenPerson = { personId ->
                                            selectedLibraryPersonId = personId
                                            screen = Screen.ADMIN_PERSON_DETAIL
                                        },
                                        onAddMorePhotos = { personId ->
                                            captureTargetPersonId = personId
                                            captureIntoActiveLibrary = true
                                            screen = Screen.ADMIN_CAPTURE_MORE_PHOTOS
                                        },
                                        onBack = { screen = Screen.ADMIN_DASHBOARD }
                                    )
                                }

                                Screen.ADMIN_PERSON_DETAIL -> {
                                    val personId = selectedLibraryPersonId

                                    if (personId == null) {
                                        screen = Screen.ADMIN_LIBRARY
                                    } else {
                                        var person by remember(personId) { mutableStateOf<PersonEntity?>(null) }
                                        var gallery by remember(personId) { mutableStateOf<List<GalleryEntity>>(emptyList()) }
                                        var vectorCount by remember(personId) { mutableStateOf(0) }

                                        LaunchedEffect(personId) {
                                            person = peopleRepo.getPersonById(personId)
                                            gallery = peopleRepo.getGalleryForPerson(personId)
                                            vectorCount = peopleRepo.getVectorCount(personId)
                                        }

                                        val currentPerson = person
                                        if (currentPerson == null) {
                                            Box(Modifier.fillMaxSize()) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            AdminPersonDetailScreen(
                                                person = currentPerson,
                                                gallery = gallery,
                                                vectorCount = vectorCount,
                                                onSaveBasics = { name, relation ->
                                                    scope.launch {
                                                        val ok = peopleRepo.updatePersonBasics(
                                                            personId = currentPerson.personId,
                                                            name = name,
                                                            relation = relation
                                                        )
                                                        if (ok) {
                                                            snack.showSnackbar("Saved.")
                                                            person = peopleRepo.getPersonById(currentPerson.personId)
                                                        } else {
                                                            snack.showSnackbar("Could not save.")
                                                        }
                                                    }
                                                },
                                                onAddMorePhotos = {
                                                    captureTargetPersonId = currentPerson.personId
                                                    captureIntoActiveLibrary = true
                                                    screen = Screen.ADMIN_CAPTURE_MORE_PHOTOS
                                                },
                                                onBack = { screen = Screen.ADMIN_LIBRARY }
                                            )
                                        }
                                    }
                                }

                                Screen.ADMIN_CAPTURE_MORE_PHOTOS -> CameraScreen(
                                    burstCount = 3,
                                    onImagesCaptured = { paths ->
                                        val targetId = captureTargetPersonId
                                        if (targetId == null) {
                                            screen = Screen.ADMIN_DASHBOARD
                                            return@CameraScreen
                                        }

                                        scope.launch {
                                            if (captureIntoActiveLibrary) {
                                                val added = peopleRepo.addPhotosToExistingPersonAndEmbed(
                                                    appContext = applicationContext,
                                                    personId = targetId,
                                                    imagePaths = paths
                                                )
                                                snack.showSnackbar("Added $added usable sample(s).")
                                                screen = if (selectedLibraryPersonId == targetId) {
                                                    Screen.ADMIN_PERSON_DETAIL
                                                } else {
                                                    Screen.ADMIN_LIBRARY
                                                }
                                            } else {
                                                peopleRepo.addPhotosToPending(targetId, paths)
                                                snack.showSnackbar("Photo(s) added.")
                                                screen = Screen.ADMIN_PEOPLE
                                            }
                                        }
                                    },
                                    onCancel = {
                                        screen = when {
                                            captureIntoActiveLibrary && selectedLibraryPersonId == captureTargetPersonId ->
                                                Screen.ADMIN_PERSON_DETAIL
                                            captureIntoActiveLibrary -> Screen.ADMIN_LIBRARY
                                            else -> Screen.ADMIN_PEOPLE
                                        }
                                    }
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
                                        },
                                        onOpenItem = { item ->
                                            selectedRoutineId = item.routineId
                                            screen = Screen.ADMIN_ROUTINE_DETAIL
                                        }
                                    )
                                }

                                Screen.ADMIN_ROUTINE_DETAIL -> {
                                    val routineId = selectedRoutineId

                                    if (routineId == null) {
                                        screen = Screen.ADMIN_ROUTINE
                                    } else {
                                        var item by remember(routineId) { mutableStateOf<RoutineItemEntity?>(null) }

                                        LaunchedEffect(routineId, all) {
                                            item = routineVm.getRoutineById(routineId)
                                        }

                                        val currentItem = item
                                        if (currentItem == null) {
                                            Box(Modifier.fillMaxSize()) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            AdminRoutineDetailScreen(
                                                item = currentItem,
                                                onSave = { updated ->
                                                    routineVm.updateRoutine(updated)
                                                    scope.launch {
                                                        snack.showSnackbar("Routine updated.")
                                                    }
                                                    screen = Screen.ADMIN_ROUTINE
                                                },
                                                onDelete = { deleting ->
                                                    routineVm.delete(deleting)
                                                    scope.launch {
                                                        snack.showSnackbar("Routine deleted.")
                                                    }
                                                    screen = Screen.ADMIN_ROUTINE
                                                },
                                                onBack = { screen = Screen.ADMIN_ROUTINE }
                                            )
                                        }
                                    }
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