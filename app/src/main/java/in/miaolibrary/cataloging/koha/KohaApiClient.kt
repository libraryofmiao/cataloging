package `in`.miaolibrary.cataloging.koha

import `in`.miaolibrary.cataloging.model.CopyDraft
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class KohaApiClient(
    private val baseUrl: String = "http://92.4.70.3:8080/api/v1",
    private val tokenProvider: () -> String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()

    fun createBiblio(marcJson: String): String {
        val token = tokenProvider() ?: error("Koha API token is not configured")
        val req = Request.Builder().url("${baseUrl}/biblios")
            .post(marcJson.toRequestBody("application/marc-in-json".toMediaType()))
            .header("Authorization", "Bearer ${token}").header("x-record-schema", "MARC21").build()
        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Koha biblio creation failed: HTTP ${response.code}: ${body}")
            val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            return json?.get("biblio_id")?.jsonPrimitive?.content
                ?: json?.get("id")?.jsonPrimitive?.content
                ?: body.trim().takeIf { it.isNotBlank() }
                ?: error("Koha returned no biblio id")
        }
    }

    fun createItem(biblioId: String, c: CopyDraft): String {
        val token = tokenProvider() ?: error("Koha API token is not configured")
        val body = buildJsonObject {
            put("external_id", c.barcode); put("home_library_id", c.homeLibrary); put("holding_library_id", c.holdingLibrary)
            put("location", c.location); put("permanent_location", c.location); put("item_type_id", c.itemType)
            put("callnumber", c.callNumber); put("call_number_source", "ddc"); put("acquisition_date", c.acquisitionDate)
            put("acquisition_source", c.acquisitionSource); c.purchasePrice?.let { put("purchase_price", it) }; c.donorDetails?.takeIf { it.isNotBlank() }?.let { put("internal_notes", "Donor: $it") }; put("copy_number", c.copyNumber)
        }.toString()
        val req = Request.Builder().url("${baseUrl}/biblios/${biblioId}/items")
            .post(body.toRequestBody("application/json".toMediaType())).header("Authorization", "Bearer ${token}").build()
        client.newCall(req).execute().use { response ->
            val result = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Koha item ${c.barcode} failed: HTTP ${response.code}: ${result}")
            return result
        }
    }
}