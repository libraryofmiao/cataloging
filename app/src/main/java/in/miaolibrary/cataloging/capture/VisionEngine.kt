package `in`.miaolibrary.cataloging.capture

data class ExtractedBook(
    val title:String?=null,val author:String?=null,val publisher:String?=null,val publicationDate:String?=null,
    val isbn:String?=null,val edition:String?=null,val pages:String?=null,val language:String?=null,val printedPrice:String?=null,
    val publicationPlace:String?=null,val preliminaryPages:String?=null,val illustrations:String?=null,val dimensions:String?=null,
    val series:String?=null,val contents:String?=null,val summary:String?=null,val physicalWarnings:String?=null
)
interface VisionEngine { suspend fun extract(photoPaths:List<String>):ExtractedBook }

class FreeOnDeviceVisionEngine(
    private val ocr:FreeOcrEngine=FreeOcrEngine(),
    private val heuristics:FreeBookHeuristics=FreeBookHeuristics()
):VisionEngine {
    override suspend fun extract(photoPaths:List<String>):ExtractedBook {
        val pages = ocr.read(photoPaths)
        val x = heuristics.extract(pages)
        return ExtractedBook(
            x.title,x.author,x.publisher,x.publicationDate,x.isbn,x.edition,x.pages,x.language,
            x.printedPrice,x.publicationPlace,x.preliminaryPages,x.illustrations,x.dimensions,
            x.series,x.contents,x.summary,x.physicalWarnings
        )
    }
}
