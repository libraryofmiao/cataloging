package `in`.miaolibrary.cataloging.capture

import android.graphics.BitmapFactory
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

enum class DetectedPhotoType(val label:String) {
    FRONT_COVER("Front cover"),
    TITLE_PAGE("Title page"),
    PUBLICATION_PAGE("Copyright / publication page"),
    ISBN_PAGE("ISBN / barcode page"),
    UNKNOWN("Book page")
}

data class PhotoDetection(val type:DetectedPhotoType,val confidence:Float,val reason:String)

class SmartPhotoClassifier {
    private val textRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner=BarcodeScanning.getClient()

    suspend fun classify(path:String):PhotoDetection {
        val bitmap=BitmapFactory.decodeFile(File(path).absolutePath)
            ?: return PhotoDetection(DetectedPhotoType.UNKNOWN,0f,"Image could not be read")
        val image=InputImage.fromBitmap(bitmap,0)
        return try {
            val text=runCatching { textRecognizer.process(image).await().text }.getOrDefault("")
            val barcodes=runCatching { barcodeScanner.process(image).await() }.getOrDefault(emptyList())
            val lower=text.lowercase()
            val isbnSignal=Regex("""(?i)\b(?:isbn(?:[- ]?1[03])?\s*[:.]?\s*)?(?:97[89][ -]?)?\d[\d -]{8,15}[\dx]\b""").containsMatchIn(lower)
            val barcodeSignal=barcodes.isNotEmpty()
            val publicationSignal=listOf("published","publisher","copyright","printed","edition","first edition","revised edition").count { lower.contains(it) }
            val titleSignal=listOf("by ","written by","author","edited by","translated by").count { lower.contains(it) }
            val yearSignal=Regex("""\b(18|19|20)\d{2}\b""").containsMatchIn(lower)
            when {
                isbnSignal || barcodeSignal -> PhotoDetection(DetectedPhotoType.ISBN_PAGE,0.96f,if(barcodeSignal) "Barcode detected" else "ISBN pattern detected")
                publicationSignal>=2 && yearSignal -> PhotoDetection(DetectedPhotoType.PUBLICATION_PAGE,0.90f,"Publication/copyright wording detected")
                titleSignal>0 && text.lines().count { it.trim().length>=3 }>=2 -> PhotoDetection(DetectedPhotoType.TITLE_PAGE,0.82f,"Title/author wording detected")
                text.lines().count { it.trim().length>=3 }<=4 -> PhotoDetection(DetectedPhotoType.FRONT_COVER,0.62f,"Sparse cover-like text")
                else -> PhotoDetection(DetectedPhotoType.UNKNOWN,0.35f,"No strong page-type signal")
            }
        } finally { bitmap.recycle() }
    }
}
