package in.miaolibrary.cataloging.camera

import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class CameraCaptureActivity: ComponentActivity(){
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  if(!granted) finish()
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
  setContent{CameraCaptureScreen{uri->setResult(RESULT_OK,android.content.Intent().setData(uri));finish()}}
 }
}

@Composable
private fun CameraCaptureScreen(onCaptured:(Uri)->Unit){
 val context=LocalContext.current
 val preview=remember{PreviewView(context)}
 var capture by remember{mutableStateOf<ImageCapture?>(null)}
 LaunchedEffect(Unit){
  val future=ProcessCameraProvider.getInstance(context)
  future.addListener({
   val provider=future.get()
   val p=androidx.camera.core.Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
   val ic=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
   capture=ic
   provider.unbindAll()
   provider.bindToLifecycle(context as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,p,ic)
  },ContextCompat.getMainExecutor(context))
 }
 Box(Modifier.fillMaxSize()){
  androidx.compose.ui.viewinterop.AndroidView({preview},Modifier.fillMaxSize())
  Button(onClick={
   val ic=capture?:return@Button
   val name="SDLM_"+SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())+".jpg"
   val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/SDLM Cataloging")}
   val options=ImageCapture.OutputFileOptions.Builder(context.contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build()
   ic.takePicture(options,ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback{
    override fun onError(e:ImageCaptureException){ }
    override fun onImageSaved(r:ImageCapture.OutputFileResults){r.savedUri?.let(onCaptured)}
   })
  },Modifier.fillMaxWidth().padding(24.dp).align(Alignment.BottomCenter)){Text("Capture photo")}
 }
}
