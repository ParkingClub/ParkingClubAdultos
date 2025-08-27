// file: app/src/main/java/com/example/parkingadultosmayores/licensing/ExpirationGate.kt
package com.example.parkingadultosmayores.licensing

import android.app.Activity
import android.app.AlertDialog

object ExpirationGate {
    // EXPIRA AYER (UTC): 2025-08-18 23:59:59Z -> 1755561599000
    // Para pruebas: así siempre verás el mensaje de “Licencia expirada”.
    //private const val EXPIRY_EPOCH_MS = 1755561599000L
    private const val EXPIRY_EPOCH_MS = 1767225599000L
    //private const val EXPIRY_EPOCH_MS = 1755652320000L



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
