package com.example.parkingadultosmayores.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.view.Surface // ✅ para ROTATION_*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ✅ Opt-in para APIs experimentales que usas (Camera2Interop y getImage())
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.camera.camera2.interop.ExperimentalCamera2Interop::class,
    ExperimentalGetImage::class
)
@Composable
fun QrScannerScreen(
    onResult: (String?) -> Unit,
    onCancel: () -> Unit,
    externalProvider: ProcessCameraProvider? = null,
    externalScanner: BarcodeScanner? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permiso cámara
    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val reqPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPerm = granted
        if (!granted) onCancel()
    }
    LaunchedEffect(Unit) { if (!hasPerm) reqPerm.launch(Manifest.permission.CAMERA) }
    if (!hasPerm) return

    // Provider & Scanner
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(externalProvider) }
    val scanner = remember(externalScanner) {
        externalScanner ?: BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    LaunchedEffect(Unit) {
        if (cameraProvider == null) {
            cameraProvider = ProcessCameraProvider.getInstance(context).get()
        }
    }

    // PreviewView PERFORMANCE
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // Executor para análisis
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    var handled by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
            update = {
                val provider = cameraProvider ?: return@AndroidView
                if (!bound) {
                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0 // ✅
                    val preview = Preview.Builder()
                        .setTargetRotation(rotation) // ✅ ahora es uno de Surface.ROTATION_*
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // Builder de análisis optimizado
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setTargetResolution(Size(960, 540))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

                    // Camera2Interop: AF continuo + FPS estable (API experimental -> OptIn arriba)
                    androidx.camera.camera2.interop.Camera2Interop.Extender(analysisBuilder).apply {
                        setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                            android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        )
                        setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            android.util.Range(24, 30)
                        )
                    }

                    val analyzer = analysisBuilder.build().apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            processQrFrame(
                                imageProxy = imageProxy,
                                scanner = scanner
                            ) { code ->
                                if (!handled && code != null) {
                                    handled = true
                                    try { provider.unbindAll() } catch (_: Exception) {}
                                    onResult(code)
                                }
                            }
                        }
                    }

                    try {
                        provider.unbindAll()
                        boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                        bound = true
                        boundCamera?.cameraControl?.enableTorch(torchOn)
                    } catch (_: Exception) { /* ignore */ }
                }
            }
        )

        // Top bar con cerrar y linterna
        TopAppBar(
            title = { Text("Escanear QR") },
            navigationIcon = {
                IconButton(onClick = {
                    try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                    onCancel()
                }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Cerrar", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = {
                    torchOn = !torchOn
                    boundCamera?.cameraControl?.enableTorch(torchOn)
                }) {
                    Icon(
                        if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = "Linterna",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0x66000000),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        // Marco guía
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(260.dp)
                    .border(2.dp, Color.White, shape = MaterialTheme.shapes.small)
            )
        }
    }
}

private fun processQrFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onFound: (String?) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            if (value != null) onFound(value)
        }
        .addOnFailureListener { /* ignore */ }
        .addOnCompleteListener { imageProxy.close() }
}
