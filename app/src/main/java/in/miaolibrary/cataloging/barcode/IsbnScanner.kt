package in.miaolibrary.cataloging.barcode

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@SuppressLint("UnsafeOptInUsageError")
class IsbnScanner {
 fun bind(view:PreviewView,lifecycle:androidx.lifecycle.Lifecycle,onIsbn:(String)->Unit){
  val future=ProcessCameraProvider.getInstance(view.context)
  future.addListener({
   val provider=future.get()
   val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)}
   val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
   val scanner=BarcodeScanning.getClient()
   analysis.setAnalyzer(view.context.mainExecutor){proxy->
    val media=proxy.image
    if(media==null){proxy.close();return@setAnalyzer}
    scanner.process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
     .addOnSuccessListener{codes->
      codes.mapNotNull{it.rawValue}.firstOrNull{v->
       val s=v.replace("-","").replace(" ","")
       s.matches(Regex("(97[89])?\\d{9}[\\dXx]"))
      }?.let(onIsbn)
     }.addOnCompleteListener{proxy.close()}
   }
   provider.unbindAll();provider.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
  },view.context.mainExecutor)
 }
}
