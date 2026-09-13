package com.waterrec.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BubbleShape { CIRCLE, SQUARE, TRIANGLE }

object BubbleConfig {
    private val _shape = MutableStateFlow(BubbleShape.CIRCLE)
    val shape: StateFlow<BubbleShape> = _shape.asStateFlow()

    private val _size = MutableStateFlow(60) // dp
    val size: StateFlow<Int> = _size.asStateFlow()

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _showWebcam = MutableStateFlow(false)
    val showWebcam: StateFlow<Boolean> = _showWebcam.asStateFlow()

    private val _recordKeyboard = MutableStateFlow(false)
    val recordKeyboard: StateFlow<Boolean> = _recordKeyboard.asStateFlow()

    private val _recordBubble = MutableStateFlow(true)
    val recordBubble: StateFlow<Boolean> = _recordBubble.asStateFlow()

    fun setShape(s: BubbleShape) { _shape.value = s }
    fun setSize(s: Int) { _size.value = s }
    fun setImageUri(uri: Uri?) { _imageUri.value = uri }
    fun setWebcam(show: Boolean) { _showWebcam.value = show }
    fun setRecordKeyboard(record: Boolean) { _recordKeyboard.value = record }
    fun setRecordBubble(record: Boolean) { _recordBubble.value = record }
}

class FloatingBubbleService : Service(), androidx.lifecycle.LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private var webcamComposeView: ComposeView? = null
    
    private lateinit var recorderManager: ScreenRecorderManager

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        recorderManager = ScreenRecorderManager(this)
        
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(1, notification)

        scope.launch {
            BubbleConfig.showWebcam.collect { show ->
                if (show) {
                    showWebcamView()
                } else {
                    removeWebcamView()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_RECORDING") {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data: Intent? = intent.getParcelableExtra("DATA")
            val width = intent.getIntExtra("WIDTH", 720)
            val height = intent.getIntExtra("HEIGHT", 1280)
            val fps = intent.getIntExtra("FPS", 30)
            val bitrate = intent.getIntExtra("BITRATE", 5000000)
            val recordAudio = intent.getBooleanExtra("RECORD_AUDIO", false)
            
            if (data != null && !recorderManager.isRecording) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = projectionManager.getMediaProjection(resultCode, data)
                if (projection != null) {
                    recorderManager.startRecording(projection, width, height, fps, bitrate, recordAudio)
                    showFloatingBubble()
                }
            }
        } else if (intent?.action == "STOP_SERVICE") {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingBubble() {
        if (this::composeView.isInitialized) return
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            
            setContent {
                MaterialTheme {
                    BubbleUI()
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false // Let compose handle click
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX > 10 || diffY > 10) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(composeView, params)
                    }
                    false
                }
                else -> false
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun showWebcamView() {
        if (webcamComposeView != null) return
        webcamComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setContent {
                WebcamUI()
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 50
            y = 100
        }

        var camInitialX = 0
        var camInitialY = 0
        var camInitialTouchX = 0f
        var camInitialTouchY = 0f

        webcamComposeView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    camInitialX = params.x
                    camInitialY = params.y
                    camInitialTouchX = event.rawX
                    camInitialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = camInitialX - (event.rawX - camInitialTouchX).toInt() // Gravity is END
                    params.y = camInitialY + (event.rawY - camInitialTouchY).toInt()
                    windowManager.updateViewLayout(webcamComposeView, params)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(webcamComposeView, params)
    }

    private fun removeWebcamView() {
        webcamComposeView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        webcamComposeView = null
    }

    @Composable
    fun WebcamUI() {
        val context = LocalContext.current
        val lifecycleOwner = this@FloatingBubbleService
        
        Box(modifier = Modifier
            .size(120.dp, 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview
                            )
                        } catch (exc: Exception) {
                            // Ignored
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    @Composable
    fun BubbleUI() {
        val shape by BubbleConfig.shape.collectAsState()
        val size by BubbleConfig.size.collectAsState()
        val imageUri by BubbleConfig.imageUri.collectAsState()
        
        var isExpanded by remember { mutableStateOf(false) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Main Bubble
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Custom Bubble",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (shape == BubbleShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
                    )
                } else {
                    val color = if (recorderManager.isPaused) Color(0xFFF59E0B) else Color(0xFFEF4444)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = this.size.width
                        val h = this.size.height
                        when (shape) {
                            BubbleShape.CIRCLE -> {
                                drawCircle(color = color, radius = w / 2f)
                            }
                            BubbleShape.SQUARE -> {
                                drawRect(color = color)
                            }
                            BubbleShape.TRIANGLE -> {
                                val path = Path().apply {
                                    moveTo(w / 2f, 0f)
                                    lineTo(w, h)
                                    lineTo(0f, h)
                                    close()
                                }
                                drawPath(path = path, color = color)
                            }
                        }
                    }
                    Text(text = if (recorderManager.isPaused) "II" else "REC", color = Color.White)
                }
            }

            // Dropdown Menu (The "pop-up que cai do céu")
            AnimatedVisibility(
                visible = isExpanded,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.padding(top = 8.dp).width(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    contentColor = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Menu de Gravação", style = MaterialTheme.typography.titleSmall, color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Gravar Teclado Toggle
                        val recordKeyboard by BubbleConfig.recordKeyboard.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Teclado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = recordKeyboard, onCheckedChange = { BubbleConfig.setRecordKeyboard(it) })
                        }
                        
                        // Gravar Bola Toggle
                        val recordBubble by BubbleConfig.recordBubble.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gravar Bola", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = recordBubble, onCheckedChange = { BubbleConfig.setRecordBubble(it) })
                        }
                        
                        // Facecam Toggle
                        val showWebcam by BubbleConfig.showWebcam.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Facecam", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = showWebcam, onCheckedChange = { BubbleConfig.setWebcam(it) })
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = {
                                if (recorderManager.isRecording) {
                                    if (recorderManager.isPaused) recorderManager.resumeRecording()
                                    else recorderManager.pauseRecording()
                                }
                            }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Pause/Resume", tint = if (recorderManager.isPaused) Color(0xFFF59E0B) else Color.White)
                            }
                            
                            IconButton(onClick = {
                                isExpanded = false
                                stopSelf()
                            }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::composeView.isInitialized && composeView.isAttachedToWindow) {
            windowManager.removeView(composeView)
        }
        removeWebcamView()
        recorderManager.stopRecording()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_record_channel",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "screen_record_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }
}
