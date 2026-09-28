package in.miaolibrary.cataloging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Context
import in.miaolibrary.cataloging.capture.GeminiCredentialStore

@Composable
fun GeminiSetupScreen(context: Context,onReady:()->Unit){
 var key by remember{mutableStateOf("")}
 var saved by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text("Gemini Cataloguing",style=MaterialTheme.typography.headlineSmall)
  Text("Gemini will examine the four book photographs and propose bibliographic facts for human review.")
  OutlinedTextField(key,{key=it},label={Text("Gemini API key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  Text("The key is stored encrypted on this device and is not included in the source code.",style=MaterialTheme.typography.bodySmall)
  Button(onClick={GeminiCredentialStore(context).set(key);saved=true},enabled=key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("SAVE KEY")}
  if(saved) Text("Gemini is configured. You can start cataloguing.",color=MaterialTheme.colorScheme.primary)
  Button(onClick=onReady,enabled=saved||key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("START CATALOGUING")}
 }
}
