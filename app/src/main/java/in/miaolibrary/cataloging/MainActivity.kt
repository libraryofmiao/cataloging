package in.miaolibrary.cataloging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import in.miaolibrary.cataloging.capture.*
import in.miaolibrary.cataloging.ui.ProductionCatalogingScreen
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent{MaterialTheme{Surface{
   var capture by remember{mutableStateOf(PhotoCaptureState())}
   var camera by remember{mutableStateOf(true)}
   var extracting by remember{mutableStateOf(false)}
   var record by remember{mutableStateOf<in.miaolibrary.cataloging.model.CatalogRecord?>(null)}
   if(camera) CameraCaptureScreen(capture,{type,path->capture=capture.add(type,path)},{camera=false;extracting=true})
   else if(extracting){
    LaunchedEffect(capture){
     lifecycleScope.launch{
      record=BookExtractionCoordinator().extract(capture)
      extracting=false
     }
    }
    CircularProgressIndicator()
   } else ProductionCatalogingScreen(initial=record)
  }}}
 }
}

