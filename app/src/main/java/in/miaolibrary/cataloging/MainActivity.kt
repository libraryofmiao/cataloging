package in.miaolibrary.cataloging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import in.miaolibrary.cataloging.capture.CameraCaptureScreen
import in.miaolibrary.cataloging.capture.PhotoCaptureState
import in.miaolibrary.cataloging.ui.ProductionCatalogingScreen

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent{MaterialTheme{Surface{
   var capture by remember{mutableStateOf(PhotoCaptureState())}
   var camera by remember{mutableStateOf(true)}
   if(camera) CameraCaptureScreen(capture,{type,path->capture=capture.add(type,path)},{camera=false})
   else ProductionCatalogingScreen()
  }}}
 }
}
