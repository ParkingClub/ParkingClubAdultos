package com.example.parkingadultosmayores.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

fun scanQrFromBitmap(
    context: Context,
    bitmap: Bitmap,
    onCode: (String) -> Unit,
    onNoCode: () -> Unit,
    onError: (Exception) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = BarcodeScanning.getClient(options)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { it.rawValue != null }?.rawValue
            if (raw != null) onCode(raw) else onNoCode()
        }
        .addOnFailureListener { onError(it) }
}
