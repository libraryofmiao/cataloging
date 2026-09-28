package in.miaolibrary.cataloging.marc
import in.miaolibrary.cataloging.model.CatalogRecord
import kotlinx.serialization.json.*

object MarcInJsonEncoder {
 fun encode(record:CatalogRecord):String {
  val marc=Marc21Builder.build(record)
  val fields=buildJsonArray {
   marc.fields.forEach { f ->
    if(f.value!=null) add(buildJsonObject{put(f.tag,f.value)})
    else add(buildJsonObject{put(f.tag,buildJsonObject{
     put("ind1",f.ind1.toString());put("ind2",f.ind2.toString())
     put("subfields",buildJsonArray{f.subfields.forEach{sf->add(buildJsonObject{put(sf.first.toString(),sf.second)})}})
    })})
   }
  }
  return buildJsonObject{put("leader",marc.leader);put("fields",fields)}.toString()
 }
}
