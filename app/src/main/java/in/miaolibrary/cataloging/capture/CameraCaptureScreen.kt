package in.miaolibrary.cataloging.capture

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CameraCaptureScreen(state:PhotoCaptureState,onCaptured:(RequiredPhoto,String)->Unit,onComplete:()->Unit){
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
 var granted by remember{mutableStateOf(false)};var imageCapture by remember{mutableStateOf<ImageCapture?>(null)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
 LaunchedEffect(Unit){launcher.launch(Manifest.permission.CAMERA)}
 if(!granted){Text("Camera permission is required.",Modifier.padding(24.dp));return}
 Box(Modifier.fillMaxSize()){
  AndroidView(factory={ctx->PreviewView(ctx).also{view->
   val future=ProcessCameraProvider.getInstance(ctx)
   future.addListener({
    val p=future.get()
    val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)}
    val capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    imageCapture=capture;p.unbindAll();p.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture)
   },ctx.mainExecutor)
  }},Modifier.fillMaxSize())
  Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   state.next()?.let{Text("Capture: "+it.label,style=MaterialTheme.typography.titleMedium)}
   Button(enabled=state.next()!=null,onClick={
    val type=state.next()?:return@Button
    val dir=File(context.cacheDir,"cataloging");dir.mkdirs();val file=File(dir,type.name+"_"+System.currentTimeMillis()+".jpg")
    imageCapture?.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),context.mainExecutor,object:ImageCapture.OnImageSavedCallback{
     override fun onError(e:ImageCaptureException){}
     override fun onImageSaved(r:ImageCapture.OutputFileResults){onCaptured(type,file.absolutePath)}
    })
   },Modifier.fillMaxWidth()){Text("CAPTURE PHOTO")}
   Button(enabled=state.isComplete(),onClick=onComplete,Modifier.fillMaxWidth()){Text("USE PHOTOS")}
  }
 }
}
