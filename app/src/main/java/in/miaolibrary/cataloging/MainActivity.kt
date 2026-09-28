package `in`.miaolibrary.cataloging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.miaolibrary.cataloging.capture.*
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.model.CatalogDraftStore
import `in`.miaolibrary.cataloging.ui.ProductionCatalogingScreenV2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val geminiEngine = remember { GeminiVisionEngine(this@MainActivity) }
            var showApiKeySetup by remember { mutableStateOf(!geminiEngine.hasApiKey()) }
            val photoPrefs = remember { getSharedPreferences("cataloging_photos", MODE_PRIVATE) }
            val draftStore = remember { CatalogDraftStore(this@MainActivity) }
            MaterialTheme(colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF2457D6),
                secondary = androidx.compose.ui.graphics.Color(0xFF5A6072),
                surface = androidx.compose.ui.graphics.Color(0xFFF7F8FC),
                surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEFF1F8)
            )) {
                Surface(Modifier.fillMaxSize()) {
                    if (showApiKeySetup) {
                        var input by remember { mutableStateOf("") }
                        var keyError by remember { mutableStateOf<String?>(null) }
                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Gemini API Key", style = MaterialTheme.typography.headlineSmall)
                            Text("Enter your Gemini API key once. It will be saved on this device and reused for future photograph extraction.")
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it; keyError = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API key") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = {
                                    runCatching { geminiEngine.saveApiKey(input) }
                                        .onSuccess { showApiKeySetup = false }
                                        .onFailure { keyError = it.message }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("SAVE & CONTINUE") }
                        }
                    } else {
                    var capture by remember { mutableStateOf(PhotoCaptureState.restore(photoPrefs)) }
                    var extracting by remember { mutableStateOf(false) }
                    var record by remember { mutableStateOf<CatalogRecord?>(draftStore.get()) }
                    var camera by remember { mutableStateOf(record == null) }
                    var error by remember { mutableStateOf<String?>(null) }
                    when {
                        camera -> CameraCaptureScreen(capture,
                            { type, path -> capture = capture.add(type, path).also { it.saveTo(photoPrefs) } },
                            { path -> capture = capture.addOptional(path).also { it.saveTo(photoPrefs) } },
                            { camera = false; extracting = true })
                        extracting -> {
                            LaunchedEffect(capture) {
                                runCatching { BookExtractionCoordinator(geminiEngine).extract(capture) }
                                    .onSuccess { record = it; draftStore.save(it) }
                                    .onFailure { error = it.message ?: it.toString() }
                                extracting = false
                            }
                            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    CircularProgressIndicator()
                                    Text("Reading book pages…", style = MaterialTheme.typography.titleMedium)
                                    Text("Analyzing…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        error != null -> Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Could not read the photographs", style = MaterialTheme.typography.headlineSmall)
                            Text(error!!)
                            Button(onClick = { error = null; camera = true; extracting = false }, Modifier.fillMaxWidth()) { Text("CAPTURE AGAIN") }
                        }
                        else -> ProductionCatalogingScreenV2(
                            initial = record,
                            onStartNewBook = {
                                draftStore.clear()
                                capture.clearSaved(photoPrefs)
                                capture = PhotoCaptureState()
                                record = null
                                error = null
                                camera = true
                                extracting = false
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}