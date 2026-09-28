package in.miaolibrary.cataloging.koha
import in.miaolibrary.cataloging.model.CopyDraft
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class KohaApiClient(private val baseUrl:String="http://92.4.70.3:8080/api/v1",private val tokenProvider:()->String?) {
 private val client=OkHttpClient()
 fun createBiblio(marcJson:String):String {
  val token=tokenProvider()?:error("Not authenticated")
  val req=Request.Builder().url("${baseUrl}/biblios").post(marcJson.toRequestBody("application/marc-in-json".toMediaType())).header("Authorization","Bearer $token").header("x-record-schema","MARC21").build()
  client.newCall(req).execute().use{r->if(!r.isSuccessful)error("Koha biblio creation failed: HTTP ${r.code}");return r.body?.string()?:error("Koha returned no biblio")}
 }
 fun createItem(biblioId:String,c:CopyDraft):String {
  val body=buildJsonObject{put("external_id",c.barcode);put("home_library_id",c.homeLibrary);put("holding_library_id",c.holdingLibrary);put("location",c.location);put("permanent_location",c.location);put("item_type_id",c.itemType);put("callnumber",c.callNumber);put("call_number_source","ddc");put("acquisition_date",c.acquisitionDate);put("acquisition_source",c.acquisitionSource);c.purchasePrice?.let{put("purchase_price",it)};put("copy_number",c.copyNumber)}.toString()
  val token=tokenProvider()?:error("Not authenticated")
  val req=Request.Builder().url("${baseUrl}/biblios/$biblioId/items").post(body.toRequestBody("application/json".toMediaType())).header("Authorization","Bearer $token").build()
  client.newCall(req).execute().use{r->if(!r.isSuccessful)error("Koha item creation failed: HTTP ${r.code}");return r.body?.string()?:error("Koha returned no item")}
 }
}
