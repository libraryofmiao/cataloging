package `in`.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import `in`.miaolibrary.cataloging.barcode.BarcodeSequence
import `in`.miaolibrary.cataloging.barcode.CopyDraftFactory
import `in`.miaolibrary.cataloging.catalog.Aacr2Normalizer
import `in`.miaolibrary.cataloging.koha.KohaApiClient
import `in`.miaolibrary.cataloging.koha.KohaSubmissionCoordinator
import `in`.miaolibrary.cataloging.koha.KohaTokenStore
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CopyDraft
import `in`.miaolibrary.cataloging.model.Language
import `in`.miaolibrary.cataloging.model.Person
import `in`.miaolibrary.cataloging.marc.Marc21Builder
import kotlinx.coroutines.launch

private val LOCATIONS = listOf("CHILD", "GEN", "NALC", "NE", "RR", "RRRLF")
private val SOURCES = listOf("RRRLF", "State Central Library", "Donation")
private val TYPES = listOf("BOOKS", "BOOKLET", "MAPS")

@Composable
fun ProductionCatalogingScreenV2(initial: CatalogRecord? = null, onStartNewBook: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cataloging_defaults", 0) }
    val tokenStore = remember { KohaTokenStore(context) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(if (initial != null) 1 else 0) }
    var record by remember { mutableStateOf(initial ?: CatalogRecord("")) }
    var location by remember { mutableStateOf(prefs.getString("location", "") ?: "") }
    var source by remember { mutableStateOf(prefs.getString("acquisition_source", "") ?: "") }
    var itemType by remember { mutableStateOf("BOOKS") }
    var copies by remember { mutableIntStateOf(1) }
    var purchasePrice by remember { mutableStateOf("") }
    var donorDetails by remember { mutableStateOf("") }
    var ddcVerified by remember { mutableStateOf(initial?.ddcEdition == "23") }
    var subjectsVerified by remember { mutableStateOf(initial?.subjects?.isEmpty() != false) }
    var token by remember { mutableStateOf(tokenStore.get().orEmpty()) }
    var approved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var biblioId by remember { mutableStateOf<String?>(null) }
    var drafts by remember { mutableStateOf<List<CopyDraft>>(emptyList()) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SDLM", style = MaterialTheme.typography.headlineSmall)
            AssistChip(onClick = {}, label = { Text("CATALOGUING") })
        }
        LinearProgressIndicator({ if (step == 0) 0f else step / 4f }, Modifier.fillMaxWidth())

        when (step) {
            0 -> {
                Text("No catalogue data", style = MaterialTheme.typography.titleLarge)
                Button({ onStartNewBook() }, Modifier.fillMaxWidth()) { Text("CAPTURE PHOTOS") }
            }
            1 -> {
                Text("Review record", style = MaterialTheme.typography.titleLarge)
                FieldV2("Title proper", record.titleProper) { record = record.copy(titleProper = it) }
                FieldV2("Other title information", record.otherTitleInformation.orEmpty()) { record = record.copy(otherTitleInformation = it.ifBlank { null }) }
                FieldV2("Author / statement of responsibility", record.statementOfResponsibility.orEmpty()) {
                    record = record.copy(statementOfResponsibility = it, mainEntry = parsePerson(it))
                }
                FieldV2("Edition statement", record.editionStatement.orEmpty()) { record = record.copy(editionStatement = it.ifBlank { null }) }
                FieldV2("Publication place", record.publication.place.orEmpty()) { record = record.copy(publication = record.publication.copy(place = it.ifBlank { null })) }
                FieldV2("Publisher", record.publication.publisher.orEmpty()) { record = record.copy(publication = record.publication.copy(publisher = it.ifBlank { null })) }
                FieldV2("Publication date", record.publication.date.orEmpty()) { record = record.copy(publication = record.publication.copy(date = it.ifBlank { null })) }
                FieldV2("Preliminary pages", record.physicalDescription.preliminaryPages.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(preliminaryPages = it.ifBlank { null })) }
                FieldV2("Main pagination", record.physicalDescription.mainPages.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(mainPages = it.ifBlank { null })) }
                FieldV2("Illustrations", record.physicalDescription.illustrations.joinToString("; ")) { record = record.copy(physicalDescription = record.physicalDescription.copy(illustrations = it.split(";").map(String::trim).filter(String::isNotBlank))) }
                FieldV2("Dimensions", record.physicalDescription.dimensions.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(dimensions = it.ifBlank { null })) }
                FieldV2("ISBN", record.isbns.firstOrNull().orEmpty()) { record = record.copy(isbns = it.split(",").map(String::trim).filter(String::isNotBlank)) }
                FieldV2("Printed price *", purchasePrice) { purchasePrice = it; record = record.copy(printedPrices = if (it.isBlank()) emptyList() else listOf(it.trim())) }
                FieldV2("Languages (comma-separated)", record.languages.joinToString(", ") { it.name }) { record = record.copy(languages = parseLanguages(it)) }
                FieldV2("Series", record.series.orEmpty()) { record = record.copy(series = it.ifBlank { null }) }
                FieldV2("Contents", record.contents.orEmpty()) { record = record.copy(contents = it.ifBlank { null }) }
                FieldV2("Summary", record.summary.orEmpty()) { record = record.copy(summary = it.ifBlank { null }) }
                FieldV2("Notes / warnings", record.notes.joinToString("; ")) { record = record.copy(notes = it.split(";").map(String::trim).filter(String::isNotBlank)) }
                FieldV2("Bibliography note (if applicable)", record.bibliographyNote.orEmpty()) { record = record.copy(bibliographyNote = it.ifBlank { null }) }
                Button({ step = 2 }, Modifier.fillMaxWidth()) { Text("VALIDATE") }
            }
            2 -> {
                Text("AACR2 + DDC 23", style = MaterialTheme.typography.titleLarge)
                FieldV2("Author surname", record.mainEntry?.surname.orEmpty()) { record = record.copy(mainEntry = Person(it, record.mainEntry?.forename)) }
                FieldV2("Author forename", record.mainEntry?.forename.orEmpty()) { record = record.copy(mainEntry = Person(record.mainEntry?.surname.orEmpty(), it.ifBlank { null })) }
                FieldV2("DDC 082 a", record.ddc.orEmpty()) {
                    record = record.copy(ddc = it.ifBlank { null }, ddcEdition = null, callNumber = null)
                    ddcVerified = false
                }
                Text("DDC edition: " + (record.ddcEdition ?: "Not verified"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ddcVerified, { ddcVerified = it })
                    Text("I verified the DDC number against a DDC 23 source.")
                }
                Text("Call number: " + (record.callNumber ?: "Will be generated after validation"))
                val ddcSources = record.evidence?.ddcCandidates.orEmpty().filter { it.edition?.trim() == "23" }
                val ddcNumbers = ddcSources.map { it.number.trim().removeSuffix(".") }.distinct()
                when {
                    ddcSources.isEmpty() -> {
                        Text(
                            "No exact DDC 23 source result. Enter a DDC 23 number only after catalogue review.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ddcNumbers.size > 1 -> {
                        Text(
                            "DDC 23 disagreement — sources returned different numbers. Do not auto-select; review the evidence and enter the final number manually.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text("DDC 23 evidence sources:", style = MaterialTheme.typography.titleMedium)
                        ddcSources.take(10).forEach {
                            Text(it.number + " — " + it.source)
                        }
                    }
                    else -> {
                        Text("DDC 23 evidence sources:", style = MaterialTheme.typography.titleMedium)
                        ddcSources.take(10).forEach {
                            Text(it.number + " — " + it.source)
                        }
                    }
                }
                FieldV2("Subjects (LCSH — librarian validated)", record.subjects.joinToString("; ")) {
                    record = record.copy(subjects = it.split(";").map(String::trim).filter(String::isNotBlank))
                    subjectsVerified = it.split(";").map(String::trim).any(String::isNotBlank).not()
                }
                if (record.subjects.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(subjectsVerified, { subjectsVerified = it })
                        Text("I verified these as valid Library of Congress Subject Headings.")
                    }
                }
                Button({
                    val n = Aacr2Normalizer.normalize(record)
                    record = n.record
                    message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                    if (n.valid && record.ddc != null && ddcVerified && subjectsVerified) step = 3
                }, Modifier.fillMaxWidth()) { Text("REVALIDATE") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            3 -> {
                Text("Copies", style = MaterialTheme.typography.titleLarge)
                ChoiceMenu("Location", location.ifBlank { "Select" }, LOCATIONS) {
                    location = it
                    prefs.edit().putString("location", it).apply()
                }
                ChoiceMenu("Acquisition source", source.ifBlank { "Select" }, SOURCES) {
                    source = it
                    prefs.edit().putString("acquisition_source", it).apply()
                }
                ChoiceMenu("Item type", itemType, TYPES) { itemType = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ copies = (copies - 1).coerceAtLeast(1) }) { Text("-") }
                    Text(copies.toString() + " copies", Modifier.padding(16.dp))
                    Button({ copies = (copies + 1).coerceAtMost(999) }) { Text("+") }
                }
                if (source == "Donation") FieldV2("Donor details", donorDetails) { donorDetails = it }
                Button({
                    val n = Aacr2Normalizer.normalize(record)
                    record = n.record
                    message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                    if (n.valid && record.ddc != null && ddcVerified && location.isNotBlank() && source.isNotBlank() &&
                        (source != "Donation" || donorDetails.isNotBlank()) && purchasePrice.isNotBlank()) {
                        drafts = CopyDraftFactory(BarcodeSequence(context)).create(
                            record, copies, location, source, itemType, parsePurchasePrice(purchasePrice),
                            donorDetails.takeIf { source == "Donation" }
                        )
                        approved = false
                        step = 4
                    }
                }, enabled = location.isNotBlank() && source.isNotBlank() && record.ddc != null && ddcVerified && purchasePrice.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("FINAL REVIEW")
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            else -> {
                Text("Final approval", style = MaterialTheme.typography.titleLarge)
                Text("Title: " + record.titleProper)
                Text("Author: " + (record.statementOfResponsibility ?: "Not established"))
                Text("Publisher: " + (record.publication.publisher ?: "Not established"))
                Text("Publication: " + listOfNotNull(record.publication.place, record.publication.date).joinToString(" "))
                Text("ISBN: " + record.isbns.joinToString(", "))
                Text("DDC: " + record.ddc + " (edition " + record.ddcEdition + ")")
                Text("Call number: " + record.callNumber)
                Text("Location: " + location + " • Source: " + source + " • Type: " + itemType)
                Text("MARC21 preview", style = MaterialTheme.typography.titleMedium)
                Marc21Builder.build(record).fields.forEach { field ->
                    val rendered = if (field.subfields.isNotEmpty()) field.subfields.joinToString(" ") { sf -> "$" + sf.first + " " + sf.second } else field.value.orEmpty()
                    Text(field.tag + " " + field.ind1 + field.ind2 + " " + rendered, style = MaterialTheme.typography.bodySmall)
                }
                drafts.forEachIndexed { index, draft ->
                    Text("Copy " + draft.copyNumber + " • barcode " + draft.barcode, style = MaterialTheme.typography.titleMedium)
                    ChoiceMenu("Location", draft.location, LOCATIONS) { value ->
                        drafts = drafts.toMutableList().also { list -> list[index] = draft.copy(location = value) }
                    }
                    ChoiceMenu("Acquisition source", draft.acquisitionSource, SOURCES) { value ->
                        drafts = drafts.toMutableList().also { list -> list[index] = draft.copy(acquisitionSource = value, donorDetails = if (value == "Donation") donorDetails.takeIf { it.isNotBlank() } else null) }
                    }
                    ChoiceMenu("Item type", draft.itemType, TYPES) { value ->
                        drafts = drafts.toMutableList().also { list -> list[index] = draft.copy(itemType = value) }
                    }
                    if (index < drafts.lastIndex) HorizontalDivider()
                }
                HorizontalDivider()
                FieldV2("Koha API token", token, true) {
                    token = it
                    tokenStore.save(it)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(approved, { approved = it })
                    Text("I have reviewed and approve this record.")
                }
                message?.let { Text(it, color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
                if (drafts.isEmpty() && biblioId != null && !busy) {
                    Button(onClick = onStartNewBook, modifier = Modifier.fillMaxWidth()) {
                        Text("START NEW BOOK")
                    }
                }

                Button({
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            tokenStore.save(token)
                            val result = KohaSubmissionCoordinator(KohaApiClient { tokenStore.get() })
                                .submit(record, drafts, biblioId)
                            biblioId = result.biblioId
                            val created = result.createdBarcodes.toSet()
                            drafts = drafts.filterNot { created.contains(it.barcode) }
                            message = if (result.failedBarcode == null)
                                "Success: biblio " + result.biblioId + " and " + result.createdBarcodes.size + " item(s) created."
                            else
                                "Biblio " + result.biblioId + " created. Item " + result.failedBarcode + " failed. Retry will continue with the remaining item(s)."
                        } catch (t: Throwable) {
                            message = t.message ?: t.toString()
                        } finally { busy = false }
                    }
                }, enabled = approved && token.isNotBlank() && !busy && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (biblioId == null) "CREATE IN KOHA" else "RETRY REMAINING ITEMS")
                }
            }
        }
    }
}


@Composable
private fun ChoiceMenu(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(value) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldV2(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
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

private fun parseLanguages(raw: String): List<Language> = raw.split(",").map(String::trim).filter(String::isNotBlank).map { Language(it, languageCode(it)) }.distinctBy { it.code }

private fun parsePurchasePrice(raw: String): Double? = Regex("""(?i)(\\d+(?:[.,]\\d{1,2})?)""").find(raw)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()

private fun languageCode(value: String): String = when (value.lowercase()) {
    "english", "eng" -> "eng"
    "hindi", "hin" -> "hin"
    "assamese", "asm" -> "asm"
    "bengali", "ben" -> "ben"
    "nepali", "nep" -> "nep"
    else -> value.lowercase().take(3)
}