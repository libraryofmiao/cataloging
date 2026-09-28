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
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
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

        camera.takePicture(
            output,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) = Unit

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    onCaptured(photo, file.absolutePath)
                }
            }
        )
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
        ) {
            val nextPhoto = state.next()

            if (nextPhoto != null) {
                Text("Capture: " + nextPhoto.label)
            }

            Button(
                onClick = {
                    if (nextPhoto != null) {
                        capturePhoto(nextPhoto)
                    }
                },
                enabled = nextPhoto != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CAPTURE")
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
