package in.miaolibrary.cataloging.capture

data class ExtractedBook(val title:String?=null,val author:String?=null,val publisher:String?=null,val publicationDate:String?=null,val isbn:String?=null,val edition:String?=null,val pages:String?=null,val language:String?=null,val printedPrice:String?=null)

interface VisionEngine { suspend fun extract(photoPaths:List<String>):ExtractedBook }

class FreeOnDeviceVisionEngine(
 private val ocr:FreeOcrEngine=FreeOcrEngine(),
 private val heuristics:FreeBookHeuristics=FreeBookHeuristics()
):VisionEngine{
 override suspend fun extract(photoPaths:List<String>):ExtractedBook{
  val pages=ocr.read(photoPaths)
  val x=heuristics.extract(pages)
  return ExtractedBook(x.title,x.author,x.publisher,x.publicationDate,x.isbn,x.edition,x.pages,x.language,x.printedPrice)
 }
}
