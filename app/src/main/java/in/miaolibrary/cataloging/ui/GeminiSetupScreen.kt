package in.miaolibrary.cataloging.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import in.miaolibrary.cataloging.capture.GeminiCredentialStore

@Composable
fun GeminiSetupScreen(context: Context,onReady:()->Unit){
 var key by remember{mutableStateOf("")}
 var saving by remember{mutableStateOf(false)}
 Column(
  Modifier.fillMaxSize().padding(24.dp),
  verticalArrangement=Arrangement.spacedBy(16.dp)
 ){
  Text("Gemini Cataloguing",style=MaterialTheme.typography.headlineSmall)
  Text("Gemini will examine the four book photographs and propose bibliographic facts for human review.")
  OutlinedTextField(
   value=key,
   onValueChange={key=it},
   label={Text("Gemini API key")},
   singleLine=true,
   visualTransformation=PasswordVisualTransformation(),
   modifier=Modifier.fillMaxWidth()
  )
  Text(
   "The key is stored on this device and is not included in the source code.",
   style=MaterialTheme.typography.bodySmall
  )
  Button(
   onClick={
    saving=true
    GeminiCredentialStore(context).set(key)
    saving=false
    onReady()
   },
   enabled=key.isNotBlank() && !saving,
   modifier=Modifier.fillMaxWidth()
  ){
   Text(if(saving) "SAVING…" else "SAVE & START")
  }
 }
}
