package in.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import in.miaolibrary.cataloging.model.CatalogRecord

private val LOCATIONS=listOf("CHILD","GEN","NALC","NE","RR","RRRLF")
private val SOURCES=listOf("RRRLF","State Central Library","Donation")
private val TYPES=listOf("BOOKS","BOOKLET","MAPS")

@Composable
fun ProductionCatalogingScreen(initial:CatalogRecord?=null,onSubmit:(CatalogRecord)->Unit={}) {
 var step by remember{mutableIntStateOf(0)}
 var record by remember{mutableStateOf(initial?:CatalogRecord(""))}
 var location by remember{mutableStateOf("")}
 var source by remember{mutableStateOf("")}
 var type by remember{mutableStateOf("BOOKS")}
 var copies by remember{mutableIntStateOf(1)}
 var confirmed by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Miao Library Cataloging",style=MaterialTheme.typography.headlineSmall)
  Text("SDLM | AACR2 + MARC21 + DDC 23 + LCSH")
  LinearProgressIndicator({(step+1)/5f},Modifier.fillMaxWidth())
  when(step){
   0->{Text("1. Physical evidence",style=MaterialTheme.typography.titleLarge);Text("Capture: front cover, title page, copyright/publication page, ISBN/barcode page.");Text("Optional: contents, preface/introduction and other relevant pages.");Button({step=1},Modifier.fillMaxWidth()){Text("CONTINUE")}}
   1->{Text("2. Evidence extraction",style=MaterialTheme.typography.titleLarge);OutlinedTextField(record.titleProper,{record=record.copy(titleProper=it)},label={Text("Title proper")},Modifier.fillMaxWidth());OutlinedTextField(record.statementOfResponsibility.orEmpty(),{record=record.copy(statementOfResponsibility=it)},label={Text("Statement of responsibility")},Modifier.fillMaxWidth());OutlinedTextField(record.editionStatement.orEmpty(),{record=record.copy(editionStatement=it)},label={Text("Edition statement")},Modifier.fillMaxWidth());Button({step=2},Modifier.fillMaxWidth()){Text("CONTINUE TO REVIEW")}}
   2->{Text("3. AACR2 / MARC21 review",style=MaterialTheme.typography.titleLarge);Text("All edits must be normalized and validated again before approval.");OutlinedTextField(record.titleProper,{record=record.copy(titleProper=it)},label={Text("245 $a Title")},Modifier.fillMaxWidth());OutlinedTextField(record.ddc.orEmpty(),{record=record.copy(ddc=it)},label={Text("082 $a DDC")},Modifier.fillMaxWidth());Button({step=3},Modifier.fillMaxWidth()){Text("REVALIDATE")}}
   3->{Text("4. Copies",style=MaterialTheme.typography.titleLarge);Text("One bibliographic record, separate Koha item for each physical copy.");Text("Location");LOCATIONS.forEach{OutlinedButton({location=it},Modifier.fillMaxWidth()){Text(if(location==it)"Selected: "+it else it)}};Text("Acquisition source");SOURCES.forEach{OutlinedButton({source=it},Modifier.fillMaxWidth()){Text(if(source==it)"Selected: "+it else it)}};Text("Item type");TYPES.forEach{OutlinedButton({type=it},Modifier.fillMaxWidth()){Text(if(type==it)"Selected: "+it else it)}};Row{Button({copies=(copies-1).coerceAtLeast(1)}){Text("-")};Text(copies.toString()+" copies",Modifier.padding(16.dp));Button({copies++}){Text("+")}};Button(location.isNotBlank()&&source.isNotBlank(),{step=4},Modifier.fillMaxWidth()){Text("FINAL REVIEW")}}
   else->{Text("5. Final confirmation",style=MaterialTheme.typography.titleLarge);Text("Title: "+record.titleProper);Text("DDC: "+(record.ddc?:"Not assigned"));Text("Copies: "+copies.toString());Text("Location: "+location);Text("Acquisition source: "+source);Text("Item type: "+type);Row{Checkbox(confirmed,{confirmed=it});Text("I have reviewed and approve this record.")};Button(confirmed,{onSubmit(record)},Modifier.fillMaxWidth()){Text("CREATE IN KOHA")}}
  }
 }
}
