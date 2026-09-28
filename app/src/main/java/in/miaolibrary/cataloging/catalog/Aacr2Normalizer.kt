package `in`.miaolibrary.cataloging.catalog

import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.Person

data class ValidationIssue(val field: String, val message: String, val blocking: Boolean = true)
data class NormalizationResult(val record: CatalogRecord, val issues: List<ValidationIssue>) {
    val valid: Boolean get() = issues.none { it.blocking }
}

object Aacr2Normalizer {
    private val supportedLanguages = setOf("eng", "hin", "asm", "ben", "nep")
    private val ddcPattern = Regex("""^\d{3}(\.\d+)?$""")

    fun normalize(input: CatalogRecord): NormalizationResult {
        val issues = mutableListOf<ValidationIssue>()
        val title = input.titleProper.trim().replace(Regex("\\s+"), " ")
        if (title.isBlank()) issues += ValidationIssue("245\$a", "Title proper is required.")

        val responsibility = input.statementOfResponsibility?.trim()
            ?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }

        input.languages.forEach {
            if (it.code !in supportedLanguages) {
                issues += ValidationIssue("041", "Unsupported language code: " + it.code + ".")
            }
        }

        val normalizedIsbns = input.isbns.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        normalizedIsbns.forEach {
            if (!validIsbn(it)) issues += ValidationIssue("020\$a", "Invalid ISBN: " + it + ".")
        }

        val ddc = input.ddc?.trim()?.takeIf { it.isNotBlank() }
        if (ddc != null && !ddcPattern.matches(ddc)) {
            issues += ValidationIssue("082\$a", "DDC must be a valid numeric class number.")
        }

        val main = input.mainEntry?.let {
            val surname = it.surname.trim().replace(Regex("\\s+"), " ")
            val forename = it.forename?.trim()?.replace(Regex("\\s+"), " ")
                ?.takeIf { value -> value.isNotBlank() }
            if (surname.isBlank()) null else Person(surname, forename)
        }

        if (responsibility != null && main == null) {
            issues += ValidationIssue("100", "A personal statement of responsibility requires a usable main-entry name.")
        }

        val callNumber = if (ddc != null && main != null) {
            ddc + " " + main.surname.take(3).uppercase()
        } else null

        val normalizedNotes = input.notes.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        return NormalizationResult(
            input.copy(
                titleProper = title,
                statementOfResponsibility = responsibility,
                mainEntry = main,
                isbns = normalizedIsbns,
                notes = normalizedNotes,
                callNumber = callNumber
            ),
            issues
        )
    }

    private fun validIsbn(raw: String): Boolean {
        val s = raw.replace("-", "").replace(" ", "")
        return when (s.length) {
            10 -> s.mapIndexed { i, c ->
                (if (c.equals('X', true)) 10 else c.digitToIntOrNull() ?: -99) * (10 - i)
            }.sum() % 11 == 0
            13 -> s.all(Char::isDigit) &&
                s.mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum() % 10 == 0
            else -> false
        }
    }
}
