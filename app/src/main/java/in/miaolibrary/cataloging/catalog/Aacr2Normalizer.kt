package in.miaolibrary.cataloging.catalog
import in.miaolibrary.cataloging.model.CatalogRecord
import in.miaolibrary.cataloging.model.Person

data class ValidationIssue(val field:String,val message:String,val blocking:Boolean=true)
data class NormalizationResult(val record:CatalogRecord,val issues:List<ValidationIssue>){val valid:Boolean get()=issues.none{it.blocking}}

object Aacr2Normalizer {
 private val supportedLanguages=setOf("eng","hin","asm","ben","nep")
 fun normalize(input:CatalogRecord):NormalizationResult {
  val issues=mutableListOf<ValidationIssue>()
  val title=input.titleProper.trim()
  if(title.isBlank()) issues += ValidationIssue("245$a","Title proper is required.")
  input.languages.forEach { if(it.code !in supportedLanguages) issues += ValidationIssue("041","Unsupported language code: ${it.code}.") }
  input.isbns.forEach { if(!validIsbn(it)) issues += ValidationIssue("020$a","Invalid ISBN: ${it}.") }
  if(input.ddc!=null && input.ddcEdition!="23") issues += ValidationIssue("082$2","Final DDC must be verified as edition 23.")
  val main=input.mainEntry?.let { Person(it.surname.trim().replace(Regex("\\s+")," "),it.forename?.trim()?.replace(Regex("\\s+")," ")) }
  val call=input.ddc?.let { ddc -> main?.surname?.takeIf{it.isNotBlank()}?.let { "${ddc} ${it.take(3).uppercase()}" } }
  return NormalizationResult(input.copy(titleProper=title,mainEntry=main,callNumber=call),issues)
 }
 private fun validIsbn(raw:String):Boolean {
  val s=raw.replace("-","").replace(" ","")
  return when(s.length){
   10 -> s.mapIndexed{i,c->(if(c.equals('X',true))10 else c.digitToIntOrNull() ?: -99)*(10-i)}.sum()%11==0
   13 -> s.all(Char::isDigit) && s.mapIndexed{i,c->c.digitToInt()*if(i%2==0)1 else 3}.sum()%10==0
   else -> false
  }
 }
}