package com.example.parkingadultosmayores.licensing

import android.app.Activity
import android.app.AlertDialog

object ExpirationGate {
    // Válido hasta: 2026-09-30 23:59:59 America/Guayaquil (UTC-05)
    // Equivale a 2026-10-01 04:59:59Z (UTC) -> epoch ms:
    // 1790830799000L
    private const val EXPIRY_EPOCH_MS = 1790830799000L

    fun isExpired(): Boolean = System.currentTimeMillis() > EXPIRY_EPOCH_MS

    fun showExpiredAndClose(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Licencia expirada")
            .setMessage("La aplicación ha excedido su tiempo de uso.\nContáctese con su proveedor.")
            .setCancelable(false)
            .setPositiveButton("Cerrar") { _, _ -> activity.finishAffinity() }
            .show()
    }
}
