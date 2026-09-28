package in.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import in.miaolibrary.cataloging.model.CatalogRecord

private val LOCATIONS = listOf("CHILD", "GEN", "NALC", "NE", "RR", "RRRLF")
private val SOURCES = listOf("RRRLF", "State Central Library", "Donation")
private val TYPES = listOf("BOOKS", "BOOKLET", "MAPS")

@Composable
fun ProductionCatalogingScreen(initial: CatalogRecord? = null, onSubmit: (CatalogRecord) -> Unit = {}) {
    var step by remember { mutableIntStateOf(0) }
    var record by remember { mutableStateOf(initial ?: CatalogRecord("")) }
    var location by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BOOKS") }
    var copies by remember { mutableIntStateOf(1) }
    var confirmed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Miao Library Cataloging", style = MaterialTheme.typography.headlineSmall)
        Text("SDLM | AACR2 + MARC21 + DDC 23 + LCSH")
        LinearProgressIndicator(progress = { ((step + 1) / 5f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        when (step) {
            0 -> {
                Text("1. Physical evidence", style = MaterialTheme.typography.titleLarge)
                Text("Capture: front cover, title page, copyright/publication page, ISBN/barcode page.")
                Text("Optional: contents, preface/introduction and other relevant pages.")
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("CONTINUE") }
            }
            1 -> {
                Text("2. Evidence extraction", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = record.titleProper, onValueChange = { record = record.copy(titleProper = it) }, label = { Text("Title proper") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = record.statementOfResponsibility.orEmpty(), onValueChange = { record = record.copy(statementOfResponsibility = it) }, label = { Text("Statement of responsibility") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = record.editionStatement.orEmpty(), onValueChange = { record = record.copy(editionStatement = it) }, label = { Text("Edition statement") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("CONTINUE TO REVIEW") }
            }
            2 -> {
                Text("3. AACR2 / MARC21 review", style = MaterialTheme.typography.titleLarge)
                Text("All edits must be normalized and validated again before approval.")
                OutlinedTextField(value = record.titleProper, onValueChange = { record = record.copy(titleProper = it) }, label = { Text("245 subfield a Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = record.ddc.orEmpty(), onValueChange = { record = record.copy(ddc = it) }, label = { Text("082 subfield a DDC") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("REVALIDATE") }
            }
            3 -> {
                Text("4. Copies", style = MaterialTheme.typography.titleLarge)
                Text("One bibliographic record, separate Koha item for each physical copy.")
                Text("Location")
                LOCATIONS.forEach { value -> OutlinedButton(onClick = { location = value }, modifier = Modifier.fillMaxWidth()) { Text(if (location == value) "Selected: $value" else value) } }
                Text("Acquisition source")
                SOURCES.forEach { value -> OutlinedButton(onClick = { source = value }, modifier = Modifier.fillMaxWidth()) { Text(if (source == value) "Selected: $value" else value) } }
                Text("Item type")
                TYPES.forEach { value -> OutlinedButton(onClick = { type = value }, modifier = Modifier.fillMaxWidth()) { Text(if (type == value) "Selected: $value" else value) } }
                Row {
                    Button(onClick = { copies = (copies - 1).coerceAtLeast(1) }) { Text("-") }
                    Text("$copies copies", modifier = Modifier.padding(16.dp))
                    Button(onClick = { copies++ }) { Text("+") }
                }
                Button(onClick = { step = 4 }, enabled = location.isNotBlank() && source.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("FINAL REVIEW") }
            }
            else -> {
                Text("5. Final confirmation", style = MaterialTheme.typography.titleLarge)
                Text("Title: ${record.titleProper}")
                Text("DDC: ${record.ddc ?: "Not assigned"}")
                Text("Copies: $copies")
                Text("Location: $location")
                Text("Acquisition source: $source")
                Text("Item type: $type")
                Row {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("I have reviewed and approve this record.")
                }
                Button(onClick = { onSubmit(record) }, enabled = confirmed, modifier = Modifier.fillMaxWidth()) { Text("CREATE IN KOHA") }
            }
        }
    }
}
