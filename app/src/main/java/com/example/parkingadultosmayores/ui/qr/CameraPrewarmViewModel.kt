package com.example.parkingadultosmayores.ui.qr

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CameraPrewarmViewModel(app: Application) : AndroidViewModel(app) {

    private val _cameraProvider = MutableStateFlow<ProcessCameraProvider?>(null)
    val cameraProvider: StateFlow<ProcessCameraProvider?> = _cameraProvider

    // Scanner de solo QR (reutilizable entre pantallas)
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .build()
    )

    init {
        // Pre-warm del ProcessCameraProvider
        viewModelScope.launch {
            _cameraProvider.value = ProcessCameraProvider.getInstance(app).await()
        }
    }

    override fun onCleared() {
        // Libera recursos del scanner cuando el VM muere
        try { scanner.close() } catch (_: Exception) { }
        super.onCleared()
    }
}
