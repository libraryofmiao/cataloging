package in.miaolibrary.cataloging.model
import kotlinx.serialization.Serializable
enum class EvidenceSource { PHYSICAL, EXTERNAL_CATALOGUE, PUBLISHER, AI_INFERENCE }
@Serializable data class EvidenceValue(val value:String,val source:EvidenceSource,val confidence:Double=1.0,val observed:Boolean=true)
@Serializable data class Person(val surname:String,val forename:String?=null)
@Serializable data class Contributor(val person:Person,val role:String)
@Serializable data class Publication(val place:String?=null,val publisher:String?=null,val date:String?=null)
@Serializable data class PhysicalDescription(val preliminaryPages:String?=null,val mainPages:String?=null,val illustrations:List<String> = emptyList(),val dimensions:String?=null)
@Serializable data class Language(val name:String,val code:String)
@Serializable data class DdcCandidate(val number:String,val edition:String?,val source:String,val recordUrl:String?,val sourceBiblionumber:String?,val confidence:Double)
@Serializable data class CatalogRecord(
 val titleProper:String,val otherTitleInformation:String?=null,val statementOfResponsibility:String?=null,
 val mainEntry:Person?=null,val contributors:List<Contributor> = emptyList(),val editionStatement:String?=null,
 val publication:Publication=Publication(),val physicalDescription:PhysicalDescription=PhysicalDescription(),
 val languages:List<Language> = emptyList(),val isbns:List<String> = emptyList(),val printedPrices:List<String> = emptyList(),
 val series:String?=null,val notes:List<String> = emptyList(),val bibliographyNote:String?=null,val contents:String?=null,val summary:String?=null,
 val subjects:List<String> = emptyList(),val ddc:String?=null,val ddcEdition:String?=null,val callNumber:String?=null,
 val itemType:String="BOOKS",val evidence:BookEvidence?=null)
@Serializable data class BookEvidence(val photos:List<String> = emptyList(),val fields:Map<String,List<EvidenceValue>> = emptyMap(),val ddcCandidates:List<DdcCandidate> = emptyList())
data class CopyDraft(val barcode:String,val homeLibrary:String="SDLM",val holdingLibrary:String="SDLM",val location:String,val itemType:String="BOOKS",val callNumber:String,val acquisitionDate:String,val acquisitionSource:String,val purchasePrice:Double?=null,val donorDetails:String?=null,val copyNumber:String="1")
