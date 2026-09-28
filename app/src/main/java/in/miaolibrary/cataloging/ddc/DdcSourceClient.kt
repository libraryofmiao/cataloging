package in.miaolibrary.cataloging.ddc

import in.miaolibrary.cataloging.model.DdcCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

class DdcSourceClient(private val http:OkHttpClient=OkHttpClient()) {
 private data class Source(val name:String,val base:String)

 private val sources=listOf(
  Source("Tezu Digital Library","https://tezudl.in"),
  Source("State Central Library, Itanagar","https://sclitanagar.in"),
  Source("Pasighat Digital Library","https://pasighatdl.in")
 )

 suspend fun find(isbn:String?,title:String?,author:String?):List<DdcCandidate> = coroutineScope {
  val queries=listOfNotNull(isbn?.takeIf{it.isNotBlank()},listOfNotNull(title,author).joinToString(" ").takeIf{it.isNotBlank()})
  if(queries.isEmpty()) return@coroutineScope emptyList()
  sources.map{source->async(Dispatchers.IO){queries.flatMap{q->searchSource(source,q)}}}.awaitAll().flatten()
 }

 private fun searchSource(source:Source,q:String):List<DdcCandidate>{
  return runCatching {
   val url="${source.base}/cgi-bin/koha/opac-search.pl?idx=&q=${URLEncoder.encode(q,"UTF-8")}"
   val html=http.newCall(Request.Builder().url(url).build()).execute().use{it.body?.string().orEmpty()}
   val doc=Jsoup.parse(html)
   doc.select("a[href*='opac-detail.pl']").take(5).mapNotNull{a->
    val href=a.attr("abs:href")
    val bib=Regex("[?&]biblionumber=(\\d+)").find(href)?.groupValues?.get(1)?:return@mapNotNull null
    fetch082(source,bib,href)
   }
  }.getOrDefault(emptyList())
 }

 private fun fetch082(source:Source,bib:String,recordUrl:String):DdcCandidate?{
  val url="${source.base}/cgi-bin/koha/opac-export.pl?op=export&bib=$bib&format=marcxml"
  val xml=http.newCall(Request.Builder().url(url).build()).execute().use{it.body?.string().orEmpty()}
  if(xml.isBlank())return null
  val doc=Jsoup.parse(xml,"",org.jsoup.parser.Parser.xmlParser())
  val field=doc.select("datafield[tag=082]").firstOrNull()?:return null
  val number=field.select("subfield[code=a]").firstOrNull()?.text()?.trim()?:return null
  val edition=field.select("subfield[code=2]").firstOrNull()?.text()?.trim()
  return DdcCandidate(number,edition,source.name,recordUrl,bib,0.0)
 }
}

class LibraryOfCongressDdcClient(private val http:OkHttpClient=OkHttpClient()){
 suspend fun find(isbn:String?,title:String?,author:String?):List<DdcCandidate>=withContext(Dispatchers.IO){
  val q=isbn?.takeIf{it.isNotBlank()}?.let{"isbn=$it"}?:listOfNotNull(title,author).joinToString(" ").takeIf{it.isNotBlank()}?.let{"title=$it"}?:return@withContext emptyList()
  runCatching{
   val url="https://www.loc.gov/z3950/lcdb?version=1.1&operation=searchRetrieve&query=${URLEncoder.encode(q,"UTF-8")}&maximumRecords=10&recordSchema=marcxml"
   val xml=http.newCall(Request.Builder().url(url).build()).execute().use{it.body?.string().orEmpty()}
   val doc=Jsoup.parse(xml,"",org.jsoup.parser.Parser.xmlParser())
   doc.select("datafield[tag=082]").mapNotNull{f->
    val n=f.select("subfield[code=a]").firstOrNull()?.text()?.trim()?:return@mapNotNull null
    DdcCandidate(n,f.select("subfield[code=2]").firstOrNull()?.text()?.trim(),"Library of Congress",null,null,0.0)
   }
  }.getOrDefault(emptyList())
 }
}
