package `in`.miaolibrary.cataloging.capture

import `in`.miaolibrary.cataloging.ddc.DdcSourceClient
import `in`.miaolibrary.cataloging.ddc.LibraryOfCongressDdcClient
import `in`.miaolibrary.cataloging.match.OpenLibraryClient
import `in`.miaolibrary.cataloging.model.*

class BookExtractionCoordinator(
    private val vision: VisionEngine,
    private val ddcSources: DdcSourceClient = DdcSourceClient(),
    private val locDdc: LibraryOfCongressDdcClient = LibraryOfCongressDdcClient(),
    private val openLibrary: OpenLibraryClient = OpenLibraryClient()
) {
    suspend fun extract(state: PhotoCaptureState): CatalogRecord {
        check(state.allPhotos().isNotEmpty()) { "Add at least one photograph" }
        val r = vision.extract(state.allPhotos())

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

        val identityMatches = runCatching { openLibrary.findIdentityMatches(isbn, base.titleProper, r.author) }.getOrDefault(emptyList())
        val candidates = runCatching {
            val a = ddcSources.find(isbn, base.titleProper, r.author)
            val b = locDdc.find(isbn, base.titleProper, r.author)
            val c = openLibrary.findVerifiedDdc(isbn, base.titleProper, r.author)
            (a + b + c).distinctBy { it.number + "|" + it.edition + "|" + it.source }
        }.getOrDefault(emptyList())

        // DDC is evidence only: retain every number fetched from the OPACs/sites.\n        val evidenceFields = mutableMapOf<String, List<EvidenceValue>>()
        fun addEvidence(field: String, value: String?) {
            if (!value.isNullOrBlank()) {
                evidenceFields[field] = listOf(EvidenceValue(value, EvidenceSource.PHYSICAL, 0.90, true))
            }
        }
        addEvidence("245$a", r.title)
        addEvidence("245$c", r.author)
        addEvidence("264$a", r.publicationPlace)
        addEvidence("264$b", r.publisher)
        addEvidence("264$c", r.publicationDate)
        addEvidence("020$a", r.isbn)
        addEvidence("020$c", r.printedPrice)
        identityMatches.firstOrNull()?.let { match ->
            if (!match.title.isNullOrBlank()) evidenceFields["external.title"] = listOf(EvidenceValue(match.title, EvidenceSource.EXTERNAL_CATALOGUE, match.confidence, false))
            if (!match.author.isNullOrBlank()) evidenceFields["external.author"] = listOf(EvidenceValue(match.author, EvidenceSource.EXTERNAL_CATALOGUE, match.confidence, false))
            if (!match.isbn.isNullOrBlank()) evidenceFields["external.isbn"] = listOf(EvidenceValue(match.isbn, EvidenceSource.EXTERNAL_CATALOGUE, match.confidence, false))
        }

        val evidence = BookEvidence(
            photos = state.allPhotos(),
            fields = evidenceFields,
            identityMatches = identityMatches,
            ddcCandidates = candidates
        )
        val withEvidence = base.copy(evidence = evidence)
        return withEvidence
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
