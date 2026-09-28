package `in`.miaolibrary.cataloging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.miaolibrary.cataloging.capture.*
import `in`.miaolibrary.cataloging.model.CatalogRecord
import `in`.miaolibrary.cataloging.ui.ProductionCatalogingScreenV2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF2457D6),
                secondary = androidx.compose.ui.graphics.Color(0xFF5A6072),
                surface = androidx.compose.ui.graphics.Color(0xFFF7F8FC),
                surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEFF1F8)
            )) {
                Surface(Modifier.fillMaxSize()) {
                    var capture by remember { mutableStateOf(PhotoCaptureState()) }
                    var camera by remember { mutableStateOf(true) }
                    var extracting by remember { mutableStateOf(false) }
                    var record by remember { mutableStateOf<CatalogRecord?>(null) }
                    var error by remember { mutableStateOf<String?>(null) }
                    when {
                        camera -> CameraCaptureScreen(capture,
                            { type, path -> capture = capture.add(type, path) },
                            { camera = false; extracting = true })
                        extracting -> {
                            LaunchedEffect(capture) {
                                runCatching { BookExtractionCoordinator(GeminiVisionEngine()).extract(capture) }
                                    .onSuccess { record = it }
                                    .onFailure { error = it.message ?: it.toString() }
                                extracting = false
                            }
                            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    CircularProgressIndicator()
                                    Text("Reading book pages…", style = MaterialTheme.typography.titleMedium)
                                    Text("Gemini 2.5 Flash • physical-book evidence extraction", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        error != null -> Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Could not read the photographs", style = MaterialTheme.typography.headlineSmall)
                            Text(error!!)
                            Button(onClick = { error = null; camera = true; capture = PhotoCaptureState() }, Modifier.fillMaxWidth()) { Text("CAPTURE AGAIN") }
                        }
                        else -> ProductionCatalogingScreenV2(initial = record)
                    }
                }
            }
        }
    }
}