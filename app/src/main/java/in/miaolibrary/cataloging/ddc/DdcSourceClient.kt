package `in`.miaolibrary.cataloging.ddc

import `in`.miaolibrary.cataloging.model.DdcCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DdcSourceClient {
    private data class Source(val name: String, val base: String, val priority: Double)
    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).writeTimeout(3, TimeUnit.SECONDS).build()
    private val sources = listOf(
        Source("Tezu Digital Library", "http://agnee.tezu.ernet.in:8999", 1.0),
        Source("State Central Library, Itanagar", "https://sclitanagar.in", 0.95),
        Source("Pasighat Digital Library", "https://pasighatdl.in", 0.90)
    )

    suspend fun find(isbn: String?, title: String?, author: String?): List<DdcCandidate> = coroutineScope {
        val queries = listOfNotNull(isbn?.trim()?.takeIf(String::isNotBlank),
            listOfNotNull(title?.trim()?.takeIf(String::isNotBlank), author?.trim()?.takeIf(String::isNotBlank)).joinToString(" ").takeIf(String::isNotBlank))
        if (queries.isEmpty()) return@coroutineScope emptyList()
        sources.map { source -> async(Dispatchers.IO) { queries.flatMap { searchSource(source, it) } } }.awaitAll().flatten()
    }

    private fun searchSource(source: Source, q: String): List<DdcCandidate> = runCatching {
        val url = "${source.base}/cgi-bin/koha/opac-search.pl?idx=&q=${URLEncoder.encode(q, "UTF-8")}"
        val html = http.newCall(Request.Builder().url(url).header("Accept", "text/html").build()).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
        Jsoup.parse(html).select("a[href*='opac-detail.pl']").take(5).mapNotNull { a ->
            val href = a.attr("abs:href")
            val bib = Regex("[?&]biblionumber=(\\d+)").find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            fetch082(source, bib, href)
        }
    }.getOrDefault(emptyList())

    private fun fetch082(source: Source, bib: String, recordUrl: String): DdcCandidate? {
        val url = "${source.base}/cgi-bin/koha/opac-export.pl?op=export&bib=${bib}&format=marcxml"
        val xml = http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
        if (xml.isBlank()) return null
        val field = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser()).select("datafield[tag=082]").firstOrNull() ?: return null
        val number = field.select("subfield[code=a]").firstOrNull()?.text()?.trim() ?: return null
        val edition = field.select("subfield[code=2]").firstOrNull()?.text()?.trim()
        return DdcCandidate(number, edition, source.name, recordUrl, bib, source.priority)
    }
}

class LibraryOfCongressDdcClient {
    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()

    suspend fun find(isbn: String?, title: String?, author: String?): List<DdcCandidate> = withContext(Dispatchers.IO) {
        val q = isbn?.trim()?.takeIf(String::isNotBlank)?.let { "isbn=$it" }
            ?: listOfNotNull(title?.trim(), author?.trim()).joinToString(" ").takeIf(String::isNotBlank)?.let { "title=$it" }
            ?: return@withContext emptyList()
        runCatching {
            val url = "http://lx2.loc.gov:210/LCDB?version=1.1&operation=searchRetrieve&query=${URLEncoder.encode(q, "UTF-8")}&maximumRecords=10&recordSchema=marcxml"
            val xml = http.newCall(Request.Builder().url(url).header("Accept", "application/xml").build()).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
            if (xml.isBlank()) return@runCatching emptyList<DdcCandidate>()
            Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser()).select("datafield[tag=082]").mapNotNull { f ->
                val n = f.select("subfield[code=a]").firstOrNull()?.text()?.trim() ?: return@mapNotNull null
                val edition = f.select("subfield[code=2]").firstOrNull()?.text()?.trim()
                DdcCandidate(n, edition, "Library of Congress", null, null, if (edition == "23") 1.0 else 0.5)
            }
        }.getOrDefault(emptyList())
    }
}