package `in`.miaolibrary.cataloging.capture

import `in`.miaolibrary.cataloging.ddc.DdcSourceClient
import `in`.miaolibrary.cataloging.ddc.LibraryOfCongressDdcClient
import `in`.miaolibrary.cataloging.match.DdcMatcher
import `in`.miaolibrary.cataloging.match.OpenLibraryClient
import `in`.miaolibrary.cataloging.model.*

class BookExtractionCoordinator(
    private val vision: VisionEngine,
    private val ddcSources: DdcSourceClient = DdcSourceClient(),
    private val locDdc: LibraryOfCongressDdcClient = LibraryOfCongressDdcClient(),
    private val openLibrary: OpenLibraryClient = OpenLibraryClient()
) {
    suspend fun extract(state: PhotoCaptureState): CatalogRecord {
        check(state.isComplete()) { "All four required photographs are required" }
        val r = vision.extract(state.photos.values.toList())

        val authorPerson = parsePerson(r.author)
        val isbn = r.isbn?.trim()?.takeIf { it.isNotBlank() }
        val language = r.language?.trim()?.takeIf { it.isNotBlank() }

        val base = CatalogRecord(
            titleProper = r.title.orEmpty(),
            statementOfResponsibility = r.author,
            mainEntry = authorPerson,
            editionStatement = r.edition,
            publication = Publication(r.publicationPlace, r.publisher, r.publicationDate),
            physicalDescription = PhysicalDescription(
                r.preliminaryPages, r.pages,
                r.illustrations?.let(::listOf) ?: emptyList(),
                r.dimensions
            ),
            isbns = isbn?.let(::listOf) ?: emptyList(),
            printedPrices = r.printedPrice?.let(::listOf) ?: emptyList(),
            series = r.series,
            contents = r.contents,
            summary = r.summary,
            languages = language?.let { listOf(Language(it, languageCode(it))) } ?: emptyList(),
            notes = r.physicalWarnings?.let(::listOf) ?: emptyList()
        )

        val candidates = runCatching {
            (ddcSources.find(isbn, base.titleProper, r.author) + locDdc.find(isbn, base.titleProper, r.author) + openLibrary.findVerifiedDdc(isbn, base.titleProper, r.author))
                .distinctBy { "${it.number}|${it.edition}|${it.source}" }
        }.getOrDefault(emptyList())

        val verified = DdcMatcher.choose(candidates.filter { it.edition == "23" })
        return if (verified != null) {
            base.copy(ddc = verified.number, ddcEdition = "23")
        } else {
            base
        }
    }

    private fun parsePerson(raw: String?): Person? {
        val value = raw?.trim()?.replace(Regex("\\s+"), " ")?.trim(' ', '.', ',') ?: return null
        if (value.isBlank()) return null
        return if (value.contains(",")) {
            val parts = value.split(",", limit = 2)
            Person(parts[0].trim(), parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() })
        } else {
            val parts = value.split(" ")
            if (parts.size == 1) Person(parts[0])
            else Person(parts.last(), parts.dropLast(1).joinToString(" "))
        }
    }

    private fun languageCode(value: String): String {
        return when (value.lowercase()) {
            "english", "eng" -> "eng"
            "hindi", "hin" -> "hin"
            "assamese", "asm" -> "asm"
            "bengali", "ben" -> "ben"
            "nepali", "nep" -> "nep"
            else -> value.lowercase().take(3)
        }
    }
}
