package in.miaolibrary.cataloging.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CatalogingScreen(vm:CatalogingViewModel){
 val state=vm.state.value
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text("Miao Library Cataloging",style=MaterialTheme.typography.headlineSmall)
  Text("SDLM • AACR2 + MARC21 + DDC 23 + LCSH")
  LinearProgressIndicator(progress={(state.ordinal+1f)/ReviewState.entries.size},modifier=Modifier.fillMaxWidth())
  Text("Stage: ${state.name.replace('_',' ')}")
  when(state){
   ReviewState.CAPTURE->Text("Capture front cover, title page, copyright/publication page and ISBN/barcode page.")
   ReviewState.EXTRACT->Text("Vision extraction will produce evidence-backed bibliographic values.")
   ReviewState.MATCH->Text("External catalogues will be compared for edition identity and DDC.")
   ReviewState.REVIEW->Text("Review and edit the proposed AACR2-normalized record.")
   ReviewState.CONFIRM->Text("Final confirmation is required before anything is written to Koha.")
   ReviewState.COMPLETE->Text("Koha creation completed.")
  }
  Spacer(Modifier.weight(1f))
  Button(onClick={}){Text("Continue")}
 }
}
