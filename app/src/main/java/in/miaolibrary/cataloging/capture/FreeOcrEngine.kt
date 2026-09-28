package in.miaolibrary.cataloging.capture

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrPage(val path:String,val text:String)

class FreeOcrEngine {
 private val latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
 private val devanagari=TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

 suspend fun read(paths:List<String>):List<OcrPage>{
  return paths.map{path->
   val bitmap=BitmapFactory.decodeFile(path) ?: return@map OcrPage(path,"")
   val image=InputImage.fromBitmap(bitmap,0)
   val a=runCatching{latin.process(image).await().text}.getOrDefault("")
   val b=runCatching{devanagari.process(image).await().text}.getOrDefault("")
   bitmap.recycle()
   OcrPage(path,merge(a,b))
  }
 }
 private fun merge(a:String,b:String):String{
  if(b.isBlank()) return a
  if(a.isBlank()) return b
  return if(b.length>a.length*0.25) a+"\n"+b else a
 }
}
