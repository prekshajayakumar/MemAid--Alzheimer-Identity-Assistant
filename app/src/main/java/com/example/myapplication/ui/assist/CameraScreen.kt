package com.example.myapplication.ui.assist

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onImagesCaptured: (List<String>) -> Unit,
    onCancel: () -> Unit,
    burstCount: Int = 3
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val camPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    var isProcessing by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }

    LaunchedEffect(Unit) {
        if (!camPermission.status.isGranted) {
            camPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(camPermission.status.isGranted, lifecycleOwner) {
        if (camPermission.status.isGranted) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
                delay(500)
                cameraReady = true
            } catch (e: Exception) {
                cameraReady = false
                snack.showSnackbar("Camera setup failed: ${e.message ?: "unknown"}")
            }
        } else {
            cameraReady = false
        }
    }

    suspend fun doBurstCapture() {
        if (isProcessing || !cameraReady) return

        isProcessing = true
        try {
            val paths = captureBurstToAppPictures(
                context = context,
                controller = controller,
                burstCount = burstCount
            )

            if (paths.isEmpty()) {
                snack.showSnackbar("Couldn’t capture usable photos.")
            } else {
                onImagesCaptured(paths)
            }
        } catch (e: Exception) {
            snack.showSnackbar("Capture failed: ${e.message ?: "unknown"}")
        } finally {
            isProcessing = false
        }
    }

    if (!camPermission.status.isGranted) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(title = { Text("Recognize Person") })
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is needed to recognize a person.")
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { camPermission.launchPermissionRequest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant permission")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back home")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recognize Person") },
                navigationIcon = {
                    TextButton(
                        onClick = { if (!isProcessing) onCancel() }
                    ) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isProcessing -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Capturing 3 shots…")
                        Spacer(Modifier.height(8.dp))
                    }

                    !cameraReady -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Preparing camera…")
                        Spacer(Modifier.height(8.dp))
                    }

                    else -> {
                        Text("Tap capture once. The app will take multiple shots automatically.")
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Button(
                    enabled = !isProcessing && cameraReady,
                    onClick = {
                        scope.launch { doBurstCapture() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isProcessing -> "Please wait…"
                            !cameraReady -> "Preparing camera…"
                            else -> "Capture"
                        }
                    )
                }
            }
        }
    }
}

private suspend fun captureBurstToAppPictures(
    context: Context,
    controller: LifecycleCameraController,
    burstCount: Int
): List<String> {
    val saved = mutableListOf<String>()

    repeat(burstCount) {
        val path = captureSingleToAppPictures(context, controller)
        if (path != null) saved += path
        delay(220)
    }

    return saved
}

private suspend fun captureSingleToAppPictures(
    context: Context,
    controller: LifecycleCameraController
): String? = suspendCancellableCoroutine { cont ->

    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (picturesDir == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        .format(System.currentTimeMillis())
    val file = File(picturesDir, "memaid_$name.jpg")

    val output = ImageCapture.OutputFileOptions.Builder(file).build()

    try {
        controller.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    cont.resume(Uri.fromFile(file).path)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resume(null)
                }
            }
        )
    } catch (_: Exception) {
        cont.resume(null)
    }
}