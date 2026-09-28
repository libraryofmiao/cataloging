package in.miaolibrary.cataloging.match

import in.miaolibrary.cataloging.model.DdcCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class OpenLibraryClient(private val http: OkHttpClient = OkHttpClient()) {
    suspend fun lookup(isbn: String?, title: String?, author: String?): JsonObject? = withContext(Dispatchers.IO) {
        val query = isbn?.trim()?.takeIf { it.isNotBlank() }?.let { "isbn=" + URLEncoder.encode(it, "UTF-8") }
            ?: listOfNotNull(title?.trim()?.takeIf { it.isNotBlank() }, author?.trim()?.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }?.let { "q=" + URLEncoder.encode(it, "UTF-8") }
            ?: return@withContext null
        runCatching {
            val url = "https://openlibrary.org/search.json?" + query + "&limit=10"
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { Json.parseToJsonElement(it).jsonObject }
            }
        }.getOrNull()
    }

    suspend fun findVerifiedDdc(isbn: String?, title: String?, author: String?): List<DdcCandidate> = withContext(Dispatchers.IO) {
        val result = lookup(isbn, title, author) ?: return@withContext emptyList()
        val docs = result["docs"]?.jsonArray ?: return@withContext emptyList()
        val lccns = docs.flatMap { element -> element.jsonObject["lccn"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty() }.distinct().take(10)
        lccns.mapNotNull { fetchLocDdc(it) }
    }

    private fun fetchLocDdc(lccn: String): DdcCandidate? = runCatching {
        val clean = lccn.trim()
        if (clean.isBlank()) return@runCatching null
        val url = "https://lccn.loc.gov/" + URLEncoder.encode(clean, "UTF-8") + "/marcxml"
        val request = Request.Builder().url(url).header("Accept", "application/xml").build()
        val xml = http.newCall(request).execute().use { response -> if (!response.isSuccessful) return@runCatching null else response.body?.string().orEmpty() }
        if (xml.isBlank()) return@runCatching null
        val field = Regex("""<datafield[^>]*tag=["']082["'][^>]*>(.*?)</datafield>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(xml)?.groupValues?.getOrNull(1) ?: return@runCatching null
        val number = Regex("""<subfield[^>]*code=["']a["'][^>]*>(.*?)</subfield>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(field)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: return@runCatching null
        val edition = Regex("""<subfield[^>]*code=["']2["'][^>]*>(.*?)</subfield>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(field)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim()
        if (number.isBlank()) return@runCatching null
        DdcCandidate(number, edition, "Library of Congress via Open Library LCCN", "https://lccn.loc.gov/" + clean, clean, if (edition == "23") 1.0 else 0.7)
    }.getOrNull()
}

object DdcMatcher {
    fun choose(candidates: List<DdcCandidate>, physicalDdc: String? = null): DdcCandidate? {
        if (candidates.isEmpty()) return null
        val normalized = physicalDdc?.trim()
        return candidates.sortedWith(compareByDescending<DdcCandidate> { it.number == normalized }.thenByDescending { it.edition == "23" }.thenByDescending { it.confidence }).first()
    }
}