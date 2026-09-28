package `in`.miaolibrary.cataloging.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CameraCaptureScreen(state:PhotoCaptureState,onCaptured:(RequiredPhoto,String)->Unit,onComplete:()->Unit){
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
 var granted by remember{mutableStateOf(false)};var capture by remember{mutableStateOf<ImageCapture?>(null)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
 LaunchedEffect(Unit){permission.launch(Manifest.permission.CAMERA)}
 if(!granted){Text("Camera permission is required.",Modifier.padding(24.dp));return}
 Box(Modifier.fillMaxSize()){
  AndroidView({ctx->PreviewView(ctx).also{view->
   val future=ProcessCameraProvider.getInstance(ctx)
   future.addListener({
    val p=future.get();val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)}
    val ic=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();capture=ic
    p.unbindAll();p.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,ic)
   },ctx.mainExecutor)
  }},Modifier.fillMaxSize())
  Column(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter)){
   state.next()?.let{Text("Capture: "+it.label)}
   Button(enabled=state.next()!=null,onClick={
    val type = state.next()
                    if (type != null) {
                        val dir = File(context.filesDir, "cataloging")
                        dir.mkdirs()
                        val file = File(dir, type.name + "_" + System.currentTimeMillis() + ".jpg")
                        capture?.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            context.mainExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(e: ImageCaptureException) = Unit
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                    onCaptured(type, file.absolutePath)
                                }
                            }
                        )
                    }
   },Modifier.fillMaxWidth()){Text("CAPTURE")}
   Button(enabled=state.isComplete(),onClick=onComplete,Modifier.fillMaxWidth()){Text("USE PHOTOS")}
  }
 }
}
