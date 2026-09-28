package `in`.miaolibrary.cataloging.koha

import `in`.miaolibrary.cataloging.marc.Marc21Builder
import `in`.miaolibrary.cataloging.marc.MarcInJsonEncoder
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CopyDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class KohaSubmissionResult(val biblioId: String, val createdBarcodes: List<String>, val failedBarcode: String? = null)

class KohaSubmissionCoordinator(private val api: KohaApiClient) {
    suspend fun submit(record: CatalogRecord, copies: List<CopyDraft>, existingBiblioId: String? = null): KohaSubmissionResult =
        withContext(Dispatchers.IO) {
            require(copies.isNotEmpty()) { "At least one copy is required" }
            val biblioId = existingBiblioId ?: api.createBiblio(MarcInJsonEncoder.encode(Marc21Builder.build(record)))
            val created = mutableListOf<String>()
            for (copy in copies) {
                try {
                    api.createItem(biblioId, copy)
                    created += copy.barcode
                } catch (t: Throwable) {
                    return@withContext KohaSubmissionResult(biblioId, created, copy.barcode)
                }
            }
            KohaSubmissionResult(biblioId, created)
        }
}