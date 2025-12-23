// app/src/main/java/com/example/myapplication/ui/assist/CameraScreen.kt
package com.example.myapplication.ui.assist

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    // Camera permission
    val camPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!camPermission.status.isGranted) camPermission.launchPermissionRequest()
    }

    if (!camPermission.status.isGranted) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission needed to show preview.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { camPermission.launchPermissionRequest() }) { Text("Grant permission") }
        }
        return
    }

    // Controller: preview + still capture (use + instead of `or` to avoid any BigInteger import hijack)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(androidx.camera.view.CameraController.IMAGE_CAPTURE)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
    }

    Column(Modifier.fillMaxSize()) {
        // Preview view
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                val pv = PreviewView(ctx)
                // Attach the CameraX controller to this PreviewView
                // Use whichever line compiles on your CameraX version:
                //pv.setController(controller)      // <-- try this first
                pv.controller = controller     // <-- if the line above is unresolved, use this one
                pv
            }
        )


        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                scope.launch {
                    captureToAppPictures(
                        context = context,
                        controller = controller,
                        onSaved = { uri ->
                            scope.launch { snack.showSnackbar("Saved: $uri") }
                        },
                        onError = { e ->
                            scope.launch { snack.showSnackbar("Capture failed: ${e.message}") }
                        }
                    )
                }
            }) {
                Text("Capture")
            }
        }

        SnackbarHost(hostState = snack)
    }
}

private fun captureToAppPictures(
    context: Context,
    controller: LifecycleCameraController,
    onSaved: (Uri?) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // App-specific external dir: no storage permission required
    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (picturesDir == null) {
        onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "No pictures dir", null))
        return
    }

    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val file = File(picturesDir, "memaid_$name.jpg")
    val output = ImageCapture.OutputFileOptions.Builder(file).build()

    controller.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(Uri.fromFile(file))
            }
            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}
