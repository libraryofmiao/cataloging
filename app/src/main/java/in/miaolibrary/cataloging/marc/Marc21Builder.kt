package in.miaolibrary.cataloging.marc
import in.miaolibrary.cataloging.model.CatalogRecord

data class MarcField(val tag:String,val value:String?=null,val ind1:Char=' ',val ind2:Char=' ',val subfields:List<Pair<Char,String>>=emptyList())
data class MarcRecord(val leader:String,val fields:List<MarcField>)

object Marc21Builder {
 fun build(r:CatalogRecord):MarcRecord {
  val f=mutableListOf<MarcField>()
  f += MarcField("003",value="SDLM")
  f += MarcField("008",value=build008(r))
  r.isbns.forEach{isbn->f += MarcField("020",subfields=buildList{add('a' to isbn);r.printedPrices.firstOrNull()?.let{add('c' to it)}})}
  f += MarcField("040",subfields=listOf('c' to "SDLM"))
  if(r.languages.isNotEmpty())f += MarcField("041",subfields=r.languages.map{'a' to it.code})
  r.ddc?.let{d->f += MarcField("082",ind1='0',ind2='4',subfields=buildList{add('a' to d);r.ddcEdition?.let{add('2' to it)}})}
  r.mainEntry?.let{p->f += MarcField("100",ind1='1',subfields=listOf('a' to "${p.surname}, ${p.forename.orEmpty()}".trimEnd()))}
  f += MarcField("245",ind1=if(r.mainEntry!=null)'1' else '0',ind2=nonFiling(r.titleProper),subfields=buildList{add('a' to r.titleProper);r.otherTitleInformation?.takeIf(String::isNotBlank)?.let{add('b' to it)};r.statementOfResponsibility?.takeIf(String::isNotBlank)?.let{add('c' to it)}})
  r.editionStatement?.let{f += MarcField("250",subfields=listOf('a' to it))}
  if(r.publication.place!=null||r.publication.publisher!=null||r.publication.date!=null)f += MarcField("264",ind2='1',subfields=buildList{r.publication.place?.let{add('a' to it)};r.publication.publisher?.let{add('b' to it)};r.publication.date?.let{add('c' to it)}})
  val p=r.physicalDescription
  if(p.preliminaryPages!=null||p.mainPages!=null||p.dimensions!=null||p.illustrations.isNotEmpty())f += MarcField("300",subfields=buildList{listOfNotNull(p.preliminaryPages,p.mainPages).joinToString(", ").takeIf(String::isNotBlank)?.let{add('a' to it)};if(p.illustrations.isNotEmpty())add('b' to p.illustrations.joinToString(", "));p.dimensions?.let{add('c' to it)}})
  f += MarcField("336",subfields=listOf('a' to "text",'2' to "rdacontent"))
  f += MarcField("337",subfields=listOf('a' to "unmediated",'2' to "rdamedia"))
  f += MarcField("338",subfields=listOf('a' to "volume",'2' to "rdacarrier"))
  r.series?.let{f += MarcField("490",subfields=listOf('a' to it))}
  r.notes.forEach{f += MarcField("500",subfields=listOf('a' to it))}
  r.contents?.let{f += MarcField("505",subfields=listOf('a' to it))}
  r.summary?.let{f += MarcField("520",subfields=listOf('a' to it))}
  r.subjects.forEach{f += MarcField("650",ind2='0',subfields=listOf('a' to it))}
  r.contributors.forEach{c->f += MarcField("700",ind1='1',subfields=listOf('a' to "${c.person.surname}, ${c.person.forename.orEmpty()}".trimEnd()))}
  f += MarcField("942",subfields=buildList{add('2' to "ddc");add('c' to r.itemType);r.ddc?.let{add('h' to it)}})
  return MarcRecord("00000nam a2200000 a 4500",f)
 }
 private fun nonFiling(t:String)=when{t.trimStart().lowercase().startsWith("the ")->'4';t.trimStart().lowercase().startsWith("an ")->'3';t.trimStart().lowercase().startsWith("a ")->'2';else->'0'}
 private fun build008(r:CatalogRecord):String{
  val a=CharArray(40){' '}
  a[6]='s'
  r.publication.date?.filter(Char::isDigit)?.take(4)?.let{year->year.forEachIndexed{i,c->a[7+i]=c}}
  val lang=(r.languages.firstOrNull()?.code?:"|||").padEnd(3,' ').take(3)
  lang.forEachIndexed{i,c->a[35+i]=c}
  return String(a)
 }
}
