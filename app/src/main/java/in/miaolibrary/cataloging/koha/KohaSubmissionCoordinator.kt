package `in`.miaolibrary.cataloging.koha

import `in`.miaolibrary.cataloging.marc.MarcInJsonEncoder
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CopyDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class KohaSubmissionResult(
    val biblioId: String,
    val createdBarcodes: List<String>,
    val failedBarcode: String? = null
)

class KohaSubmissionCoordinator(private val api: KohaApiClient) {
    suspend fun submit(
        record: CatalogRecord,
        copies: List<CopyDraft>,
        existingBiblioId: String? = null
    ): KohaSubmissionResult = withContext(Dispatchers.IO) {
        require(copies.isNotEmpty())

        copies.filter { it.acquisitionSource == "Donation" }.forEach {
            require(!it.donorDetails.isNullOrBlank()) { "Donor details are required for barcode " + it.barcode }
        }

        val duplicateDraft = copies.groupingBy { it.barcode }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicateDraft == null) { "Duplicate barcode in this submission: " + duplicateDraft!!.key }

        val collisions = copies.filter { api.barcodeExists(it.barcode) }.map { it.barcode }
        require(collisions.isEmpty()) {
            "These barcodes already exist in Koha: " + collisions.joinToString(", ") + ". Regenerate them before submitting."
        }

        val biblioId = existingBiblioId ?: api.createBiblio(MarcInJsonEncoder.encode(record))
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
