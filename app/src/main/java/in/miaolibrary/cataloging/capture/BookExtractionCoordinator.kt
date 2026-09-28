package `in`.miaolibrary.cataloging.capture

import `in`.miaolibrary.cataloging.model.*

class BookExtractionCoordinator(private val vision: VisionEngine) {
 suspend fun extract(state: PhotoCaptureState): CatalogRecord {
  check(state.isComplete()) { "All four required photographs are required" }
  val r=vision.extract(state.photos.values.toList())
  return CatalogRecord(
   titleProper=r.title.orEmpty(), statementOfResponsibility=r.author, editionStatement=r.edition,
   publication=Publication(r.publicationPlace,r.publisher,r.publicationDate),
   physicalDescription=PhysicalDescription(r.preliminaryPages,r.pages,r.illustrations?.let(::listOf)?:emptyList(),r.dimensions),
   isbns=r.isbn?.let(::listOf)?:emptyList(), printedPrices=r.printedPrice?.let(::listOf)?:emptyList(),
   series=r.series, contents=r.contents, summary=r.summary,
   languages=r.language?.let { listOf(Language(it,"")) }?:emptyList()
  )
 }
}
