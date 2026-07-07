package com.example.campusbuddy.ui.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.data.vision.GeminiVisionService
import com.example.campusbuddy.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

enum class ScanSide {
    FRONT, BACK
}

enum class AutoCaptureState {
    WAITING, DETECTING, STABLE, CAPTURING
}

/**
 * State holder that bridges Compose composition values into the CameraX analyzer closure.
 * The analyzer captures this object by reference (stable pointer), then reads the latest
 * values through its fields on each frame — avoiding stale closure bugs.
 */
class CameraAnalyzerBridge {
    var isActive: Boolean = true
    var onTextReceived: (String) -> Unit = {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanIdScreen(
    repository: CampusBuddyRepository,
    onScanComplete: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var scanSide by remember { mutableStateOf(ScanSide.FRONT) }
    var scannedFrontData by remember { mutableStateOf<StudentIdData?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Position your student ID in the frame") }
    var permissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }
    var showSkipConfirmDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var finalScannedData by remember { mutableStateOf<StudentIdData?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val ocrProcessor = remember { OcrProcessor() }
    val geminiApiKey = remember { BuildConfig.GEMINI_API_KEY }
    val geminiService = remember {
        if (geminiApiKey.isNotBlank()) GeminiVisionService(geminiApiKey) else null
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraAnalysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // ── Auto-capture state ──
    var autoCaptureState by remember { mutableStateOf(AutoCaptureState.WAITING) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var detectedText by remember { mutableStateOf("") }

    // Stability tracking: keep last 3 recognized text samples
    val textHistory = remember { mutableListOf<String>() }
    val maxHistorySize = 3
    val stabilizationDelayMs = 1500L
    var stabilityJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // ML Kit text recognizer for real-time frame analysis
    val frameRecognizer: TextRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // ML Kit throttling: prevent concurrent invocations on the same recognizer
    val isMlKitBusy = remember { AtomicBoolean(false) }

    // Bridge object for CameraX analyzer — avoids stale closure bug
    val analyzerBridge = remember { CameraAnalyzerBridge() }

    // ── Auto-capture logic (runs on each recomposition, updates the bridge) ──
    val isAnalyzing = autoCaptureEnabled && !isProcessing && autoCaptureState != AutoCaptureState.CAPTURING

    fun onFrameTextReceived(frameText: String) {
        if (!autoCaptureEnabled || isProcessing || autoCaptureState == AutoCaptureState.CAPTURING) return

        if (frameText.isBlank()) {
            textHistory.clear()
            stabilityJob?.cancel()
            if (autoCaptureState != AutoCaptureState.WAITING && scanSide == ScanSide.FRONT) {
                autoCaptureState = AutoCaptureState.WAITING
                statusText = "Position your student ID in the frame"
            }
            return
        }

        textHistory.add(frameText)
        if (textHistory.size > maxHistorySize) {
            textHistory.removeAt(0)
        }

        if (textHistory.size < maxHistorySize) {
            autoCaptureState = AutoCaptureState.DETECTING
            statusText = "Hold still..."
            return
        }

        // Check if text is stable using similarity (not exact match)
        val firstText = textHistory.first()
        val isStable = textHistory.all { textsAreSimilar(it, firstText) }

        if (isStable) {
            if (autoCaptureState != AutoCaptureState.STABLE) {
                autoCaptureState = AutoCaptureState.STABLE
                statusText = "Hold steady..."
                detectedText = firstText
                stabilityJob?.cancel()

                stabilityJob = scope.launch {
                    delay(stabilizationDelayMs)
                    if (autoCaptureState == AutoCaptureState.STABLE) {
                        autoCaptureState = AutoCaptureState.CAPTURING
                        triggerAutoCapture(
                            imageCapture = imageCapture,
                            cameraExecutor = cameraExecutor,
                            scope = scope,
                            scanSide = scanSide,
                            geminiService = geminiService,
                            ocrProcessor = ocrProcessor,
                            scannedFrontData = scannedFrontData,
                            onResult = { studentIdData ->
                                handleScanResult(
                                    studentIdData, scanSide, scannedFrontData,
                                    onUpdateFrontData = { scannedFrontData = it },
                                    onShowReview = { data ->
                                        finalScannedData = data
                                        showReviewDialog = true
                                    },
                                    onChangeSide = { scanSide = it },
                                    onProcessingDone = { isProcessing = false },
                                    onStatusUpdate = { statusText = it }
                                )
                                autoCaptureState = AutoCaptureState.WAITING
                                detectedText = ""
                                textHistory.clear()
                            },
                            onError = { msg ->
                                isProcessing = false
                                statusText = msg
                                autoCaptureState = AutoCaptureState.WAITING
                                detectedText = ""
                                textHistory.clear()
                            }
                        )
                    }
                }
            }
        } else {
            stabilityJob?.cancel()
            autoCaptureState = AutoCaptureState.DETECTING
            statusText = "Hold still..."
        }
    }

    // Update the analyzer bridge on every recomposition (SideEffect runs after composition)
    SideEffect {
        analyzerBridge.isActive = isAnalyzing
        analyzerBridge.onTextReceived = { text -> onFrameTextReceived(text) }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionGranted = true
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    context as android.app.Activity,
                    Manifest.permission.CAMERA
                )
            ) {
                permissionDeniedPermanently = true
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.close()
            frameRecognizer.close()
            cameraExecutor.shutdown()
            cameraAnalysisExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Skip confirmation dialog ──
    if (showSkipConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSkipConfirmDialog = false },
            title = {
                Text("Skip ID Scan?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text("You'll get better matches if you verify with your student ID. You can always do this later from your profile.",
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showSkipConfirmDialog = false; onSkip() }) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmDialog = false }) {
                    Text("Scan Now", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scanSide == ScanSide.FRONT) "Scan Front Side" else "Scan Back Side") },
                navigationIcon = {
                    IconButton(onClick = { ocrProcessor.close(); frameRecognizer.close(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showSkipConfirmDialog = true }) {
                        Text("Do It Later", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Camera Preview ──
            if (permissionGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val cameraProvider = ProcessCameraProvider.getInstance(ctx)

                        cameraProvider.addListener({
                            val provider = cameraProvider.get()
                            val preview = androidx.camera.core.Preview.Builder()
                                .build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setTargetResolution(android.util.Size(1920, 1080))
                                .build()
                            imageCapture = capture

                            // ── ImageAnalysis for real-time text detection ──
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(android.util.Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(cameraAnalysisExecutor) { imageProxy ->
                                // Read current state from the bridge (avoids stale closure)
                                if (!analyzerBridge.isActive) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                // Throttle: skip if previous ML Kit call is still running
                                if (!isMlKitBusy.compareAndSet(false, true)) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    frameRecognizer.process(inputImage)
                                        .addOnSuccessListener { visionText ->
                                            val text = visionText.text
                                            if (text.isNotBlank()) {
                                                analyzerBridge.onTextReceived(text)
                                            }
                                        }
                                        .addOnFailureListener { /* skip failed frames */ }
                                        .addOnCompleteListener {
                                            isMlKitBusy.set(false)
                                            imageProxy.close()
                                        }
                                } else {
                                    isMlKitBusy.set(false)
                                    imageProxy.close()
                                }
                            }

                            val selector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // No permission UI (unchanged)
                NoPermissionContent(
                    permissionDeniedPermanently = permissionDeniedPermanently,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    onSkip = { showSkipConfirmDialog = true }
                )
            }

            // ── Viewfinder overlay with corner brackets and state feedback ──
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                val viewfinderColor = when (autoCaptureState) {
                    AutoCaptureState.WAITING -> if (scanSide == ScanSide.FRONT) Color(0xFF003527) else Color(0xFF0F00A3)
                    AutoCaptureState.DETECTING -> Color(0xFFFFD600)
                    AutoCaptureState.STABLE -> Color(0xFF00C853)
                    AutoCaptureState.CAPTURING -> Color(0xFFFF6D00)
                }
                val vfGlowAlpha by animateFloatAsState(
                    targetValue = if (autoCaptureState == AutoCaptureState.DETECTING) 0.6f else 0f,
                    animationSpec = if (autoCaptureState == AutoCaptureState.DETECTING)
                        infiniteRepeatable(tween(800), RepeatMode.Reverse)
                    else tween(300)
                )

                Box(
                    Modifier
                        .fillMaxWidth(0.85f).aspectRatio(1.6f)
                ) {
                    // Glow behind viewfinder during detection
                    if (autoCaptureState == AutoCaptureState.DETECTING || autoCaptureState == AutoCaptureState.STABLE) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(viewfinderColor.copy(alpha = vfGlowAlpha * 0.08f), RoundedCornerShape(12.dp))
                        )
                    }

                    // Corner brackets (top-left, top-right, bottom-left, bottom-right)
                    val bracketLength = 30.dp
                    val bracketThickness = 3.dp
                    val bracketOffset = 2.dp

                    // Top-left
                    Box(Modifier.align(Alignment.TopStart).padding(start = bracketOffset, top = bracketOffset)) {
                        Column {
                            Box(Modifier.width(bracketLength).height(bracketThickness).background(viewfinderColor))
                        }
                        Box(Modifier.width(bracketThickness).height(bracketLength).background(viewfinderColor))
                    }
                    // Top-right
                    Box(Modifier.align(Alignment.TopEnd).padding(end = bracketOffset, top = bracketOffset)) {
                        Box(Modifier.align(Alignment.TopEnd).width(bracketLength).height(bracketThickness).background(viewfinderColor))
                        Box(Modifier.align(Alignment.TopEnd).width(bracketThickness).height(bracketLength).background(viewfinderColor))
                    }
                    // Bottom-left
                    Box(Modifier.align(Alignment.BottomStart).padding(start = bracketOffset, bottom = bracketOffset)) {
                        Box(Modifier.width(bracketLength).height(bracketThickness).background(viewfinderColor))
                        Box(Modifier.align(Alignment.BottomStart).width(bracketThickness).height(bracketLength).background(viewfinderColor))
                    }
                    // Bottom-right
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = bracketOffset, bottom = bracketOffset)) {
                        Box(Modifier.align(Alignment.BottomEnd).width(bracketLength).height(bracketThickness).background(viewfinderColor))
                        Box(Modifier.align(Alignment.BottomEnd).width(bracketThickness).height(bracketLength).background(viewfinderColor))
                    }

                    // Scanning line animation (horizontal line sweeping down during DETECTING)
                    if (autoCaptureState == AutoCaptureState.DETECTING) {
                        val scanLineY by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopStart)
                                .offset(y = (scanLineY * 200).dp)
                                .background(viewfinderColor.copy(alpha = 0.6f))
                        )
                    }

                    // Center icon indicator (camera icon when waiting, check when stable)
                    if (autoCaptureState == AutoCaptureState.WAITING) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center).size(40.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                // Stability countdown ring — a proper animated arc for the 1.5s countdown
                if (autoCaptureState == AutoCaptureState.STABLE) {
                    var elapsedMs by remember { mutableLongStateOf(0L) }
                    val startTimeMs = remember { mutableLongStateOf(0L) }

                    LaunchedEffect(Unit) {
                        startTimeMs.value = System.currentTimeMillis()
                        while (elapsedMs < stabilizationDelayMs) {
                            delay(16) // ~60fps
                            elapsedMs = System.currentTimeMillis() - startTimeMs.value
                        }
                    }

                    val progress = (elapsedMs.toFloat() / stabilizationDelayMs).coerceIn(0f, 1f)

                    Box(Modifier.fillMaxWidth(0.85f).aspectRatio(1.6f)) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF00C853),
                            strokeWidth = 3.dp,
                            trackColor = Color.Transparent
                        )
                        // Check mark in center when complete
                        if (progress >= 1f) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                                tint = Color(0xFF00C853)
                            )
                        }
                    }
                }
            }

            // ── Bottom controls with state-aware status panel ──
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Status pill with icon ──
                val statusIcon = when {
                    isProcessing -> Icons.Filled.HourglassTop
                    autoCaptureState == AutoCaptureState.WAITING -> Icons.Filled.QrCodeScanner
                    autoCaptureState == AutoCaptureState.DETECTING -> Icons.Filled.CameraAlt
                    autoCaptureState == AutoCaptureState.STABLE -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Info
                }
                val statusColor = when {
                    isProcessing -> Color(0xFFFFD600)
                    autoCaptureState == AutoCaptureState.STABLE -> Color(0xFF00C853)
                    autoCaptureState == AutoCaptureState.DETECTING -> Color(0xFFFFD600)
                    statusText.contains("lighting", ignoreCase = true) ||
                        statusText.contains("failed", ignoreCase = true) -> Color(0xFFFF8F00)
                    else -> Color.White
                }

                AnimatedVisibility(visible = statusText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(statusIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = statusColor)
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                }

                // Auto-capture badge
                if (autoCaptureEnabled && !isProcessing) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF00C853).copy(alpha = 0.15f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (autoCaptureState == AutoCaptureState.STABLE) Color(0xFF00C853)
                                        else Color(0xFF00C853).copy(alpha = 0.5f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (autoCaptureState) {
                                    AutoCaptureState.WAITING -> "Auto-capture ready"
                                    AutoCaptureState.DETECTING -> "Detecting text..."
                                    AutoCaptureState.STABLE -> "Capturing in moment..."
                                    AutoCaptureState.CAPTURING -> "Capturing..."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00C853).copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // Warning hint
                if (!isProcessing && statusText.contains("lighting", ignoreCase = true)) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFFF8F00).copy(alpha = 0.2f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF8F00))
                            Spacer(Modifier.width(6.dp))
                            Text("Better lighting or move closer", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF8F00))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Capture button (pulses when auto-capture is about to trigger) ──
                val captureButtonScale by animateFloatAsState(
                    targetValue = if (autoCaptureState == AutoCaptureState.STABLE) 1.15f else 1f,
                    animationSpec = if (autoCaptureState == AutoCaptureState.STABLE)
                        infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                    else spring(dampingRatio = 0.6f)
                )

                if (!isProcessing) {
                    Box(contentAlignment = Alignment.Center) {
                        // Pulse ring around button when stable
                        if (autoCaptureState == AutoCaptureState.STABLE) {
                            val pulseAlpha by animateFloatAsState(
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
                            )
                            Box(
                                Modifier
                                    .size(80.dp)
                                    .graphicsLayer(alpha = 1f - pulseAlpha)
                                    .border(2.dp, Color(0xFF00C853).copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                stabilityJob?.cancel()
                                autoCaptureState = AutoCaptureState.WAITING
                                textHistory.clear()
                                isProcessing = true
                                statusText = if (scanSide == ScanSide.FRONT && geminiService != null) "Analyzing with AI..." else "Processing..."
                                scope.launch {
                                    val capture = imageCapture ?: return@launch
                                    capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch {
                                                processCapturedImage(
                                                    ocrProcessor, geminiService, image, scanSide, scannedFrontData,
                                                    onResult = { handleScanResult(it, scanSide, scannedFrontData,
                                                        onUpdateFrontData = { scannedFrontData = it },
                                                        onShowReview = { finalScannedData = it; showReviewDialog = true },
                                                        onChangeSide = { scanSide = it },
                                                        onProcessingDone = { isProcessing = false },
                                                        onStatusUpdate = { statusText = it }
                                                    )},
                                                    onError = { isProcessing = false; statusText = it }
                                                )
                                            }
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            isProcessing = false; statusText = "Capture failed. Try again."
                                        }
                                    })
                                }
                            },
                            modifier = Modifier
                                .size(if (autoCaptureState == AutoCaptureState.STABLE) 72.dp else 64.dp)
                                .graphicsLayer(scaleX = captureButtonScale, scaleY = captureButtonScale),
                            containerColor = if (autoCaptureState == AutoCaptureState.STABLE) Color(0xFF00C853) else Color.White,
                            contentColor = if (autoCaptureState == AutoCaptureState.STABLE) Color.White else Color(0xFF003527)
                        ) {
                            Icon(
                                if (autoCaptureState == AutoCaptureState.STABLE) Icons.Filled.Check else Icons.Filled.CameraAlt,
                                contentDescription = "Capture",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                }

                Spacer(Modifier.height(12.dp))

                // Side indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50),
                        color = if (scanSide == ScanSide.FRONT) Color(0xFF003527) else Color(0xFF003527).copy(alpha = 0.3f)) {
                        Text("Front", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(50),
                        color = if (scanSide == ScanSide.BACK) Color(0xFF0F00A3) else Color(0xFF0F00A3).copy(alpha = 0.3f)) {
                        Text("Back", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(if (scanSide == ScanSide.FRONT) "Position the front of your ID in the frame" else "Now flip over and scan the back",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showSkipConfirmDialog = true }) {
                    Text("Skip, do this later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Review & Edit dialog (security: edits -> PENDING_REVIEW, unedited -> VERIFIED) ──
        if (showReviewDialog && finalScannedData != null) {
            val currentData = finalScannedData!!
            var editableName by remember(currentData) { mutableStateOf(currentData.fullName) }
            var editableReg by remember(currentData) { mutableStateOf(currentData.registrationNumber) }
            var editableDept by remember(currentData) { mutableStateOf(currentData.department) }
            var editableYear by remember(currentData) { mutableStateOf(currentData.year) }
            var editableEmail by remember(currentData) { mutableStateOf(currentData.email) }
            var editableCollege by remember(currentData) { mutableStateOf(currentData.collegeName) }

            val isNameValid = editableName.trim().isNotBlank()
            val isRegValid = editableReg.trim().isNotBlank()
            val isCollegeValid = editableCollege.trim().isNotBlank()
            val isFormValid = isNameValid && isRegValid && isCollegeValid

            // Track whether the user modified any OCR-extracted field
            val wasEdited = editableName != currentData.fullName ||
                    editableReg != currentData.registrationNumber ||
                    editableDept != currentData.department ||
                    editableYear != currentData.year ||
                    editableEmail != currentData.email ||
                    editableCollege != currentData.collegeName

            AlertDialog(
                onDismissRequest = { },
                title = { Text("Review Your Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Please correct any mistakes the scanner made before saving.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // If user edited fields, show a security notice
                        if (wasEdited) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "You edited scanned fields. Your verification will be flagged for manual review by an admin.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        OutlinedTextField(editableName, { editableName = it }, label = { Text("Full Name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), isError = !isNameValid,
                            supportingText = if (!isNameValid) {{ Text("Full name is required", color = MaterialTheme.colorScheme.error) }} else null,
                            shape = RoundedCornerShape(4.dp))
                        OutlinedTextField(editableReg, { editableReg = it }, label = { Text("Registration Number") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), isError = !isRegValid,
                            supportingText = if (!isRegValid) {{ Text("Registration number is required", color = MaterialTheme.colorScheme.error) }} else null,
                            shape = RoundedCornerShape(4.dp))
                        OutlinedTextField(editableDept, { editableDept = it }, label = { Text("Department") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp))
                        OutlinedTextField(editableYear, { editableYear = it }, label = { Text("Year") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp))
                        OutlinedTextField(editableEmail, { editableEmail = it }, label = { Text("Email Address") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp))
                        OutlinedTextField(editableCollege, { editableCollege = it }, label = { Text("College Name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), isError = !isCollegeValid,
                            supportingText = if (!isCollegeValid) {{ Text("College name is required", color = MaterialTheme.colorScheme.error) }} else null,
                            shape = RoundedCornerShape(4.dp))

                        if (!isFormValid) {
                            Text("Please fill in all required fields (Full Name, Registration Number, College Name)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = !isSaving && isFormValid, onClick = {
                        isSaving = true
                        scope.launch {
                            val user = repository.getCurrentFirebaseUser()
                            if (user != null) {
                                val updates = mutableMapOf<String, Any>(
                                    // Security: only set VERIFIED if user did NOT manually edit fields
                                    "isVerifiedStudent" to !wasEdited,
                                    "status" to if (wasEdited)
                                        com.example.campusbuddy.data.enums.UserStatus.PENDING_REVIEW.name
                                    else
                                        com.example.campusbuddy.data.enums.UserStatus.VERIFIED.name
                                )
                                if (editableName.trim().isNotBlank()) updates["fullName"] = editableName.trim()
                                if (editableReg.trim().isNotBlank()) updates["registrationNumber"] = editableReg.trim()
                                if (editableDept.trim().isNotBlank()) updates["department"] = editableDept.trim()
                                if (editableYear.trim().isNotBlank()) updates["year"] = editableYear.trim()
                                if (editableEmail.trim().isNotBlank()) updates["email"] = editableEmail.trim()
                                if (editableCollege.trim().isNotBlank()) updates["collegeName"] = editableCollege.trim()
                                val result = repository.updateUserProfile(user.uid, updates)
                                if (result.isSuccess) {
                                    showReviewDialog = false; finalScannedData = null; onScanComplete()
                                } else {
                                    isSaving = false; statusText = "Network error: Could not save profile. Please try again."
                                }
                            } else {
                                isSaving = false; statusText = "Session expired. Please log in again."
                            }
                        }
                    }) { Text(if (isSaving) "Saving..." else "Confirm & Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showReviewDialog = false; finalScannedData = null
                        scanSide = ScanSide.FRONT; scannedFrontData = null
                        autoCaptureState = AutoCaptureState.WAITING; textHistory.clear()
                        statusText = "Position your student ID in the frame"
                    }) { Text("Rescan") }
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// COMPOSABLE HELPERS
// ═══════════════════════════════════════════════════════════

@Composable
private fun NoPermissionContent(
    permissionDeniedPermanently: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(20.dp))
            Text("Camera Permission Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                if (permissionDeniedPermanently)
                    "Camera access was permanently denied. Please enable it in your device settings to scan your student ID."
                else "We need camera access to scan your student ID card.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            if (permissionDeniedPermanently) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Grant Permission", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSkip) {
                Text("Do It Later", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TEXT SIMILARITY FOR STABILITY DETECTION
// ═══════════════════════════════════════════════════════════

/**
 * Compare two text strings using a combination of prefix/suffix overlap and
 * Levenshtein distance ratio. Returns true if the texts are semantically similar
 * (tolerant of minor OCR variations between frames).
 */
private fun textsAreSimilar(t1: String, t2: String, threshold: Float = 0.75f): Boolean {
    if (t1 == t2) return true
    if (t1.isBlank() || t2.isBlank()) return false

    val s1 = t1.trim().take(300).lowercase()
    val s2 = t2.trim().take(300).lowercase()

    // If one contains the other, they're close enough
    if (s1.contains(s2) || s2.contains(s1)) return true

    val maxLen = maxOf(s1.length, s2.length).toFloat()
    if (maxLen == 0f) return true

    // Common prefix/suffix ratio — handles cases where OCR reads slight variations
    val commonPrefixLen = s1.commonPrefixWith(s2).length
    val commonSuffixLen = s1.commonSuffixWith(s2).length
    val overlapScore = (commonPrefixLen + commonSuffixLen) / maxLen

    if (overlapScore >= threshold) return true

    // Also check character-set overlap (handles word reordering)
    val charOverlap = s1.toSet().intersect(s2.toSet()).size.toFloat() / maxOf(s1.toSet().size, s2.toSet().size, 1)
    return charOverlap >= threshold
}

// ═══════════════════════════════════════════════════════════
// SCAN RESULT HANDLING
// ═══════════════════════════════════════════════════════════

private fun handleScanResult(
    studentIdData: StudentIdData,
    scanSide: ScanSide,
    scannedFrontData: StudentIdData?,
    onUpdateFrontData: (StudentIdData) -> Unit,
    onShowReview: (StudentIdData) -> Unit,
    onChangeSide: (ScanSide) -> Unit,
    onProcessingDone: () -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val hasRequiredFields = studentIdData.fullName.isNotBlank() &&
            studentIdData.registrationNumber.isNotBlank() &&
            studentIdData.collegeName.isNotBlank()

    if (hasRequiredFields || scanSide == ScanSide.BACK) {
        // Show review: either we have all required fields, or we've tried both sides
        val merged = if (scanSide == ScanSide.BACK && scannedFrontData != null) {
            scannedFrontData.copy(
                department = if (scannedFrontData.department.isBlank()) studentIdData.department else scannedFrontData.department,
                year = if (scannedFrontData.year.isBlank()) studentIdData.year else scannedFrontData.year,
                email = if (scannedFrontData.email.isBlank()) studentIdData.email else scannedFrontData.email,
                backScanned = true
            )
        } else {
            studentIdData
        }
        onProcessingDone()
        onStatusUpdate(if (hasRequiredFields) "Scan complete! Please review your details." else "Review and fill in any missing fields.")
        onShowReview(merged)
    } else {
        // Front scan didn't get all required fields — save partial data and ask for back
        onUpdateFrontData(studentIdData)
        onChangeSide(ScanSide.BACK)
        onProcessingDone()
        onStatusUpdate("Front scanned! Now flip your ID and scan the back.")
    }
}

// ═══════════════════════════════════════════════════════════
// AUTO-CAPTURE
// ═══════════════════════════════════════════════════════════

private fun triggerAutoCapture(
    imageCapture: ImageCapture?,
    cameraExecutor: ExecutorService,
    scope: kotlinx.coroutines.CoroutineScope,
    scanSide: ScanSide,
    geminiService: GeminiVisionService?,
    ocrProcessor: OcrProcessor,
    scannedFrontData: StudentIdData?,
    onResult: (StudentIdData) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run { onError("Camera not ready"); return }
    capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            scope.launch {
                processCapturedImage(ocrProcessor, geminiService, image, scanSide, scannedFrontData, onResult, onError)
            }
        }
        override fun onError(exception: ImageCaptureException) {
            onError("Auto-capture failed. Try manual capture.")
        }
    })
}

// ═══════════════════════════════════════════════════════════
// IMAGE PROCESSING PIPELINE
// ═══════════════════════════════════════════════════════════

private suspend fun processCapturedImage(
    ocrProcessor: OcrProcessor,
    geminiService: GeminiVisionService?,
    imageProxy: ImageProxy,
    scanSide: ScanSide,
    scannedFrontData: StudentIdData?,
    onResult: (StudentIdData) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = withContext(Dispatchers.IO) { imageProxyToBitmap(imageProxy, rotationDegrees) }
        if (bitmap == null) { onError("Could not process image"); return }

        // Crop to viewfinder area to eliminate background noise
        val croppedBitmap = ImagePreprocessor.cropToViewfinder(bitmap, 0.85f, 1.6f)

        // Try Gemini first (cloud)
        if (geminiService != null) {
            val cloudResult = geminiService.extractStudentIdData(croppedBitmap)
            if (cloudResult.isSuccess) {
                val data = cloudResult.getOrThrow()
                if (data.hasAnyData) { onResult(data); return }
            }
        }

        // ML Kit OCR fallback (always pass correct rotation)
        val rawText = ocrProcessor.recognizeText(croppedBitmap, rotationDegrees = 0) // already rotated
        if (rawText.isBlank()) { onError("No text detected. Please try again with better lighting."); return }

        val result = if (scanSide == ScanSide.FRONT) {
            ocrProcessor.parseFrontSide(rawText)
        } else {
            ocrProcessor.parseBackSide(rawText, scannedFrontData ?: StudentIdData())
        }
        if (!result.hasAnyData) { onError("Could not extract student details. Try repositioning the ID."); return }
        onResult(result)
    } catch (e: Exception) {
        onError("Error processing image: ${e.message}")
    } finally {
        try { imageProxy.close() } catch (_: Exception) { /* already closed */ }
    }
}

/**
 * Convert an ImageProxy (JPEG) to a Bitmap, applying EXIF rotation
 * so the resulting bitmap is always upright (matches display orientation).
 *
 * CRITICAL: CameraX captures in sensor orientation (typically landscape 1920x1080).
 * Without applying rotationDegrees, the bitmap would be sideways when the phone
 * is held in portrait, causing the viewfinder crop to select the wrong area.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Step 1: Decode the JPEG bytes
        val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return null

        // Step 2: Apply EXIF rotation so the bitmap is upright
        if (rotationDegrees == 0) return original

        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = android.graphics.Bitmap.createBitmap(
            original, 0, 0, original.width, original.height, matrix, true
        )
        if (rotated !== original) original.recycle()
        rotated
    } catch (_: Exception) { null }
}
