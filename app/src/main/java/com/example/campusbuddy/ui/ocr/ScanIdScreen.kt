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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

enum class ScanSide {
    FRONT, BACK
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

    val ocrProcessor = remember { OcrProcessor() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.close()
            cameraExecutor.shutdown()
        }
    }

    // Launch permission request when the screen appears if not already granted
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Skip confirmation dialog
    if (showSkipConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSkipConfirmDialog = false },
            title = {
                Text(
                    text = "Skip ID Scan?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "You'll get better matches if you verify with your student ID. You can always do this later from your profile.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipConfirmDialog = false
                    onSkip()
                }) {
                    Text(
                        text = "Skip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmDialog = false }) {
                    Text(
                        text = "Scan Now",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (scanSide == ScanSide.FRONT) "Scan Front Side" else "Scan Back Side"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        ocrProcessor.close()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Do It Later button in top bar
                    TextButton(onClick = { showSkipConfirmDialog = true }) {
                        Text(
                            text = "Do It Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Camera Preview
            if (permissionGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

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

                            val selector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
 lifecycleOwner, selector, preview, capture
                                )
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // No permission — show request/retry UI
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (permissionDeniedPermanently)
                                "Camera access was permanently denied. Please enable it in your device settings to scan your student ID."
                            else
                                "We need camera access to scan your student ID card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        if (permissionDeniedPermanently) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Permission", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Skip option even without camera
                        TextButton(onClick = { showSkipConfirmDialog = true }) {
                            Text(
                                text = "Do It Later",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Viewfinder overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1.6f)
                        .border(
                            width = 3.dp,
                            color = if (scanSide == ScanSide.FRONT)
                                Color(0xFF003527) else Color(0xFF0F00A3),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status text
                AnimatedVisibility(visible = statusText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Lighting / flash hint when processing fails
                if (!isProcessing && statusText.contains("lighting", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFF8F00).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF8F00)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Try better lighting or move closer",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF8F00)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Capture button
                if (!isProcessing) {
                    FloatingActionButton(
                        onClick = {
                            val capture = imageCapture ?: return@FloatingActionButton
                            isProcessing = true
                            statusText = "Processing..."

                            scope.launch {
                                capture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch {
                                                processCapturedImage(
                                                    ocrProcessor = ocrProcessor,
                                                    imageProxy = image,
                                                    scanSide = scanSide,
                                                    scannedFrontData = scannedFrontData,
                                                    onResult = { studentIdData ->
                                                        if (scanSide == ScanSide.FRONT) {
                                                            scannedFrontData = studentIdData
                                                            scanSide = ScanSide.BACK
                                                            isProcessing = false
                                                            statusText = "Front scanned! Now flip your ID and scan the back."
                                                        } else {
                                                            // Back side scanned successfully — show review dialog instead of saving immediately
                                                            isProcessing = false
                                                            statusText = "Scan complete! Please review your details."
                                                            finalScannedData = studentIdData
                                                            showReviewDialog = true
                                                        }
                                                    },
                                                    onError = { msg ->
                                                        isProcessing = false
                                                        statusText = msg
                                                    }
                                                )
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isProcessing = false
                                            statusText = "Capture failed. Try again."
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = Color.White,
                        contentColor = Color(0xFF003527)
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Capture",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Side indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (scanSide == ScanSide.FRONT)
                            Color(0xFF003527) else Color(0xFF003527).copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "Front",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (scanSide == ScanSide.BACK)
                            Color(0xFF0F00A3) else Color(0xFF0F00A3).copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "Back",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (scanSide == ScanSide.FRONT)
                        "Position the front of your ID in the frame"
                    else
                        "Now flip over and scan the back",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Do It Later skip button at bottom
                TextButton(onClick = { showSkipConfirmDialog = true }) {
                    Text(
                        text = "Skip, do this later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Review & Edit dialog — allows user to correct OCR mistakes before saving
        if (showReviewDialog && finalScannedData != null) {
            val currentData = finalScannedData!!
            // Re-initialize editable fields whenever scanned data changes
            var editableName by remember(currentData) { mutableStateOf(currentData.fullName) }
            var editableReg by remember(currentData) { mutableStateOf(currentData.registrationNumber) }
            var editableDept by remember(currentData) { mutableStateOf(currentData.department) }
            var editableYear by remember(currentData) { mutableStateOf(currentData.year) }
            var editableEmail by remember(currentData) { mutableStateOf(currentData.email) }
            var editableCollege by remember(currentData) { mutableStateOf(currentData.collegeName) }

            // Validation: essential fields must not be blank
            val isNameValid = editableName.trim().isNotBlank()
            val isRegValid = editableReg.trim().isNotBlank()
            val isCollegeValid = editableCollege.trim().isNotBlank()
            val isFormValid = isNameValid && isRegValid && isCollegeValid

            AlertDialog(
                onDismissRequest = { /* Force user to confirm or rescan */ },
                title = {
                    Text(
                        text = "Review Your Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Please correct any mistakes the scanner made before saving.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = editableName,
                            onValueChange = { editableName = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isNameValid,
                            supportingText = if (!isNameValid) {{
                                Text("Full name is required", color = MaterialTheme.colorScheme.error)
                            }} else null,
                            shape = RoundedCornerShape(4.dp)
                        )

                        OutlinedTextField(
                            value = editableReg,
                            onValueChange = { editableReg = it },
                            label = { Text("Registration Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isRegValid,
                            supportingText = if (!isRegValid) {{
                                Text("Registration number is required", color = MaterialTheme.colorScheme.error)
                            }} else null,
                            shape = RoundedCornerShape(4.dp)
                        )

                        OutlinedTextField(
                            value = editableDept,
                            onValueChange = { editableDept = it },
                            label = { Text("Department") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp)
                        )

                        OutlinedTextField(
                            value = editableYear,
                            onValueChange = { editableYear = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp)
                        )

                        OutlinedTextField(
                            value = editableEmail,
                            onValueChange = { editableEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp)
                        )

                        OutlinedTextField(
                            value = editableCollege,
                            onValueChange = { editableCollege = it },
                            label = { Text("College Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isCollegeValid,
                            supportingText = if (!isCollegeValid) {{
                                Text("College name is required", color = MaterialTheme.colorScheme.error)
                            }} else null,
                            shape = RoundedCornerShape(4.dp)
                        )

                        if (!isFormValid) {
                            Text(
                                text = "Please fill in all required fields (Full Name, Registration Number, College Name)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isSaving && isFormValid,
                        onClick = {
                            isSaving = true
                            scope.launch {
                                // Await Firebase update result to handle errors
                                val user = repository.getCurrentFirebaseUser()
                                if (user != null) {
                                    // Build update map — only include non-empty fields to avoid
                                    // overwriting original signup data with empty strings from OCR gaps.
                                    val updates = mutableMapOf<String, Any>(
                                        "isVerifiedStudent" to true,
                                        "status" to com.example.campusbuddy.data.enums.UserStatus.VERIFIED.name
                                    )
                                    if (editableName.trim().isNotBlank()) updates["fullName"] = editableName.trim()
                                    if (editableReg.trim().isNotBlank()) updates["registrationNumber"] = editableReg.trim()
                                    if (editableDept.trim().isNotBlank()) updates["department"] = editableDept.trim()
                                    if (editableYear.trim().isNotBlank()) updates["year"] = editableYear.trim()
                                    if (editableEmail.trim().isNotBlank()) updates["email"] = editableEmail.trim()
                                    if (editableCollege.trim().isNotBlank()) updates["collegeName"] = editableCollege.trim()
                                    val result = repository.updateUserProfile(user.uid, updates)
                                    if (result.isSuccess) {
                                        showReviewDialog = false
                                        finalScannedData = null
                                        onScanComplete()
                                    } else {
                                        isSaving = false
                                        statusText = "Network error: Could not save profile. Please try again."
                                    }
                                } else {
                                    isSaving = false
                                    statusText = "Session expired. Please log in again."
                                }
                            }
                        }
                    ) {
                        Text(if (isSaving) "Saving..." else "Confirm & Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            // Reset: go back to front scan and let user try again
                            showReviewDialog = false
                            finalScannedData = null
                            scanSide = ScanSide.FRONT
                            scannedFrontData = null
                            statusText = "Position your student ID in the frame"
                        }
                    ) {
                        Text("Rescan")
                    }
                }
            )
        }
    }
}

private suspend fun processCapturedImage(
    ocrProcessor: OcrProcessor,
    imageProxy: ImageProxy,
    scanSide: ScanSide,
    scannedFrontData: StudentIdData?,
    onResult: (StudentIdData) -> Unit,
    onError: (String) -> Unit
) {
    try {
        // Convert ImageProxy to Bitmap
        val bitmap = withContext(Dispatchers.IO) {
            imageProxyToBitmap(imageProxy)
        }
        // imageProxy is closed in the finally block below — do NOT close it here
        // to avoid a double-close crash.

        if (bitmap == null) {
            onError("Could not process image")
            return
        }

        // Recognize text
        val rawText = ocrProcessor.recognizeText(bitmap)

        if (rawText.isBlank()) {
            onError("No text detected. Please try again with better lighting.")
            return
        }

        // Parse based on side
        val result = if (scanSide == ScanSide.FRONT) {
            ocrProcessor.parseFrontSide(rawText)
        } else {
            val frontData = scannedFrontData ?: StudentIdData()
            ocrProcessor.parseBackSide(rawText, frontData)
        }

        if (!result.hasAnyData) {
            onError("Could not extract student details. Try repositioning the ID.")
            return
        }

        onResult(result)
    } catch (e: Exception) {
        onError("Error processing image: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        // ImageCapture outputs JPEG — planes[0].buffer contains the JPEG bytes directly
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return null

        // Scale down for OCR speed while maintaining aspect ratio
        val maxDimension = 1024
        val scale = minOf(maxDimension.toFloat() / originalBitmap.width, maxDimension.toFloat() / originalBitmap.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                originalBitmap,
                (originalBitmap.width * scale).toInt(),
                (originalBitmap.height * scale).toInt(),
                true
            )
        } else {
            originalBitmap
        }
    } catch (e: Exception) {
        null
    }
}
