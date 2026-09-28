package `in`.miaolibrary.cataloging.capture

import `in`.miaolibrary.cataloging.BuildConfig

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
    private val http: OkHttpClient = DEFAULT_HTTP
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
                put("maxOutputTokens", 4096)
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        listOf("title","author","publisher","publication_date","isbn","edition","main_pages","language",
                            "printed_price","publication_place","preliminary_pages","illustrations","dimensions","series",
                            "contents","summary","physical_warnings").forEach { key ->
                            putJsonObject(key) { putJsonArray("type") { add("string"); add("null") } }
                        }
                    }
                    putJsonArray("required") {
                        listOf("title","author","publisher","publication_date","isbn","edition","main_pages","language",
                            "printed_price","publication_place","preliminary_pages","illustrations","dimensions","series",
                            "contents","summary","physical_warnings").forEach { add(it) }
                    }
                }
            }
        }.toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        repeat(3) { attempt ->
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) return@withContext parseResponse(raw)
                if ((response.code == 429 || response.code >= 500) && attempt < 2) {
                    kotlinx.coroutines.delay(250L shl attempt)
                } else {
                    throw IllegalStateException("Gemini request failed (${response.code}): ${raw.take(500)}")
                }
            }
        }
        throw IllegalStateException("Gemini extraction failed")
    }

    private fun compressForGemini(file: File): ByteArray {
        require(file.exists()) { "Photo not found: ${file.absolutePath}" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read image: ${file.name}" }
        val maxDimension = 1600
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: throw IllegalStateException("Unable to decode image: ${file.name}")
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
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

    private class GeminiRequestException(val code: Int, message: String) : IllegalStateException(message)

    companion object {
        private val DEFAULT_HTTP = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

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
