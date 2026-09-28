package in.miaolibrary.cataloging.match

import in.miaolibrary.cataloging.model.DdcCandidate
import in.miaolibrary.cataloging.model.ExternalIdentityCandidate
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

    suspend fun findIdentityMatches(isbn: String?, title: String?, author: String?): List<ExternalIdentityCandidate> = withContext(Dispatchers.IO) {
        val result = lookup(isbn, title, author) ?: return@withContext emptyList()
        val docs = result["docs"]?.jsonArray ?: return@withContext emptyList()
        docs.mapNotNull { element ->
            val doc = element.jsonObject
            val candidateTitle = doc["title"]?.jsonPrimitive?.contentOrNull
            val candidateAuthor = doc["author_name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            val candidateIsbn = doc["isbn"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            val score = identityScore(isbn, title, author, candidateIsbn, candidateTitle, candidateAuthor)
            if (score < 0.70) null else ExternalIdentityCandidate(
                candidateTitle, candidateAuthor, candidateIsbn,
                "Open Library", doc["key"]?.jsonPrimitive?.contentOrNull?.let { "https://openlibrary.org$it" }, score
            )
        }.sortedByDescending { it.confidence }.take(5)
    }

    suspend fun findVerifiedDdc(isbn: String?, title: String?, author: String?): List<DdcCandidate> = withContext(Dispatchers.IO) {
        val result = lookup(isbn, title, author) ?: return@withContext emptyList()
        val docs = result["docs"]?.jsonArray ?: return@withContext emptyList()
        val lccns = docs.filter { d ->
            val o = d.jsonObject
            identityScore(isbn, title, author, o["isbn"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull, o["title"]?.jsonPrimitive?.contentOrNull, o["author_name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull) >= 0.70
        }.flatMap { element -> element.jsonObject["lccn"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty() }.distinct().take(10)
        lccns.mapNotNull { fetchLocDdc(it) }
    }

    private fun identityScore(isbn:String?, title:String?, author:String?, candidateIsbn:String?, candidateTitle:String?, candidateAuthor:String?): Double {
        val aIsbn = isbn?.filter(Char::isDigit)
        val bIsbn = candidateIsbn?.filter(Char::isDigit)
        if (!aIsbn.isNullOrBlank() && aIsbn == bIsbn) return 1.0
        fun norm(s:String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val t = norm(title)
        val ct = norm(candidateTitle)
        val aw = norm(author).split(" ").filter(String::isNotBlank).toSet()
        val caw = norm(candidateAuthor).split(" ").filter(String::isNotBlank).toSet()
        val titleScore = if (t.isNotBlank() && ct.isNotBlank()) {
            val common = t.split(" ").intersect(ct.split(" ").toSet()).size.toDouble()
            common / maxOf(t.split(" ").size, ct.split(" ").size)
        } else 0.0
        val authorScore = if (aw.isNotEmpty() && caw.isNotEmpty()) aw.intersect(caw).size.toDouble() / maxOf(aw.size, caw.size) else 0.0
        return titleScore * 0.7 + authorScore * 0.3
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
    /**
     * DDC 23 is accepted only when the evidence is edition-exact.
     *
     * The pipeline deliberately does NOT silently choose a higher-priority source
     * when verified sources disagree. A disagreement is left for catalogue review;
     * the UI can still display every candidate and its source.
     */
    fun choose(candidates: List<DdcCandidate>, physicalDdc: String? = null): DdcCandidate? {
        val verified = candidates
            .filter { it.edition?.trim() == "23" && it.number.isNotBlank() }
            .map { it.copy(number = normalize(it.number)) }

        if (verified.isEmpty()) return null

        val distinctNumbers = verified.map { it.number }.distinct()
        if (distinctNumbers.size != 1) return null

        val normalizedPhysical = physicalDdc?.let(::normalize)?.takeIf { it.isNotBlank() }
        if (normalizedPhysical != null && normalizedPhysical != distinctNumbers.single()) {
            // Physical evidence conflicts with catalogue evidence: require review.
            return null
        }

        fun priority(source: String): Int = when {
            source.contains("Tezu", true) -> 6
            source.contains("State Central Library", true) -> 5
            source.contains("Pasighat", true) -> 4
            source.contains("Library of Congress", true) -> 3
            source.contains("Open Library", true) -> 2
            else -> 1
        }

        return verified
            .sortedWith(
                compareByDescending<DdcCandidate> { priority(it.source) }
                    .thenByDescending { it.confidence }
            )
            .first()
    }

    fun verifiedCandidates(candidates: List<DdcCandidate>): List<DdcCandidate> =
        candidates
            .filter { it.edition?.trim() == "23" && it.number.isNotBlank() }
            .map { it.copy(number = normalize(it.number)) }

    fun hasDisagreement(candidates: List<DdcCandidate>): Boolean =
        verifiedCandidates(candidates).map { it.number }.distinct().size > 1

    private fun normalize(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .removeSuffix(".")
            .trim()
}
