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
import kotlinx.coroutines.launch

private val LOCATIONS = listOf("CHILD", "GEN", "NALC", "NE", "RR", "RRRLF")
private val SOURCES = listOf("RRRLF", "State Central Library", "Donation")
private val TYPES = listOf("BOOKS", "BOOKLET", "MAPS")

@Composable
fun ProductionCatalogingScreenV2(initial: CatalogRecord? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cataloging_defaults", 0) }
    val tokenStore = remember { KohaTokenStore(context) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var record by remember { mutableStateOf(initial ?: CatalogRecord("")) }
    var location by remember { mutableStateOf(prefs.getString("location", "") ?: "") }
    var source by remember { mutableStateOf(prefs.getString("acquisition_source", "") ?: "") }
    var itemType by remember { mutableStateOf("BOOKS") }
    var copies by remember { mutableIntStateOf(1) }
    var purchasePrice by remember { mutableStateOf("") }
    var donorDetails by remember { mutableStateOf("") }
    var ddcVerified by remember { mutableStateOf(initial?.ddcEdition == "23") }
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
        Text("SDLM Cataloguing", style = MaterialTheme.typography.headlineSmall)
        Text("AACR2 • MARC21 • DDC 23 • LCSH", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator({ (step + 1) / 5f }, Modifier.fillMaxWidth())

        when (step) {
            0 -> {
                Text("1. Physical evidence", style = MaterialTheme.typography.titleLarge)
                Text("Required photographs: front cover, title page, copyright/publication page and ISBN/barcode page.")
                Text("Optional contents, preface/introduction and other relevant pages can be added later.")
                Button({ step = 1 }, Modifier.fillMaxWidth()) { Text("CONTINUE") }
            }
            1 -> {
                Text("2. Review extracted bibliographic data", style = MaterialTheme.typography.titleLarge)
                Field("Title proper", record.titleProper) { record = record.copy(titleProper = it) }
                Field("Other title information", record.otherTitleInformation.orEmpty()) { record = record.copy(otherTitleInformation = it.ifBlank { null }) }
                Field("Author / statement of responsibility", record.statementOfResponsibility.orEmpty()) {
                    record = record.copy(statementOfResponsibility = it, mainEntry = parsePerson(it))
                }
                Field("Edition statement", record.editionStatement.orEmpty()) { record = record.copy(editionStatement = it.ifBlank { null }) }
                Field("Publication place", record.publication.place.orEmpty()) { record = record.copy(publication = record.publication.copy(place = it.ifBlank { null })) }
                Field("Publisher", record.publication.publisher.orEmpty()) { record = record.copy(publication = record.publication.copy(publisher = it.ifBlank { null })) }
                Field("Publication date", record.publication.date.orEmpty()) { record = record.copy(publication = record.publication.copy(date = it.ifBlank { null })) }
                Field("Preliminary pages", record.physicalDescription.preliminaryPages.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(preliminaryPages = it.ifBlank { null })) }
                Field("Main pagination", record.physicalDescription.mainPages.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(mainPages = it.ifBlank { null })) }
                Field("Illustrations", record.physicalDescription.illustrations.joinToString("; ")) { record = record.copy(physicalDescription = record.physicalDescription.copy(illustrations = it.split(";").map(String::trim).filter(String::isNotBlank))) }
                Field("Dimensions", record.physicalDescription.dimensions.orEmpty()) { record = record.copy(physicalDescription = record.physicalDescription.copy(dimensions = it.ifBlank { null })) }
                Field("ISBN", record.isbns.firstOrNull().orEmpty()) { record = record.copy(isbns = it.split(",").map(String::trim).filter(String::isNotBlank)) }
                Field("Printed price", record.printedPrices.joinToString(", ")) { record = record.copy(printedPrices = it.split(",").map(String::trim).filter(String::isNotBlank)) }
                Field("Language", record.languages.firstOrNull()?.name.orEmpty()) { record = record.copy(languages = if (it.isBlank()) emptyList() else listOf(Language(it, languageCode(it)))) }
                Field("Series", record.series.orEmpty()) { record = record.copy(series = it.ifBlank { null }) }
                Field("Contents", record.contents.orEmpty()) { record = record.copy(contents = it.ifBlank { null }) }
                Field("Summary", record.summary.orEmpty()) { record = record.copy(summary = it.ifBlank { null }) }
                Field("Notes / warnings", record.notes.joinToString("; ")) { record = record.copy(notes = it.split(";").map(String::trim).filter(String::isNotBlank)) }
                Button({ step = 2 }, Modifier.fillMaxWidth()) { Text("CONTINUE TO DDC") }
            }
            2 -> {
                Text("3. AACR2 / DDC validation", style = MaterialTheme.typography.titleLarge)
                Field("Author surname", record.mainEntry?.surname.orEmpty()) { record = record.copy(mainEntry = Person(it, record.mainEntry?.forename)) }
                Field("Author forename", record.mainEntry?.forename.orEmpty()) { record = record.copy(mainEntry = Person(record.mainEntry?.surname.orEmpty(), it.ifBlank { null })) }
                Field("DDC 082 a", record.ddc.orEmpty()) {
                    record = record.copy(ddc = it.ifBlank { null }, ddcEdition = null, callNumber = null)
                    ddcVerified = false
                }
                Text("DDC edition: " + (record.ddcEdition ?: "Not verified"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ddcVerified, { ddcVerified = it })
                    Text("I verified the DDC number against a DDC 23 source.")
                }
                Text("Call number: " + (record.callNumber ?: "Will be generated after validation"))
                Field("Subjects (LCSH)", record.subjects.joinToString("; ")) { record = record.copy(subjects = it.split(";").map(String::trim).filter(String::isNotBlank)) }
                Button({
                    val n = Aacr2Normalizer.normalize(record)
                    record = n.record
                    message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                    if (n.valid && record.ddc != null && ddcVerified) step = 3
                }, Modifier.fillMaxWidth()) { Text("REVALIDATE") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            3 -> {
                Text("4. Copies and acquisition", style = MaterialTheme.typography.titleLarge)
                Text("The selected location and acquisition source are persistent app defaults for future items.")
                Text("Location", style = MaterialTheme.typography.titleMedium)
                LOCATIONS.forEach { value ->
                    OutlinedButton({ location = value; prefs.edit().putString("location", value).apply() }, Modifier.fillMaxWidth()) {
                        Text(if (location == value) "Selected: " + value else value)
                    }
                }
                Text("Acquisition source", style = MaterialTheme.typography.titleMedium)
                SOURCES.forEach { value ->
                    OutlinedButton({ source = value; prefs.edit().putString("acquisition_source", value).apply() }, Modifier.fillMaxWidth()) {
                        Text(if (source == value) "Selected: " + value else value)
                    }
                }
                Text("Item type", style = MaterialTheme.typography.titleMedium)
                TYPES.forEach { value ->
                    OutlinedButton({ itemType = value }, Modifier.fillMaxWidth()) {
                        Text(if (itemType == value) "Selected: " + value else value)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ copies = (copies - 1).coerceAtLeast(1) }) { Text("-") }
                    Text(copies.toString() + " copies", Modifier.padding(16.dp))
                    Button({ copies = (copies + 1).coerceAtMost(999) }) { Text("+") }
                }
                Field("Purchase price (optional)", purchasePrice) { purchasePrice = it }
                if (source == "Donation") Field("Donor details", donorDetails) { donorDetails = it }
                Button({
                    val n = Aacr2Normalizer.normalize(record)
                    record = n.record
                    message = n.issues.joinToString("\n") { it.message }.takeIf(String::isNotBlank)
                    if (n.valid && record.ddc != null && ddcVerified && location.isNotBlank() && source.isNotBlank() &&
                        (source != "Donation" || donorDetails.isNotBlank())) {
                        drafts = CopyDraftFactory(BarcodeSequence(context)).create(
                            record, copies, location, source, itemType, purchasePrice.toDoubleOrNull(),
                            donorDetails.takeIf { source == "Donation" }
                        )
                        approved = false
                        step = 4
                    }
                }, enabled = location.isNotBlank() && source.isNotBlank() && record.ddc != null && ddcVerified, Modifier.fillMaxWidth()) {
                    Text("FINAL REVIEW")
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            else -> {
                Text("5. Final confirmation", style = MaterialTheme.typography.titleLarge)
                Text("Title: " + record.titleProper)
                Text("Author: " + (record.statementOfResponsibility ?: "Not established"))
                Text("Publisher: " + (record.publication.publisher ?: "Not established"))
                Text("Publication: " + listOfNotNull(record.publication.place, record.publication.date).joinToString(" "))
                Text("ISBN: " + record.isbns.joinToString(", "))
                Text("DDC: " + record.ddc + " (edition " + record.ddcEdition + ")")
                Text("Call number: " + record.callNumber)
                Text("Location: " + location + " • Source: " + source + " • Type: " + itemType)
                drafts.forEach { Text("Copy " + it.copyNumber + ": barcode " + it.barcode) }
                HorizontalDivider()
                Field("Koha API token", token, {
                    token = it
                    tokenStore.save(it)
                }
                Text("The API token is encrypted with Android Keystore and is not displayed after leaving this screen.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(approved, { approved = it })
                    Text("I have reviewed and approve this record.")
                }
                message?.let { Text(it, color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
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
                }, enabled = approved && token.isNotBlank() && !busy && drafts.isNotEmpty(), Modifier.fillMaxWidth()) {
                    Text(if (biblioId == null) "CREATE IN KOHA" else "RETRY REMAINING ITEMS")
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit, password: Boolean = false) {
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

private fun languageCode(value: String): String = when (value.lowercase()) {
    "english", "eng" -> "eng"
    "hindi", "hin" -> "hin"
    "assamese", "asm" -> "asm"
    "bengali", "ben" -> "ben"
    "nepali", "nep" -> "nep"
    else -> value.lowercase().take(3)
}