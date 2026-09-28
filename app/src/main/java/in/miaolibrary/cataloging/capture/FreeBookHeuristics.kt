package `in`.miaolibrary.cataloging.capture

data class FreeExtractedBook(
 val title:String?=null,val author:String?=null,val publisher:String?=null,
 val publicationDate:String?=null,val isbn:String?=null,val edition:String?=null,
 val pages:String?=null,val language:String?=null,val printedPrice:String?=null
)

class FreeBookHeuristics {
    fun extract(pages:List<OcrPage>):FreeExtractedBook {
        val all = pages.joinToString("\n") { it.text }
        val titlePage = pages.firstOrNull { it.path.contains("TITLE_PAGE",true) }?.text.orEmpty()
        val publicationPage = pages.firstOrNull { it.path.contains("PUBLICATION_PAGE",true) }?.text.orEmpty()
        val isbnPage = pages.firstOrNull { it.path.contains("ISBN_PAGE",true) }?.text.orEmpty()
        val cover = pages.firstOrNull { it.path.contains("FRONT_COVER",true) }?.text.orEmpty()
        val isbn = findIsbn("$isbnPage\n$all")
        val year = Regex("""\b(18|19|20)\d{2}\b""").find(publicationPage.ifBlank { all })?.value
        val pagesCount = Regex("""(?i)(?:pages?|p\.)\s*[:.]?\s*(\d{1,4})\b""").find(all)?.groupValues?.getOrNull(1)
        val price = Regex("""(?i)(?:₹|rs\.?|inr|\$|£|€)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?""").find("$isbnPage\n$publicationPage\n$all")?.value
        val edition = Regex("""(?i)\b(?:\d+(?:st|nd|rd|th)\s+edition|revised edition|second edition|third edition|new edition)\b""").find(all)?.value
        val lines = titlePage.lines().map { it.trim() }.filter { it.length >= 2 }
        val authorLine = lines.firstOrNull { Regex("""(?i)^(by|written by|edited by|translated by)\b""").containsMatchIn(it) }
        val author = authorLine?.replace(Regex("""(?i)^(by|written by|edited by|translated by)\s*"""),"")?.trim()
        val publisher = Regex("""(?im)^(?:published by|publisher)\s*[:\-]?\s*(.+)$""").find("$publicationPage\n$all")?.groupValues?.getOrNull(1)?.trim()
        val publicationPlace = Regex("""(?im)^(?:place of publication|published at)\s*[:\-]?\s*(.+)$""").find(publicationPage)?.groupValues?.getOrNull(1)?.trim()
        val titleCandidates = lines.filterNot { it.equals(authorLine,true) }
            .filterNot { Regex("""(?i)^(isbn|published by|publisher|copyright|edition)\b""").containsMatchIn(it) }
            .sortedByDescending { it.length }
        val title = titleCandidates.firstOrNull() ?: cover.lines().map { it.trim() }.filter { it.length >= 3 }.maxByOrNull { it.length }
        val language = detectLanguage(all)
        val warnings = buildList {
            if (title == null) add("Title not confidently detected from OCR.")
            if (author == null) add("Author/statement of responsibility not confidently detected.")
            if (isbn == null) add("ISBN not confidently detected.")
            if (publisher == null) add("Publisher not confidently detected.")
        }.joinToString("; ").takeIf { it.isNotBlank() }
        return FreeExtractedBook(title,author,publisher,year,isbn,edition,pagesCount,language,price,publicationPlace,physicalWarnings=warnings)
    }
    private fun findIsbn(text:String):String? {
        val raw = Regex("""(?i)\b(?:isbn(?:[- ]?1[03])?\s*[:.]?\s*)?((?:97[89][ -]?)?\d[\d -]{8,15}[\dx])\b""").find(text)?.groupValues?.getOrNull(1) ?: return null
        val compact = raw.replace(Regex("[^0-9Xx]"),"").uppercase()
        return compact.takeIf { it.length == 10 || it.length == 13 }
    }
    private fun detectLanguage(text:String):String? {
        val lower=text.lowercase()
        return when {
            text.any { it in '\u0900'..'\u097F' } || listOf("hindi","हिंदी").any(lower::contains) -> "Hindi"
            listOf("assamese","অসমীয়া").any(lower::contains) -> "Assamese"
            listOf("bengali","বাংলা").any(lower::contains) -> "Bengali"
            listOf("nepali","नेपाली").any(lower::contains) -> "Nepali"
            lower.contains("english") -> "English"
            else -> null
        }
    }
}
