package in.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import in.miaolibrary.cataloging.catalog.Aacr2Normalizer
import in.miaolibrary.cataloging.model.CatalogRecord
import in.miaolibrary.cataloging.model.Person
import in.miaolibrary.cataloging.model.Language

private val LOCATIONS = listOf("CHILD", "GEN", "NALC", "NE", "RR", "RRRLF")
private val SOURCES = listOf("RRRLF", "State Central Library", "Donation")
private val TYPES = listOf("BOOKS", "BOOKLET", "MAPS")

@Composable
fun ProductionCatalogingScreen(initial: CatalogRecord? = null, onSubmit: (CatalogRecord) -> Unit = {}) {
    var step by remember { mutableIntStateOf(0) }
    var record by remember { mutableStateOf(initial ?: CatalogRecord("")) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cataloging_defaults", 0) }
    var location by remember { mutableStateOf(prefs.getString("location", "") ?: "") }
    var source by remember { mutableStateOf(prefs.getString("acquisition_source", "") ?: "") }
    var type by remember { mutableStateOf("BOOKS") }
    var copies by remember { mutableIntStateOf(1) }
    var confirmed by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var ddcVerifiedByCataloger by remember { mutableStateOf(initial?.ddcEdition == "23") }

    Column(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SDLM Cataloguing", style = MaterialTheme.typography.headlineSmall)
                Text("AACR2 • MARC21 • DDC 23 • LCSH", style = MaterialTheme.typography.bodyMedium)
                Text("Evidence first • Human approval before Koha", style = MaterialTheme.typography.bodySmall)
            }
        }
        LinearProgressIndicator(
            progress = { ((step + 1) / 5f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )

        when (step) {
            0 -> {
                Text("1. Physical evidence", style = MaterialTheme.typography.titleLarge)
                Text("Required: front cover, title page, copyright/publication page, ISBN/barcode page.")
                Text("Optional: contents, preface/introduction and other relevant pages.")
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("CONTINUE") }
            }

            1 -> {
                Text("2. Evidence extraction", style = MaterialTheme.typography.titleLarge)
                Text("Review the information extracted from the photographs. Physical evidence takes priority.")
                Field("Title proper", record.titleProper) { record = record.copy(titleProper = it) }
                Field("Other title information", record.otherTitleInformation.orEmpty()) {
                    record = record.copy(otherTitleInformation = it.ifBlank { null })
                }
                Field("Author / statement of responsibility", record.statementOfResponsibility.orEmpty()) {
                    val person = parsePerson(it)
                    record = record.copy(statementOfResponsibility = it, mainEntry = person)
                }
                Field("Edition statement", record.editionStatement.orEmpty()) {
                    record = record.copy(editionStatement = it.ifBlank { null })
                }
                Field("Publication place", record.publication.place.orEmpty()) {
                    record = record.copy(publication = record.publication.copy(place = it.ifBlank { null }))
                }
                Field("Publisher", record.publication.publisher.orEmpty()) {
                    record = record.copy(publication = record.publication.copy(publisher = it.ifBlank { null }))
                }
                Field("Publication date", record.publication.date.orEmpty()) {
                    record = record.copy(publication = record.publication.copy(date = it.ifBlank { null }))
                }
                Field("Preliminary pages", record.physicalDescription.preliminaryPages.orEmpty()) {
                    record = record.copy(physicalDescription = record.physicalDescription.copy(preliminaryPages = it.ifBlank { null }))
                }
                Field("Main pagination", record.physicalDescription.mainPages.orEmpty()) {
                    record = record.copy(physicalDescription = record.physicalDescription.copy(mainPages = it.ifBlank { null }))
                }
                Field("Illustrations", record.physicalDescription.illustrations.joinToString("; ")) {
                    record = record.copy(physicalDescription = record.physicalDescription.copy(
                        illustrations = it.split(";").map(String::trim).filter(String::isNotBlank)
                    ))
                }
                Field("Dimensions", record.physicalDescription.dimensions.orEmpty()) {
                    record = record.copy(physicalDescription = record.physicalDescription.copy(dimensions = it.ifBlank { null }))
                }
                Field("ISBN", record.isbns.firstOrNull().orEmpty()) {
                    record = record.copy(isbns = it.split(",").map(String::trim).filter(String::isNotBlank))
                }
                Field("Printed price", record.printedPrices.joinToString(", ")) {
                    record = record.copy(printedPrices = it.split(",").map(String::trim).filter(String::isNotBlank))
                }
                Field("Language", record.languages.firstOrNull()?.name.orEmpty()) {
                    val code = languageCode(it)
                    record = record.copy(languages = if (it.isBlank()) emptyList() else listOf(Language(it, code)))
                }
                Field("Series", record.series.orEmpty()) { record = record.copy(series = it.ifBlank { null }) }
                Field("Contents", record.contents.orEmpty()) { record = record.copy(contents = it.ifBlank { null }) }
                Field("Summary", record.summary.orEmpty()) { record = record.copy(summary = it.ifBlank { null }) }
                Field("Notes / warnings", record.notes.joinToString("; ")) {
                    record = record.copy(notes = it.split(";").map(String::trim).filter(String::isNotBlank))
                }
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("CONTINUE TO DDC / REVIEW") }
            }

            2 -> {
                Text("3. AACR2 / MARC21 + DDC review", style = MaterialTheme.typography.titleLarge)
                Text("DDC is populated only from a verified DDC 23 source. AI does not invent a DDC number.")
                Field("Author surname", record.mainEntry?.surname.orEmpty()) {
                    record = record.copy(mainEntry = Person(it, record.mainEntry?.forename))
                }
                Field("Author forename", record.mainEntry?.forename.orEmpty()) {
                    record = record.copy(mainEntry = Person(record.mainEntry?.surname.orEmpty(), it.ifBlank { null }))
                }
                Field("DDC 082 \$a", record.ddc.orEmpty()) {
                    record = record.copy(ddc = it.ifBlank { null }, ddcEdition = null, callNumber = null); ddcVerifiedByCataloger = false
                }
                Text("DDC edition: ${record.ddcEdition ?: "Not verified"}")
                Checkbox(checked = ddcVerifiedByCataloger, onCheckedChange = { ddcVerifiedByCataloger = it })
                Text("I verified the DDC number against a DDC 23 source.")
                Text("Call number: ${record.callNumber ?: "Will be generated from DDC + author surname"}")
                Field("Subjects (LCSH)", record.subjects.joinToString("; ")) {
                    record = record.copy(subjects = it.split(";").map(String::trim).filter(String::isNotBlank))
                }
                Text("All manual edits must be normalized and validated again before final approval.")
                Button(onClick = { val n = Aacr2Normalizer.normalize(record); record = n.record; validationMessage = n.issues.joinToString("\n") { it.message }.takeIf { it.isNotBlank() }; if (n.valid && record.ddc != null && ddcVerifiedByCataloger) step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("REVALIDATE") }
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            3 -> {
                Text("4. Copies", style = MaterialTheme.typography.titleLarge)
                Text("One bibliographic record, separate Koha item for each physical copy.")
                Text("Location", style = MaterialTheme.typography.titleMedium)
                LOCATIONS.forEach { value ->
                    OutlinedButton(onClick = { location = value; prefs.edit().putString("location", value).apply() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (location == value) "Selected: $value" else value)
                    }
                }
                Text("Acquisition source", style = MaterialTheme.typography.titleMedium)
                SOURCES.forEach { value ->
                    OutlinedButton(onClick = { source = value; prefs.edit().putString("acquisition_source", value).apply() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (source == value) "Selected: $value" else value)
                    }
                }
                Text("Item type", style = MaterialTheme.typography.titleMedium)
                TYPES.forEach { value ->
                    OutlinedButton(onClick = { type = value }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (type == value) "Selected: $value" else value)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { copies = (copies - 1).coerceAtLeast(1) }) { Text("-") }
                    Text("$copies copies", modifier = Modifier.padding(16.dp))
                    Button(onClick = { copies++ }) { Text("+") }
                }
                Button(
                    onClick = { val n = Aacr2Normalizer.normalize(record); record = n.record; validationMessage = n.issues.joinToString("\n") { it.message }.takeIf { it.isNotBlank() }; if (n.valid && record.ddc != null && ddcVerifiedByCataloger && location.isNotBlank() && source.isNotBlank()) step = 4 },
                    enabled = location.isNotBlank() && source.isNotBlank() && record.ddc != null && ddcVerifiedByCataloger,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("FINAL REVIEW") }
            }

            else -> {
                Text("5. Final confirmation", style = MaterialTheme.typography.titleLarge)
                Text("Title: ${record.titleProper}")
                Text("Author: ${record.statementOfResponsibility ?: "Not established"}")
                Text("Publisher: ${record.publication.publisher ?: "Not established"}")
                Text("Publication: ${record.publication.place ?: ""} ${record.publication.date ?: ""}".trim())
                Text("ISBN: ${record.isbns.joinToString(", ")}")
                Text("DDC: ${record.ddc ?: "Not assigned / not verified"}")
                Text("Call number: ${record.callNumber ?: "Not generated"}")
                Text("Copies: $copies")
                Text("Location: $location")
                Text("Acquisition source: $source")
                Text("Item type: $type")
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("I have reviewed and approve this record.", modifier = Modifier.padding(top = 12.dp))
                }
                Button(
                    onClick = { onSubmit(record) },
                    enabled = confirmed,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("CREATE IN KOHA") }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun parsePerson(raw: String): Person? {
    val value = raw.trim().replace(Regex("\\s+"), " ").trim(' ', '.', ',')
    if (value.isBlank()) return null
    return if (value.contains(",")) {
        val p = value.split(",", limit = 2)
        Person(p[0].trim(), p.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() })
    } else {
        val p = value.split(" ")
        if (p.size == 1) Person(p[0]) else Person(p.last(), p.dropLast(1).joinToString(" "))
    }
}

private fun languageCode(value: String): String = when (value.lowercase()) {
    "english", "eng" -> "eng"
    "hindi", "hin" -> "hin"
    "assamese", "asm" -> "asm"
    "bengali", "ben" -> "ben"
    "nepali", "nep" -> "nep"
    else -> value.lowercase().take(3)
}
