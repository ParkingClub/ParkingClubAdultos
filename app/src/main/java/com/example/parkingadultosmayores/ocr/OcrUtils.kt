package com.example.parkingadultosmayores.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

fun recognizePlateFromBitmap(
    context: Context,
    bitmap: Bitmap,
    onPlate: (String) -> Unit,
    onNoPlate: () -> Unit,
    onError: (Exception) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            // Ajusta el regex si tus placas tienen otro formato
            val regex = Regex("[A-Z]{3,4}-\\d{3,4}")
            val match = regex.find(visionText.text)
            val plate = match?.value
            if (plate != null) onPlate(plate) else onNoPlate()
        }
        .addOnFailureListener { e -> onError(e) }
}
