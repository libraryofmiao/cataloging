package `in`.miaolibrary.cataloging.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CameraCaptureScreen(
    state: PhotoCaptureState,
    onCaptured: (RequiredPhoto, String) -> Unit,
    onOptionalCaptured: (String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var detectionMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val detector = remember { SmartPhotoClassifier() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> cameraPermissionGranted = isGranted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!cameraPermissionGranted) {
        Text(
            text = "Camera permission is required.",
            modifier = Modifier.padding(24.dp)
        )
        return
    }

    fun capturePhoto(photo: RequiredPhoto) {
        val directory = File(context.filesDir, "cataloging")
        directory.mkdirs()
        val file = File(
            directory,
            photo.name + "_" + System.currentTimeMillis() + ".jpg"
        )
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        val camera = imageCapture ?: return

        busy = true
        camera.takePicture(
            output,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) { busy = false; detectionMessage = "Could not save the photograph. Please try again." }

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    busy = false
                    scope.launch {
                        val detection = runCatching { detector.classify(file.absolutePath) }.getOrNull()
                        if (detection == null) {
                            onCaptured(photo, file.absolutePath)
                            detectionMessage = "Photo captured. Page type could not be verified."
                            return@launch
                        }
                        val expected = when (photo) {
                            RequiredPhoto.FRONT_COVER -> DetectedPhotoType.FRONT_COVER
                            RequiredPhoto.TITLE_PAGE -> DetectedPhotoType.TITLE_PAGE
                            RequiredPhoto.PUBLICATION_PAGE -> DetectedPhotoType.PUBLICATION_PAGE
                            RequiredPhoto.ISBN_PAGE -> DetectedPhotoType.ISBN_PAGE
                        }
                        val confidentWrong = detection.confidence >= 0.80f &&
                            detection.type != DetectedPhotoType.UNKNOWN && detection.type != expected
                        if (confidentWrong) {
                            file.delete()
                            detectionMessage = "Detected " + detection.type.label + ". Retake the " + photo.label + "."
                        } else {
                            onCaptured(photo, file.absolutePath)
                            detectionMessage = "Captured " + photo.label
                        }
                    }
                }
            }
        )
    }
    fun captureOptional(label: String) {
        val directory = File(context.filesDir, "cataloging")
        directory.mkdirs()
        val file = File(directory, "OPTIONAL_" + System.currentTimeMillis() + ".jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        val camera = imageCapture ?: return
        busy = true
        camera.takePicture(output, context.mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                busy = false
                detectionMessage = "Could not save the optional photograph. Please try again."
            }
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                busy = false
                onOptionalCaptured(file.absolutePath)
                detectionMessage = "Added optional page: " + label
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener(
                        {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val captureUseCase = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            imageCapture = captureUseCase

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                captureUseCase
                            )
                        },
                        ctx.mainExecutor
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            val nextPhoto = state.next()

            RequiredPhoto.entries.forEach { required ->
                val captured = state.photos[required]?.let { File(it).exists() } == true
                Text((if (captured) "✓ " else "○ ") + required.label)
            }
            if (nextPhoto != null) {
                Text("Next: " + nextPhoto.label)
            } else {
                Text("All required photographs captured.")
            }
            detectionMessage?.let { Text(it) }

            Button(
                onClick = {
                    if (nextPhoto != null) {
                        capturePhoto(nextPhoto)
                    }
                },
                enabled = nextPhoto != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "SAVING…" else "CAPTURE")
            }

            if (state.isComplete()) {
                Text("Optional photographs", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Add contents, preface/introduction, or any other useful bibliographic page. These are sent to Gemini as additional evidence.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ captureOptional("Contents") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("CONTENTS") }
                    Button({ captureOptional("Preface") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("PREFACE") }
                }
                Button({ captureOptional("Other page") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("OTHER PAGE") }
                Text("Optional pages captured: " + state.optionalPhotos.size)
            }

            Button(
                onClick = onComplete,
                enabled = state.isComplete(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("USE PHOTOS")
            }
        }
    }
}
