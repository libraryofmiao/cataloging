package `in`.miaolibrary.cataloging.capture

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class OcrPage(val path:String,val text:String)

class FreeOcrEngine {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun read(paths:List<String>):List<OcrPage> = coroutineScope {
        paths.map { path ->
            async {
                val bitmap = BitmapFactory.decodeFile(path) ?: return@async OcrPage(path,"")
                val image = InputImage.fromBitmap(bitmap,0)
                try {
                    val latinText = runCatching { latin.process(image).await().text }.getOrDefault("")
                    val devanagariText = if (latinText.any { it in '\u0900'..'\u097F' }) {
                        runCatching { devanagari.process(image).await().text }.getOrDefault("")
                    } else ""
                    OcrPage(path,merge(latinText,devanagariText))
                } finally {
                    bitmap.recycle()
                }
            }
        }.awaitAll()
    }

    private fun merge(a:String,b:String):String {
        if (b.isBlank()) return a
        if (a.isBlank()) return b
        return if (b.length > a.length * 0.18) "$a\n$b" else a
    }
}
