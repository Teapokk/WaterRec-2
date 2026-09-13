package com.waterrec.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Estúdio", "Configurações", "Transmissão")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF38BDF8)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) },
                    icon = {
                        val icon = when (index) {
                            0 -> Icons.Default.Videocam
                            1 -> Icons.Default.Settings
                            else -> Icons.Default.Stream
                        }
                        Icon(icon, contentDescription = title)
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF000000))
                    )
                )
        ) {
            when (selectedTabIndex) {
                0 -> StudioTab()
                1 -> SettingsTab()
                2 -> StreamingTab()
            }
        }
    }
}

@Composable
fun StudioTab() {
    val context = LocalContext.current
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // Since we don't persist these globally yet, we just start with defaults for the intent
    var resolution = "1080p"
    var fps = 60
    var bitrate = 5000000
    var recordAudio = true

    val screenRecordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val width = if (resolution == "1080p") 1080 else 720
            val height = if (resolution == "1080p") 1920 else 1280
            
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = "START_RECORDING"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
                putExtra("WIDTH", width)
                putExtra("HEIGHT", height)
                putExtra("FPS", fps)
                putExtra("BITRATE", bitrate)
                putExtra("RECORD_AUDIO", recordAudio)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            screenRecordLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WaterRec Studio",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Você não está mais excluído. Grave como um profissional.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                    return@Button
                }

                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    
                    // Note: Quick workaround for asking both permissions, ideally we'd use RequestMultiplePermissions
                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    return@Button
                }

                screenRecordLauncher.launch(projectionManager.createScreenCaptureIntent())
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("INICIAR GRAVAÇÃO", fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsTab() {
    val scrollState = rememberScrollState()
    val bubbleShape by BubbleConfig.shape.collectAsState()
    val bubbleSize by BubbleConfig.size.collectAsState()
    val imageUri by BubbleConfig.imageUri.collectAsState()

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            BubbleConfig.setImageUri(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)
    ) {
        Text("Personalização da Bola", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escolher Imagem para a Bola")
                }
                
                if (imageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.CenterHorizontally)
                    )
                    TextButton(onClick = { BubbleConfig.setImageUri(null) }) {
                        Text("Remover Imagem", color = Color(0xFFEF4444))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Tamanho (${bubbleSize}dp)", color = Color.LightGray)
                Slider(
                    value = bubbleSize.toFloat(),
                    onValueChange = { BubbleConfig.setSize(it.toInt()) },
                    valueRange = 40f..120f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Qualidade (Padrão)", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("As configurações de gravação são aplicadas no próximo início.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StreamingTab() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Stream, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Transmissão ao Vivo (RTMP)",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Autenticação e transmissão direta para Twitch e YouTube via RTMP exigem integração de um SDK de broadcasting avançado.\n\nPor enquanto, grave localmente como um profissional utilizando nossa Facecam e o menu animado!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Stream Key (Em Breve)") },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Autenticar (Em Breve)")
        }
    }
}
