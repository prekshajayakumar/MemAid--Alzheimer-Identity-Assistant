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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val camPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!camPermission.status.isGranted) {
            camPermission.launchPermissionRequest()
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

    var isProcessing by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
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
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    PreviewView(ctx).apply { this.controller = controller }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isProcessing) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Scanning…")
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    enabled = !isProcessing,
                    onClick = {
                        isProcessing = true
                        captureToAppPictures(
                            context = context,
                            controller = controller,
                            onSaved = { uri ->
                                isProcessing = false
                                val path = uri?.path
                                if (path.isNullOrBlank()) {
                                    scope.launch {
                                        snack.showSnackbar("Couldn’t save photo.")
                                    }
                                } else {
                                    onImageCaptured(path)
                                }
                            },
                            onError = { e ->
                                isProcessing = false
                                scope.launch {
                                    snack.showSnackbar("Capture failed: ${e.message ?: "unknown"}")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isProcessing) "Please wait…" else "Capture")
                }
            }
        }
    }
}

private fun captureToAppPictures(
    context: Context,
    controller: LifecycleCameraController,
    onSaved: (Uri?) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: run {
        onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "No dir", null))
        return
    }

    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val file = File(picturesDir, "memaid_$name.jpg")

    val output = ImageCapture.OutputFileOptions.Builder(file).build()

    controller.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                onSaved(Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}