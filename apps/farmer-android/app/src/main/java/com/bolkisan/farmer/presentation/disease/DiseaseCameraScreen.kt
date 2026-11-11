package com.bolkisan.farmer.presentation.disease

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bolkisan.farmer.data.remote.models.DiseaseDiagnosisDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.NetworkStatusBar
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.diseaseCropOptions
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.presentation.common.localizedPlantPart
import com.bolkisan.farmer.presentation.common.plantPartOptions
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseCameraScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isOnline by repository.isOnline.collectAsState()
    val isVisionAvailable by repository.isGeminiVisionAvailable.collectAsState()
    val outboxCount by repository.outboxPendingCount.collectAsState(initial = 0)
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var rawBitmapForQuality by remember { mutableStateOf<Bitmap?>(null) }
    var selectedCrop by remember { mutableStateOf(repository.selectedCrop) }
    var selectedPlantPart by remember { mutableStateOf("leaf") }
    var farmerNotes by remember { mutableStateOf("") }
    var isDiagnosing by remember { mutableStateOf(false) }
    var diagnosisResult by remember { mutableStateOf<DiseaseDiagnosisDto?>(null) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isEscalating by remember { mutableStateOf(false) }
    var escalationNotice by remember { mutableStateOf<String?>(null) }

    // TTS Engine initialization
    var ttsEngine: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = when (repository.currentLanguage) {
                    "te" -> Locale("te", "IN")
                    "hi" -> Locale("hi", "IN")
                    else -> Locale.ENGLISH
                }
                val res = ttsInstance?.setLanguage(locale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInstance?.language = Locale.ENGLISH
                }
                isTtsReady = true
            }
        }
        ttsEngine = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    // Keep the spoken language in sync when the farmer changes language.
    LaunchedEffect(currentLang, isTtsReady) {
        if (isTtsReady) {
            val locale = when (currentLang) {
                "te" -> Locale("te", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.ENGLISH
            }
            val res = ttsEngine?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine?.language = Locale.ENGLISH
            }
        }
    }

    // Gallery Photo Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val photoFile = File(context.cacheDir, "bolkisan_gallery_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(photoFile).use { output ->
                        input.copyTo(output)
                    }
                }
                capturedPhotoFile = photoFile
                val bm = BitmapFactory.decodeFile(photoFile.absolutePath)
                rawBitmapForQuality = bm
                previewBitmap = bm?.asImageBitmap()
                diagnosisResult = null
            } catch (e: Exception) {
                Log.e("DiseaseCameraScreen", "Failed to load image from gallery", e)
            }
        }
    }

    val crops = remember(currentLang) { diseaseCropOptions(currentLang) }

    val plantParts = remember(currentLang) { plantPartOptions(currentLang) }

    // Pre-Upload Image Quality Estimator
    val qualityWarning = remember(rawBitmapForQuality, currentLang) {
        val bm = rawBitmapForQuality
        if (bm != null) {
            val sampleW = minOf(bm.width, 100)
            val sampleH = minOf(bm.height, 100)
            var totalBrightness = 0L
            var count = 0
            for (x in 0 until sampleW step 5) {
                for (y in 0 until sampleH step 5) {
                    val pixel = bm.getPixel(x, y)
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    totalBrightness += (r * 299 + g * 587 + b * 114) / 1000
                    count++
                }
            }
            val avg = if (count > 0) totalBrightness / count else 128
            when {
                avg < 35 -> strings.qualityTooDark
                avg > 230 -> strings.qualityOverexposed
                else -> null
            }
        } else null
    }

    fun triggerPhotoCapture() {
        val photoFile = File(
            context.cacheDir,
            "bolkisan_capture_${System.currentTimeMillis()}.jpg"
        )

        val capture = imageCapture
        if (capture != null && hasCameraPermission) {
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        capturedPhotoFile = photoFile
                        val bm = BitmapFactory.decodeFile(photoFile.absolutePath)
                        rawBitmapForQuality = bm
                        previewBitmap = bm?.asImageBitmap()
                        diagnosisResult = null
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("DiseaseCameraScreen", "Camera capture error: ${exception.message}", exception)
                        galleryLauncher.launch("image/*")
                    }
                }
            )
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    fun submitDiagnosis() {
        val file = capturedPhotoFile ?: return
        isDiagnosing = true
        coroutineScope.launch {
            val res = repository.diagnoseDisease(
                imageFile = file,
                cropName = selectedCrop,
                plantPart = selectedPlantPart,
                farmerNotes = farmerNotes
            )
            isDiagnosing = false
            if (res.isSuccess) {
                diagnosisResult = res.getOrNull()
            }
        }
    }

    fun speakDiagnosis(text: String) {
        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "diag_tts_${System.currentTimeMillis()}")
    }

    fun triggerManualOfficerEscalation() {
        isEscalating = true
        coroutineScope.launch {
            val diagName = diagnosisResult?.diseaseName ?: strings.suspectedDisease
            val res = repository.escalateToHumanOfficer(
                questionText = strings.officerEscalationRequest(
                    localizedCropName(selectedCrop, currentLang),
                    localizedPlantPart(selectedPlantPart, currentLang),
                    farmerNotes.ifBlank { diagName }
                ),
                reason = strings.officerEscalationReason
            )
            isEscalating = false
            if (res.isSuccess) {
                val resp = res.getOrNull()
                if (resp?.isOfflineSaved == true || resp?.status in listOf("SAVED_OFFLINE", "PENDING_SYNC")) {
                    escalationNotice = "Your request has been saved. It will be sent to the extension officer automatically when internet connectivity is restored."
                } else {
                    escalationNotice = strings.escalationSent(resp?.ticketNumber ?: "")
                }
            } else {
                escalationNotice = strings.escalationSendFailed
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121814)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Camera Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = strings.diseaseTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = {
                    flashMode = if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
                        ImageCapture.FLASH_MODE_ON
                    } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                        ImageCapture.FLASH_MODE_OFF
                    } else {
                        ImageCapture.FLASH_MODE_AUTO
                    }
                    imageCapture?.flashMode = flashMode
                }) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        },
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }

            // Real-Time Network & Vision State Strip (Decoupled Gemini Vision)
            NetworkStatusBar(
                isOnline = isOnline,
                language = currentLang,
                isVisionAvailable = isVisionAvailable,
                outboxCount = outboxCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Main View Area: CameraX Viewfinder OR Captured Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (capturedPhotoFile != null && previewBitmap != null) {
                    // PREVIEW MODE: Display captured crop photo
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = previewBitmap!!,
                            contentDescription = "Captured Leaf",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Privacy Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = "Privacy", tint = LeafGreenSecondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.privacyBadge,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Quality Pre-Check Warning Badge
                        qualityWarning?.let { warning ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF7F1D1D).copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Quality Warning", tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = warning,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // LIVE CAMERAX VIEWFINDER
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setFlashMode(flashMode)
                                        .build()
                                    imageCapture = capture

                                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            selector,
                                            preview,
                                            capture
                                        )
                                    } catch (exc: Exception) {
                                        Log.e("DiseaseCameraScreen", "Camera binding failed", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Permission Request Prompt
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.cameraPermissionRequired,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                            ) {
                                Text(strings.grantPermission)
                            }
                        }
                    }

                    // Viewfinder Target Frame Overlay
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(2.dp, LeafGreenSecondary, RoundedCornerShape(24.dp))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.alignInsideFrame,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Bottom Panel: Crop Selector & Plant Part & Notes & Controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E2620),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. CROP SELECTION BAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.cropLabel,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = localizedCropName(selectedCrop, currentLang),
                            color = LeafGreenSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        crops.forEach { (cropKey, cropLabel) ->
                            val isSelected = selectedCrop == cropKey
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) ForestGreenPrimary else Color(0xFF2A362D),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) LeafGreenSecondary else Color.Transparent
                                ),
                                modifier = Modifier.clickable {
                                    selectedCrop = cropKey
                                    repository.selectedCrop = cropKey
                                }
                            ) {
                                Text(
                                    text = cropLabel,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. PLANT PART SELECTION BAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.plantPartLabel,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = localizedPlantPart(selectedPlantPart, currentLang),
                            color = HarvestGoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plantParts.forEach { (partKey, partLabel) ->
                            val isSelected = selectedPlantPart == partKey
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF854D0E) else Color(0xFF2A362D),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) HarvestGoldAccent else Color.Transparent
                                ),
                                modifier = Modifier.clickable { selectedPlantPart = partKey }
                            ) {
                                Text(
                                    text = partLabel,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. OPTIONAL FARMER NOTES / SYMPTOMS INPUT
                    OutlinedTextField(
                        value = farmerNotes,
                        onValueChange = { farmerNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                strings.symptomsHint,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = LeafGreenSecondary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LeafGreenSecondary,
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = LeafGreenSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. ACTIONS: Capture / Gallery / Diagnose
                    if (capturedPhotoFile != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    capturedPhotoFile = null
                                    rawBitmapForQuality = null
                                    previewBitmap = null
                                    diagnosisResult = null
                                    escalationNotice = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.retake, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { submitDiagnosis() },
                                enabled = !isDiagnosing,
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LeafGreenSecondary)
                            ) {
                                if (isDiagnosing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strings.analyzing, fontSize = 12.sp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "Diagnose", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.diagnose, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // Shutter & Gallery Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Upload Button
                            IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery Upload", tint = LeafGreenSecondary, modifier = Modifier.size(28.dp))
                                    Text(strings.gallery, color = Color.LightGray, fontSize = 10.sp)
                                }
                            }

                            // Camera Shutter Button
                            Surface(
                                shape = CircleShape,
                                color = ForestGreenPrimary,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable { triggerPhotoCapture() },
                                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Camera, contentDescription = "Shutter", tint = Color.White, modifier = Modifier.size(34.dp))
                                }
                            }

                            // Gallery Leaf Selection Button
                            IconButton(onClick = {
                                galleryLauncher.launch("image/*")
                            }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose Photo", tint = HarvestGoldAccent, modifier = Modifier.size(28.dp))
                                    Text(strings.gallery, color = Color.LightGray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // 5. ESCALATION NOTICE BANNER
                    escalationNotice?.let { notice ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF143020),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LeafGreenSecondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Notice", tint = LeafGreenSecondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(notice, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // 6. DIAGNOSIS RESULT CARD
                    diagnosisResult?.let { diag ->
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    2.dp,
                                    if (diag.isOfflineSyncPending) Color(0xFFFCD34D) else LeafGreenSecondary,
                                    RoundedCornerShape(18.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF142018))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Result Header & Voice Readout
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (diag.isOfflineSyncPending) Icons.Default.CloudSync else Icons.Default.CheckCircle,
                                            contentDescription = "Status",
                                            tint = if (diag.isOfflineSyncPending) Color(0xFFF59E0B) else LeafGreenSecondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (diag.isOfflineSyncPending) strings.offlineSyncProtection else strings.confirmedResult,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Voice Readout Button
                                        IconButton(onClick = {
                                            val textToSpeak = "${diag.diseaseName ?: strings.cropProblem}. ${diag.symptoms ?: ""}. ${diag.treatment ?: ""}"
                                            speakDiagnosis(textToSpeak)
                                        }) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Speak Diagnosis", tint = LeafGreenSecondary)
                                        }

                                        diag.confidence?.let { conf ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF2A362D)
                                            ) {
                                                Text(
                                                    text = strings.matchLabel((conf * 100).toInt()),
                                                    color = LeafGreenSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Offline Sync Banner
                                if (diag.isOfflineSyncPending) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF332A15),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = strings.syncSaved,
                                                color = Color(0xFFFDE68A),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = strings.syncWorker(diag.ticketNumber ?: ""),
                                                color = Color(0xFFFDE68A).copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Disease Name
                                Text(
                                    text = diag.diseaseName ?: strings.suspectedDisease,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Symptoms
                                diag.symptoms?.let { sym ->
                                    Text(
                                        text = sym,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 6.dp),
                                        lineHeight = 16.sp
                                    )
                                }

                                // Treatment & ICAR Advisory
                                diag.treatment?.let { treat ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1E2D22),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = strings.icarRecommendation,
                                                color = LeafGreenSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = treat,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp),
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }

                                // Citations
                                diag.citations?.let { citations ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    for (c in citations) {
                                        Text(
                                            text = "• ${strings.citation(c)}",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 1-TAP ESCALATION TO HUMAN AGRICULTURAL OFFICER
                                OutlinedButton(
                                    onClick = { triggerManualOfficerEscalation() },
                                    enabled = !isEscalating,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HarvestGoldAccent),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, HarvestGoldAccent)
                                ) {
                                    if (isEscalating) {
                                        CircularProgressIndicator(color = HarvestGoldAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.escalating, fontSize = 12.sp, color = HarvestGoldAccent)
                                    } else {
                                        Icon(Icons.Default.SupportAgent, contentDescription = "Officer Escalation", tint = HarvestGoldAccent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.escalateToMao, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HarvestGoldAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
