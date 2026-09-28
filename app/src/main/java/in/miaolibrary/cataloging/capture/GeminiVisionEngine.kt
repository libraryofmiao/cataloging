package in.miaolibrary.cataloging.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiVisionEngine(private val apiKey: String, private val model: String = "gemini-3.8-flash") : VisionEngine {
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    override suspend fun extract(photoPaths: List<String>): ExtractedBook = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured." }
        val input = JSONArray().put(JSONObject().put("type", "text").put("text", PROMPT))
        photoPaths.forEach { path ->
            val file = File(path)
            require(file.exists()) { "Photograph is missing: " + file.name }
            input.put(JSONObject().put("type", "image").put("mime_type", "image/jpeg").put("data", Base64.encodeToString(compress(file), Base64.NO_WRAP)))
        }
        val schema = JSONObject().put("type", "object").put("properties", JSONObject()
            .put("title", nullable()).put("author", nullable()).put("publisher", nullable())
            .put("publicationDate", nullable()).put("publicationPlace", nullable()).put("isbn", nullable())
            .put("edition", nullable()).put("pages", nullable()).put("preliminaryPages", nullable())
            .put("illustrations", nullable()).put("dimensions", nullable()).put("language", nullable())
            .put("printedPrice", nullable()).put("series", nullable()).put("contents", nullable())
            .put("summary", nullable()).put("physicalWarnings", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            .put("required", JSONArray().apply { listOf("title","author","publisher","publicationDate","publicationPlace","isbn","edition","pages","preliminaryPages","illustrations","dimensions","language","printedPrice","series","contents","summary","physicalWarnings").forEach(::put) })
        val body = JSONObject().put("model", model).put("input", input).put("store", false)
            .put("system_instruction", SYSTEM).put("response_format", JSONObject().put("type", "text").put("mime_type", "application/json").put("schema", schema))
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .addHeader("x-goog-api-key", apiKey).post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Gemini request failed (" + response.code + "): " + raw.take(500))
            parse(raw)
        }
    }

    private fun nullable() = JSONObject().put("type", JSONArray().put("string").put("null"))

    private fun compress(file: File): ByteArray {
        val source = BitmapFactory.decodeFile(file.absolutePath) ?: error("Could not read " + file.name)
        val scale = minOf(1f, 1600f / source.width, 1600f / source.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        return out.toByteArray()
    }

    private fun parse(raw: String): ExtractedBook {
        val text = findText(JSONObject(raw)) ?: error("Gemini returned no text output")
        val x = JSONObject(text)
        fun v(n: String) = if (x.isNull(n)) null else x.optString(n).takeIf { it.isNotBlank() }
        val warnings = x.optJSONArray("physicalWarnings")
        val warningText = buildString {
            if (warnings != null) for (i in 0 until warnings.length()) {
                val w = warnings.optString(i)
                if (w.isNotBlank()) { if (isNotEmpty()) append("; "); append(w) }
            }
        }
        return ExtractedBook(v("title"),v("author"),v("publisher"),v("publicationDate"),v("isbn"),v("edition"),v("pages"),v("language"),v("printedPrice"),v("publicationPlace"),v("preliminaryPages"),v("illustrations"),v("dimensions"),v("series"),v("contents"),v("summary"),warningText.takeIf { it.isNotBlank() })
    }

    private fun findText(value: Any): String? {
        if (value is JSONObject) {
            value.optString("output_text","").takeIf { it.isNotBlank() }?.let { return it }
            value.optString("text","").takeIf { it.isNotBlank() }?.let { return it }
            val keys=value.keys()
            while(keys.hasNext()) findText(value.opt(keys.next()))?.let { return it }
        } else if (value is JSONArray) for(i in 0 until value.length()) findText(value.opt(i))?.let { return it }
        return null
    }

    companion object {
        private const val SYSTEM = "You are the multimodal cataloguing assistant for Sub Divisional Library Miao (SDLM). Extract only facts established by the supplied physical-book photographs. Never invent, guess, autocomplete, or silently correct facts. Return null when a value is not established. Preserve physical wording. Copy ISBN and printed price exactly when readable. Do not calculate missing ISBN digits or infer pagination from another edition. Put ambiguities, contradictions and damaged/obscured values in physicalWarnings. Do not assign DDC or LCSH. This is an evidence extraction proposal for mandatory human review, never a final catalogue record."
        private const val PROMPT = "Examine all supplied photographs together: front cover, title page, copyright/publication page and ISBN/barcode page. Extract title, author/statement of responsibility, publisher, publication place/date, ISBN, edition, preliminary/main pagination, illustrations, dimensions, language, printed price with original currency, series, visible contents, printed summary, and every ambiguity requiring human verification. Return only the requested JSON."
    }
}
