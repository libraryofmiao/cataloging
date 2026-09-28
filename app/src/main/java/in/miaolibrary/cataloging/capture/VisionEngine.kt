package in.miaolibrary.cataloging.capture

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import in.miaolibrary.cataloging.model.EvidenceSource
import in.miaolibrary.cataloging.model.EvidenceValue

data class ExtractedBook(val title:String?=null,val author:String?=null,val publisher:String?=null,val publicationDate:String?=null,val isbn:String?=null,val edition:String?=null,val pages:String?=null,val language:String?=null,val printedPrice:String?=null)

interface VisionEngine { suspend fun extract(photoPaths:List<String>):ExtractedBook }

class LocalVisionEngine:VisionEngine{
 override suspend fun extract(photoPaths:List<String>):ExtractedBook=withContext(Dispatchers.Default){
  photoPaths.forEach{BitmapFactory.decodeFile(it)?.recycle()}
  ExtractedBook()
 }
}
