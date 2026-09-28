package `in`.miaolibrary.cataloging.barcode

import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CopyDraft
import java.time.LocalDate

class CopyDraftFactory(private val sequence: BarcodeSequence) {
    fun create(record: CatalogRecord, count: Int, location: String, source: String, itemType: String): List<CopyDraft> {
        require(count > 0) { "At least one copy is required" }
        require(location.isNotBlank()) { "Location is required" }
        require(source.isNotBlank()) { "Acquisition source is required" }
        return (1..count).map { index ->
            CopyDraft(
                barcode = sequence.next(),
                location = location,
                itemType = itemType,
                callNumber = record.callNumber ?: error("Call number is required"),
                acquisitionDate = LocalDate.now().toString(),
                acquisitionSource = source,
                copyNumber = index.toString()
            )
        }
    }
}