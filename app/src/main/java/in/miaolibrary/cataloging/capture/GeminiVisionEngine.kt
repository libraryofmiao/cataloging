package `in`.miaolibrary.cataloging.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiVisionEngine(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) : VisionEngine {
    override suspend fun extract(photoPaths: List<String>): ExtractedBook = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured. Add GEMINI_API_KEY to the app build configuration." }
        require(photoPaths.isNotEmpty()) { "No photographs were supplied" }

        val parts = buildJsonArray {
            addJsonObject { put("text", EXTRACTION_PROMPT) }
            photoPaths.forEachIndexed { index, path ->
                val jpeg = compressForGemini(File(path))
                addJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", "image/jpeg")
                        put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    }
                }
                addJsonObject { put("text", "Photograph ${index + 1} of ${photoPaths.size}. Use it as physical evidence.") }
            }
        }
        val body = buildJsonObject {
            putJsonObject("contents") { putJsonArray("parts") { parts.forEach { add(it) } } }
            putJsonObject("generationConfig") {
                put("temperature", 0.0)
                put("responseMimeType", "application/json")
            }
        }.toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Gemini request failed (${response.code}): ${raw.take(500)}")
            parseResponse(raw)
        }
    }

    private fun compressForGemini(file: File): ByteArray {
        require(file.exists()) { "Photo not found: ${file.absolutePath}" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read image: ${file.name}" }
        val maxDimension = 1600
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bounds.outWidth, bounds.outHeight))
        val width = (bounds.outWidth * scale).toInt().coerceAtLeast(1)
        val height = (bounds.outHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: throw IllegalStateException("Unable to decode image: ${file.name}")
        val scaled = if (bitmap.width == width && bitmap.height == height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun parseResponse(raw: String): ExtractedBook {
        val root = Json.parseToJsonElement(raw).jsonObject
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Gemini returned no extraction content")
        val fence = "```"
        val cleaned = text.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
        val o = Json.parseToJsonElement(cleaned).jsonObject
        fun s(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return ExtractedBook(
            title=s("title"), author=s("author"), publisher=s("publisher"), publicationDate=s("publication_date"),
            isbn=s("isbn"), edition=s("edition"), pages=s("main_pages"), language=s("language"),
            printedPrice=s("printed_price"), publicationPlace=s("publication_place"), preliminaryPages=s("preliminary_pages"),
            illustrations=s("illustrations"), dimensions=s("dimensions"), series=s("series"), contents=s("contents"),
            summary=s("summary"), physicalWarnings=s("physical_warnings")
        )
    }

    companion object {
        private const val EXTRACTION_PROMPT = """
You are the bibliographic cataloguing vision model for a library. Examine ALL supplied photographs together.
Extract ONLY what is visibly supported by the physical book. Never invent missing data.
Priority: title page > copyright/publication page > ISBN/barcode page > front cover.
Use the title page as the chief source for title and statement of responsibility.
Preserve printed wording, names, publisher, place, date, edition, pagination, dimensions, series, contents, summary/abstract, language, ISBN and printed price.
If a field is absent or unreadable, return null. Do not infer a first edition. Do not convert currencies.
Do not create DDC or subject headings from guesswork.
For pagination, distinguish preliminary pages from the main numbered sequence when visible.
For illustrations, report only visible/printed physical-description evidence.
For author, return the printed personal name as shown, including surname order if evident.
Return ONLY valid JSON with exactly these keys:
title, author, publisher, publication_date, isbn, edition, main_pages, language, printed_price,
publication_place, preliminary_pages, illustrations, dimensions, series, contents, summary, physical_warnings.
All values must be strings or null.
"""
    }
}
