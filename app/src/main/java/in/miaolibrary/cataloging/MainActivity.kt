package in.miaolibrary.cataloging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import in.miaolibrary.cataloging.capture.*
import in.miaolibrary.cataloging.ui.GeminiSetupScreen
import in.miaolibrary.cataloging.ui.ProductionCatalogingScreen
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent{
   MaterialTheme{
    Surface(Modifier.fillMaxSize()){
     val store=remember{GeminiCredentialStore(this@MainActivity)}
     var configured by remember{mutableStateOf(store.get().isNotBlank())}
     var capture by remember{mutableStateOf(PhotoCaptureState())}
     var camera by remember{mutableStateOf(configured)}
     var extracting by remember{mutableStateOf(false)}
     var record by remember{mutableStateOf<in.miaolibrary.cataloging.model.CatalogRecord?>(null)}
     var error by remember{mutableStateOf<String?>(null)}

     when{
      !configured->GeminiSetupScreen(this@MainActivity){configured=store.get().isNotBlank();camera=true}
      camera->CameraCaptureScreen(capture,{type,path->capture=capture.add(type,path)},{camera=false;extracting=true})
      extracting->{
       LaunchedEffect(capture){
        runCatching{
         record=BookExtractionCoordinator(GeminiVisionEngine(store.get())).extract(capture)
        }.onFailure{error=it.message?:it.toString()}
        extracting=false
       }
       Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
         CircularProgressIndicator()
         Text("Gemini is examining the book photographs…")
        }
       }
      }
      error!=null->Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(androidx.compose.ui.unit.dp(16f))){
       Text("Gemini extraction failed",style=MaterialTheme.typography.headlineSmall)
       Text(error!!)
       Button({error=null;camera=true;capture=PhotoCaptureState()}){Text("CAPTURE AGAIN")}
      }
      else->ProductionCatalogingScreen(initial=record)
     }
    }
   }
  }
 }
}
