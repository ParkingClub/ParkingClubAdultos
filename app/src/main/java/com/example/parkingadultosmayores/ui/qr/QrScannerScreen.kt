package com.example.parkingadultosmayores.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onResult: (String?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // permiso cámara
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

    // evitar doble navegación
    var handled by remember { mutableStateOf(false) }

    // scanner sólo QR
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    if (!hasPerm) return

    // provider y preview recordados
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.getInstance(context).await()
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // flag para no re-enlazar cada recomposición
    var bound by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
            update = {
                val provider = cameraProvider ?: return@AndroidView
                if (!bound) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analyzer = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().apply {
                            setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                                processQrFrame(
                                    imageProxy = imageProxy,
                                    scanner = scanner
                                ) { code ->
                                    if (!handled) {
                                        handled = true
                                        try { provider.unbindAll() } catch (_: Exception) {}
                                        onResult(code)
                                    }
                                }
                            }
                        }

                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                        bound = true
                    } catch (_: Exception) { /* ignore */ }
                }
            }
        )

        // barra superior
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0x66000000),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        // guía visual
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
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
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
