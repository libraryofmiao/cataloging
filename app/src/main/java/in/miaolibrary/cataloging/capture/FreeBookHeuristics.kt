package in.miaolibrary.cataloging.capture

data class FreeExtractedBook(
 val title:String?=null,val author:String?=null,val publisher:String?=null,
 val publicationDate:String?=null,val isbn:String?=null,val edition:String?=null,
 val pages:String?=null,val language:String?=null,val printedPrice:String?=null
)

class FreeBookHeuristics {
 fun extract(pages:List<OcrPage>):FreeExtractedBook{
  val all=pages.joinToString("\n"){it.text}
  val isbn=Regex("""(?i)(?:ISBN(?:-1[03])?\s*[:\- ]*)?((?:97[89][\- ]?)?\d[\d\- ]{8,15}[\dX])""").find(all)?.groupValues?.getOrNull(1)?.replace(" ","")
  val year=Regex("""\b(18|19|20)\d{2}\b""").find(all)?.value
  val pagesCount=Regex("""(?i)(?:pages?|p\.)\s*[:.]?\s*(\d{1,4})\b""").find(all)?.groupValues?.getOrNull(1)
  val price=Regex("""(?:₹|Rs\.?|INR|\$|£|€)\s*[0-9][0-9,]*(?:\.\d{1,2})?""").find(all)?.value
  val titlePage=pages.firstOrNull{it.path.contains("TITLE_PAGE",true)}?.text.orEmpty()
  val lines=titlePage.lines().map{it.trim()}.filter{it.length>=2}
  val authorLine=lines.firstOrNull{Regex("""(?i)^(by|written by|edited by|translated by)\b""").containsMatchIn(it)}
  val author=authorLine?.replace(Regex("""(?i)^(by|written by|edited by|translated by)\s*"""),"")?.trim()
  val publisher=Regex("""(?im)^(published by|publisher)\s*[:\-]?\s*(.+)$""").find(all)?.groupValues?.getOrNull(2)?.trim()
  val edition=Regex("""(?i)\b(\d+(?:st|nd|rd|th)?\s+edition|revised edition|second edition|third edition|new edition)\b""").find(all)?.value
  val title=when{
   lines.isEmpty()->null
   else->lines.firstOrNull{!it.equals(authorLine,true) && !it.matches(Regex("""(?i)^(published by|publisher|isbn).*"""))}
  }
  return FreeExtractedBook(title,author,publisher,year,isbn,edition,pagesCount,null,price)
 }
}
