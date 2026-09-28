package in.miaolibrary.cataloging.capture

import in.miaolibrary.cataloging.model.CatalogRecord

class BookExtractionCoordinator(private val vision:VisionEngine = FreeOnDeviceVisionEngine()){
 suspend fun extract(state:PhotoCaptureState):CatalogRecord{
  check(state.isComplete()){"All four required photographs are required"}
  val result=vision.extract(state.photos.values.toList())
  return CatalogRecord(
   titleProper=result.title.orEmpty(),
   statementOfResponsibility=result.author,
   editionStatement=result.edition,
   publication=in.miaolibrary.cataloging.model.Publication(publisher=result.publisher,date=result.publicationDate),
   isbns=result.isbn?.let{listOf(it)}?:emptyList(),
   printedPrices=result.printedPrice?.let{listOf(it)}?:emptyList()
  )
 }
}
