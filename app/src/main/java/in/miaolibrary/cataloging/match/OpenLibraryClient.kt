package in.miaolibrary.cataloging.match

import in.miaolibrary.cataloging.model.DdcCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class OpenLibraryClient(private val http:OkHttpClient=OkHttpClient()){
 suspend fun lookup(isbn:String?,title:String?,author:String?):JsonObject?=withContext(Dispatchers.IO){
  val q=isbn?.takeIf{it.isNotBlank()}?.let{"isbn=${URLEncoder.encode(it,"UTF-8")}"}?:listOfNotNull(title,author).joinToString(" ").takeIf{it.isNotBlank()}?.let{"q=${URLEncoder.encode(it,"UTF-8")}"}?:return@withContext null
  runCatching{
   val url="https://openlibrary.org/search.json?$q&limit=10"
   val body=http.newCall(Request.Builder().url(url).build()).execute().use{it.body?.string().orEmpty()}
   Json.parseToJsonElement(body).jsonObject
  }.getOrNull()
 }
}

object DdcMatcher {
 fun choose(candidates:List<DdcCandidate>,physicalDdc:String?=null):DdcCandidate? {
  if(candidates.isEmpty())return null
  val normalized=physicalDdc?.trim()
  return candidates.sortedWith(compareByDescending<DdcCandidate>{it.number==normalized}.thenByDescending{it.edition=="23"}.thenByDescending{it.confidence}).first()
 }
}
