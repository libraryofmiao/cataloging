package `in`.miaolibrary.cataloging.capture

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class OcrPage(val path: String, val text: String)

class FreeOcrEngine {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    suspend fun read(paths: List<String>): List<OcrPage> = coroutineScope {
        paths.map { path -> async {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@async OcrPage(path, "")
            val image = InputImage.fromBitmap(bitmap, 0)
            try {
                val a = runCatching { latin.process(image).await().text }.getOrDefault("")
                val b = runCatching { devanagari.process(image).await().text }.getOrDefault("")
                OcrPage(path, if (b.isBlank()) a else if (a.isBlank()) b else "$a\n$b")
            } finally { bitmap.recycle() }
        }}.awaitAll()
    }
}