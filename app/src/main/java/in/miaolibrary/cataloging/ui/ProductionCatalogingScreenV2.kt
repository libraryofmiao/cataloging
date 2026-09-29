package `in`.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import `in`.miaolibrary.cataloging.barcode.BarcodeSequence
import `in`.miaolibrary.cataloging.barcode.CopyDraftFactory
import `in`.miaolibrary.cataloging.catalog.Aacr2Normalizer
import `in`.miaolibrary.cataloging.koha.KohaApiClient
import `in`.miaolibrary.cataloging.koha.KohaSubmissionCoordinator
import `in`.miaolibrary.cataloging.koha.KohaTokenStore
import `in`.miaolibrary.cataloging.marc.MarcField
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CopyDraft
import `in`.miaolibrary.cataloging.model.Language
import `in`.miaolibrary.cataloging.model.Person
import `in`.miaolibrary.cataloging.marc.Marc21Builder
import kotlinx.coroutines.launch

private val LOCATIONS = listOf("CHILD", "GEN", "NALC", "NE", "RR", "RRRLF")
private val SOURCES = listOf("RRRLF", "State Central Library", "Donation")
private val TYPES = listOf("BOOKS", "BOOKLET", "MAPS")
private const val COPY_LIMIT = 200

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
    var subjectsVerified by remember { mutableStateOf(initial?.subjects?.isEmpty() != false) }
    var token by remember { mutableStateOf(tokenStore.get().orEmpty()) }
    var approved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var biblioId by remember { mutableStateOf<String?>(null) }
    var drafts by remember { mutableStateOf<List<CopyDraft>>(emptyList()) }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SDLM", style = MaterialTheme.typography.headlineSmall)
                        AssistChip(onClick = {}, label = { Text("CATALOGUING") })
                    }
                    LinearProgressIndicator(progress = { if (step == 0) 0f else step / 4f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message?.let {
                        Text(
                            it,
                            color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    when (step) {
                        0 -> Button({ onStartNewBook() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("CAPTURE PHOTOS") }
                        1 -> Button({ step = 2 }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("VALIDATE") }
                        2 -> Button({
                            val n = Aacr2Normalizer.normalize(record)
                            record = n.record
                            message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                            if (n.valid && record.ddc != null && subjectsVerified) step = 3
                        }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("REVALIDATE") }
                        3 -> Button({
                            val n = Aacr2Normalizer.normalize(record)
                            record = n.record
                            message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                            if (n.valid && record.ddc != null && location.isNotBlank() && source.isNotBlank() &&
                                (source != "Donation" || donorDetails.isNotBlank()) && purchasePrice.isNotBlank()) {
                                drafts = CopyDraftFactory(BarcodeSequence(context)).create(
                                    record, copies, location, source, itemType, parsePurchasePrice(purchasePrice),
                                    donorDetails.takeIf { source == "Donation" }
                                )
                                approved = false
                                step = 4
                            }
                        }, enabled = location.isNotBlank() && source.isNotBlank() && record.ddc != null && purchasePrice.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("FINAL REVIEW")
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (drafts.isEmpty() && biblioId != null && !busy) {
                                OutlinedButton(onClick = onStartNewBook, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
                                    } finally {
                                        busy = false
                                    }
                                }
                            }, enabled = approved && token.isNotBlank() && !busy && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (biblioId == null) "CREATE IN KOHA" else "RETRY REMAINING ITEMS")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (step >= 4) {
            val marcFields = remember(record) { Marc21Builder.build(record).fields }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Final review", style = MaterialTheme.typography.headlineSmall)
                    Text("AACR2 catalogue preview", style = MaterialTheme.typography.titleLarge)
                    Text("This is the catalogue-style preview of the MARC data that will be sent to Koha.", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                item {
                    Aacr2Preview(record)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("Koha MARC21 fields", style = MaterialTheme.typography.titleLarge)
                    Text("These are the MARC fields and subfields generated from the reviewed record.", style = MaterialTheme.typography.bodyMedium)
                }
                items(marcFields) { field -> MarcFieldCard(field) }
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("Item / copy preview", style = MaterialTheme.typography.titleLarge)
                    Text("Location: " + location)
                    Text("Acquisition source: " + source)
                    Text("Item type: " + itemType)
                }
                items(drafts, key = { it.barcode }) { draft ->
                    CopyDraftCard(
                        draft = draft,
                        onLocationChange = { value -> drafts = drafts.map { if (it.barcode == draft.barcode) it.copy(location = value) else it } },
                        onSourceChange = { value ->
                            drafts = drafts.map {
                                if (it.barcode == draft.barcode) it.copy(
                                    acquisitionSource = value,
                                    donorDetails = if (value == "Donation") donorDetails.takeIf { d -> d.isNotBlank() } else null
                                ) else it
                            }
                        },
                        onTypeChange = { value -> drafts = drafts.map { if (it.barcode == draft.barcode) it.copy(itemType = value) else it } }
                    )
                }
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    FieldV2("Koha API token", token, password = true, singleLine = true, imeAction = ImeAction.Done) {
                        token = it
                        tokenStore.save(it)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(approved, { approved = it })
                        Text("I have reviewed and approve this record.")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    0 -> {
                        Text("No catalogue data", style = MaterialTheme.typography.titleLarge)
                        Text("Capture the required pages to start a new book.")
                    }
                    1 -> {
                        Text("Review record", style = MaterialTheme.typography.titleLarge)
                        FieldV2("Title proper", record.titleProper) { record = record.copy(titleProper = it) }
                        FieldV2("Other title information", record.otherTitleInformation.orEmpty()) { record = record.copy(otherTitleInformation = it.ifBlank { null }) }
                        FieldV2("Author / statement of responsibility", record.statementOfResponsibility.orEmpty()) {
                            record = record.copy(statementOfResponsibility = it, mainEntry = parsePerson(it))
                        }
                        FieldV2("Edition statement", record.editionStatement.orEmpty(), singleLine = true) { record = record.copy(editionStatement = it.ifBlank { null }) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldV2("Publication place", record.publication.place.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(publication = record.publication.copy(place = it.ifBlank { null }))
                            }
                            FieldV2("Publication date", record.publication.date.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(publication = record.publication.copy(date = it.ifBlank { null }))
                            }
                        }
                        FieldV2("Publisher", record.publication.publisher.orEmpty(), singleLine = true) { record = record.copy(publication = record.publication.copy(publisher = it.ifBlank { null })) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldV2("Preliminary pages", record.physicalDescription.preliminaryPages.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(physicalDescription = record.physicalDescription.copy(preliminaryPages = it.ifBlank { null }))
                            }
                            FieldV2("Main pagination", record.physicalDescription.mainPages.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(physicalDescription = record.physicalDescription.copy(mainPages = it.ifBlank { null }))
                            }
                        }
                        FieldV2("Illustrations", record.physicalDescription.illustrations.joinToString("; ")) {
                            record = record.copy(physicalDescription = record.physicalDescription.copy(illustrations = it.split(";").map(String::trim).filter(String::isNotBlank)))
                        }
                        FieldV2("Dimensions", record.physicalDescription.dimensions.orEmpty(), singleLine = true) { record = record.copy(physicalDescription = record.physicalDescription.copy(dimensions = it.ifBlank { null })) }
                        FieldV2("ISBN", record.isbns.firstOrNull().orEmpty(), singleLine = true) { record = record.copy(isbns = it.split(",").map(String::trim).filter(String::isNotBlank)) }
                        FieldV2("Printed price *", purchasePrice, singleLine = true, keyboardType = KeyboardType.Decimal) {
                            purchasePrice = it
                            record = record.copy(printedPrices = if (it.isBlank()) emptyList() else listOf(it.trim()))
                        }
                        FieldV2("Languages (comma-separated)", record.languages.joinToString(", ") { it.name }, singleLine = true) { record = record.copy(languages = parseLanguages(it)) }
                        FieldV2("Series", record.series.orEmpty(), singleLine = true) { record = record.copy(series = it.ifBlank { null }) }
                        FieldV2("Contents", record.contents.orEmpty()) { record = record.copy(contents = it.ifBlank { null }) }
                        FieldV2("Summary", record.summary.orEmpty()) { record = record.copy(summary = it.ifBlank { null }) }
                        FieldV2("Notes / warnings", record.notes.joinToString("; ")) { record = record.copy(notes = it.split(";").map(String::trim).filter(String::isNotBlank)) }
                        FieldV2("Bibliography note (if applicable)", record.bibliographyNote.orEmpty()) { record = record.copy(bibliographyNote = it.ifBlank { null }) }
                    }
                    2 -> {
                        Text("AACR2 + DDC", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldV2("Author surname", record.mainEntry?.surname.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(mainEntry = Person(it, record.mainEntry?.forename))
                            }
                            FieldV2("Author forename", record.mainEntry?.forename.orEmpty(), singleLine = true, modifier = Modifier.weight(1f)) {
                                record = record.copy(mainEntry = Person(record.mainEntry?.surname.orEmpty(), it.ifBlank { null }))
                            }
                        }
                        FieldV2("DDC 082 a", record.ddc.orEmpty(), singleLine = true) {
                            record = record.copy(ddc = it.ifBlank { null }, ddcEdition = null, callNumber = null)
                        }
                        Text("Call number: " + (record.callNumber ?: "Will be generated after validation"))
                        val ddcSources = record.evidence?.ddcCandidates.orEmpty()
                        if (ddcSources.isEmpty()) {
                            Text("No DDC number was fetched from the configured OPACs/sites.")
                        } else {
                            Text("Fetched DDC numbers — tap to use:", style = MaterialTheme.typography.titleMedium)
                            ddcSources.take(20).forEach { candidate ->
                                val edition = candidate.edition?.trim()?.takeIf { it.isNotBlank() }?.let { " (edition $it)" } ?: ""
                                OutlinedCard(
                                    onClick = { record = record.copy(ddc = candidate.number.trim(), ddcEdition = candidate.edition, callNumber = null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(candidate.number.trim() + edition, style = MaterialTheme.typography.bodyLarge)
                                        Text(candidate.source, style = MaterialTheme.typography.bodySmall)
                                    }
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(onClick = { copies = (copies - 1).coerceAtLeast(1) }, modifier = Modifier.size(48.dp)) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(copies.toString() + " copies", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { copies = (copies + 1).coerceAtMost(COPY_LIMIT) }, modifier = Modifier.size(48.dp)) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        if (source == "Donation") FieldV2("Donor details", donorDetails, singleLine = true) { donorDetails = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarcFieldCard(field: MarcField) {
    val rendered = if (field.subfields.isNotEmpty()) {
        field.subfields.joinToString(" ") { sf -> "\$" + sf.first + " " + sf.second }
    } else {
        field.value.orEmpty()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(field.tag + " " + field.ind1 + field.ind2, style = MaterialTheme.typography.labelLarge)
            Text(rendered, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CopyDraftCard(
    draft: CopyDraft,
    onLocationChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onTypeChange: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Copy " + draft.copyNumber + " • barcode " + draft.barcode, style = MaterialTheme.typography.titleMedium)
            ChoiceMenu("Location", draft.location, LOCATIONS, onLocationChange)
            ChoiceMenu("Acquisition source", draft.acquisitionSource, SOURCES, onSourceChange)
            ChoiceMenu("Item type", draft.itemType, TYPES, onTypeChange)
        }
    }
}

@Composable
private fun ChoiceMenu(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(value) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
private fun FieldV2(
    label: String,
    value: String,
    password: Boolean = false,
    singleLine: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        )
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

private fun parsePurchasePrice(raw: String): Double? = Regex("""(?i)(\d+(?:[.,]\d{1,2})?)""").find(raw)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()

private fun languageCode(value: String): String = when (value.lowercase()) {
    "english", "eng" -> "eng"
    "hindi", "hin" -> "hin"
    "assamese", "asm" -> "asm"
    "bengali", "ben" -> "ben"
    "nepali", "nep" -> "nep"
    else -> value.lowercase().take(3)
}

@Composable
private fun Aacr2Preview(record: CatalogRecord) {
    val r = record
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Aacr2Line("100", r.mainEntry?.let(::formatPerson))
        Aacr2Line(
            "245",
            buildString {
                append(if (r.mainEntry != null) "1" else "0")
                append(nonFilingIndicator(r.titleProper))
                append("  ")
                append(r.titleProper.trim())
                r.otherTitleInformation?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append(" : ")
                    append(it)
                }
                r.statementOfResponsibility?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append(" / ")
                    append(it)
                }
            }
        )
        Aacr2Line("250", r.editionStatement?.trim()?.takeIf { it.isNotBlank() }?.let { it.removeSuffix(".") + "." })
        Aacr2Line(
            "264",
            listOfNotNull(
                r.publication.place?.trim()?.takeIf { it.isNotBlank() },
                r.publication.publisher?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(" : ").takeIf { it.isNotBlank() }?.let { base ->
                val date = r.publication.date?.trim()?.takeIf { it.isNotBlank() }
                if (date != null) "$base, $date." else "$base."
            }
        )
        Aacr2Line(
            "300",
            listOfNotNull(
                listOfNotNull(r.physicalDescription.preliminaryPages, r.physicalDescription.mainPages)
                    .joinToString(", ").takeIf { it.isNotBlank() },
                r.physicalDescription.illustrations.joinToString(", ").takeIf { it.isNotBlank() }?.let { ": $it" },
                r.physicalDescription.dimensions?.trim()?.takeIf { it.isNotBlank() }?.let { "; $it" }
            ).joinToString("").takeIf { it.isNotBlank() }?.let { "$it." }
        )
        Aacr2Line("020", r.isbns.firstOrNull()?.let { isbn ->
            buildString {
                append(isbn.trim())
                r.printedPrices.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append(" : $it")
                }
                append(".")
            }
        })
        Aacr2Line("490", r.series?.trim()?.takeIf { it.isNotBlank() }?.let { "$it." })
        Aacr2Line("500", r.notes.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { "$it." })
        Aacr2Line("504", r.bibliographyNote?.trim()?.takeIf { it.isNotBlank() }?.let { "$it." })
        Aacr2Line("505", r.contents?.trim()?.takeIf { it.isNotBlank() }?.let { "$it." })
        Aacr2Line("520", r.summary?.trim()?.takeIf { it.isNotBlank() }?.let { "$it." })
        r.subjects.forEach { subject ->
            Aacr2Line("650", subject.trim().takeIf { it.isNotBlank() }?.let { "$it." })
        }
        Aacr2Line("082", r.ddc?.trim()?.takeIf { it.isNotBlank() })
        Aacr2Line("942", r.callNumber?.trim()?.takeIf { it.isNotBlank() }?.let { "Call number: $it" })
    }
}

@Composable
private fun Aacr2Line(tag: String, value: String?) {
    if (value.isNullOrBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(42.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

private fun formatPerson(person: Person): String =
    listOfNotNull(person.surname.trim().takeIf { it.isNotBlank() }, person.forename?.trim()?.takeIf { it.isNotBlank() })
        .joinToString(", ") + "."

private fun nonFilingIndicator(title: String): Int {
    val t = title.trimStart().lowercase()
    return when {
        t.startsWith("the ") -> 4
        t.startsWith("an ") -> 3
        t.startsWith("a ") -> 2
        else -> 0
    }
}
