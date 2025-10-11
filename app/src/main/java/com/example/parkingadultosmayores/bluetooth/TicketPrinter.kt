package com.example.parkingadultosmayores.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID

private const val TAG = "BT_PRINT"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private val UTF8: Charset = Charsets.UTF_8

/** ===========================================================
 *                 UTILIDADES DE FORMATO ESC/POS
 *  =========================================================== */
private object EscPos {
    const val ESC: Byte = 0x1B
    const val GS:  Byte = 0x1D

    val ALIGN_LEFT   = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val ALIGN_RIGHT  = byteArrayOf(ESC, 0x61, 0x02)

    val BOLD_ON  = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)

    // Tamaños: 0x1D, 0x21, n  (n: 0 normal, 0x01 ancho x2, 0x10 alto x2, 0x11 ambos)
    fun size(n: Int) = byteArrayOf(GS, 0x21, n.toByte())

    // Fuente pequeña/estándar (no todas las impresoras lo respetan)
    val FONT_SMALL = byteArrayOf(ESC, 0x4D, 0x01) // pequeña
    val FONT_STD   = byteArrayOf(ESC, 0x4D, 0x00) // estándar
}

private const val SEP = "------------------------------" // ~30-32 columnas típico

private fun writeln(out: java.io.OutputStream, text: String = "") {
    out.write((text + "\n").toByteArray(UTF8))
}
private fun write(out: java.io.OutputStream, bytes: ByteArray) { out.write(bytes) }

/** Alimentación (saltos) controlada para evitar corte de letras por guillotina */
private fun feedLines(out: java.io.OutputStream, lines: Int) {
    repeat(lines.coerceAtLeast(0)) { writeln(out) }
}

private fun headerCompactCentered(
    out: java.io.OutputStream,
    sucuName: String,
    ubicacion: String
) {
    write(out, EscPos.ALIGN_CENTER)
    write(out, EscPos.BOLD_ON)
    write(out, EscPos.size(0x01)) // ancho x2 (resalta sin gastar alto)
    writeln(out, sucuName)
    write(out, EscPos.size(0x00))
    write(out, EscPos.BOLD_OFF)

    writeln(out, "PARKING CLUB")
    writeln(out, ubicacion)
    writeln(out, SEP)
}

/** ---------- QR ESC/POS: GS ( k  con tamaño ajustable ---------- */
private fun printQRCodeWithSize(data: String, out: java.io.OutputStream, moduleSize: Int) {
    // Modelo
    out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
    // Tamaño del módulo (entre 4 y 8 suele ser seguro). Pediste "un poco más grande" => 7.
    val mod = moduleSize.coerceIn(4, 8).toByte()
    out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, mod))
    // Corrección de error (0x30 → nivel por defecto adecuado)
    out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
    // Datos
    val payload = data.toByteArray(UTF8)
    val len = payload.size + 3
    val pL = (len % 256).toByte()
    val pH = (len / 256).toByte()
    out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
    out.write(payload)
    // Imprimir
    out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
}

/** ---------- Conexión Bluetooth con fallback robusto ---------- */
@SuppressLint("MissingPermission")
private fun connectSocket(macAddress: String): BluetoothSocket {
    val adapter = BluetoothAdapter.getDefaultAdapter()
        ?: throw IllegalStateException("Este dispositivo no tiene Bluetooth")

    if (!adapter.isEnabled) throw IllegalStateException("Bluetooth apagado")

    // Verificar emparejamiento (mejor UX)
    val bonded = adapter.bondedDevices.any { it.address.equals(macAddress, ignoreCase = true) }
    if (!bonded) throw IllegalStateException("La impresora no está emparejada")

    val device = adapter.getRemoteDevice(macAddress)
    return try {
        adapter.cancelDiscovery()
        device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
    } catch (e1: Exception) {
        Log.w(TAG, "SPP seguro falló: ${e1.message}")
        try {
            adapter.cancelDiscovery()
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
        } catch (e2: Exception) {
            Log.w(TAG, "SPP insecure falló: ${e2.message}")
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
        }
    }
}

/** ===========================================================
 *                  TICKET DE INGRESO (COMPACTO)
 *  =========================================================== */
@SuppressLint("MissingPermission")
suspend fun printTicketIngresoVerbose(
    macAddress: String,
    sucuName: String,
    ubicacion: String,
    placa: String,
    fecha: String,
    hora: String,
    info: String,       // leyenda/condiciones (puede venir de PrinterConfig.INFO_INGRESO)
    qrData: String
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    try {
        val socket = connectSocket(macAddress)

        socket.outputStream.use { out ->
            // Encabezado centrado
            write(out, EscPos.ALIGN_CENTER)
            headerCompactCentered(out, sucuName, ubicacion)

            // Título centrado
            write(out, EscPos.BOLD_ON)
            writeln(out, "TICKET DE INGRESO")
            write(out, EscPos.BOLD_OFF)
            writeln(out, SEP)

            // Datos alineados a la IZQUIERDA (como pediste)
            write(out, EscPos.ALIGN_LEFT)
            write(out, EscPos.FONT_STD)
            writeln(out, "Placa: $placa")
            writeln(out, "Fecha: $fecha")
            writeln(out, "Hora:  $hora \n\n")

            // QR centrado, tamaño un poco mayor (7) y SOLO 1 salto después
            write(out, EscPos.ALIGN_CENTER)
            printQRCodeWithSize(qrData, out, moduleSize = 7)

            // Leyenda compacta (centrada para estética general)
            write(out, EscPos.ALIGN_CENTER)
            write(out, EscPos.FONT_SMALL)
            info.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { writeln(out, it) }
            write(out, EscPos.FONT_STD)

            // Espacio extra para evitar cortar letras al final (3–4 líneas)
            feedLines(out, 3)
            out.flush()
            delay(250L)
        }

        try { socket.close() } catch (_: Exception) {}
        true to null
    } catch (se: SecurityException) {
        Log.e(TAG, "Permiso faltante", se)
        false to "Falta permiso de Bluetooth"
    } catch (e: Exception) {
        Log.e(TAG, "Error imprimiendo ingreso", e)
        false to (e.message ?: "Fallo desconocido")
    }
}

/** ===========================================================
 *                  TICKET DE SALIDA (COMPACTO)
 *  =========================================================== */
@SuppressLint("MissingPermission")
suspend fun printTicketSalidaVerbose(
    macAddress: String,
    sucuName: String,
    ubicacion: String,
    placa: String,
    fecha: String,
    horaIngreso: String,
    horaSalida: String,
    total: Double,
    info: String,      // leyenda/condiciones adicionales
    qrData: String
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    try {
        val socket = connectSocket(macAddress)

        socket.outputStream.use { out ->
            // Encabezado centrado
            write(out, EscPos.ALIGN_CENTER)
            headerCompactCentered(out, sucuName, ubicacion)

            // Título centrado
            write(out, EscPos.BOLD_ON)
            writeln(out, "TICKET DE SALIDA")
            write(out, EscPos.BOLD_OFF)
            writeln(out, SEP)

            // Datos alineados a la IZQUIERDA (como pediste)
            write(out, EscPos.ALIGN_LEFT)
            writeln(out, "Placa:   $placa")
            writeln(out, "Fecha:   $fecha")
            writeln(out, "Entrada: $horaIngreso")
            writeln(out, "Salida:  $horaSalida")
            writeln(out, SEP)

            // Total centrado y destacado
            write(out, EscPos.ALIGN_CENTER)
            write(out, EscPos.BOLD_ON)
            writeln(out, "TOTAL")
            write(out, EscPos.size(0x01)) // ancho x2 compacto
            val totalTxt = String.format(Locale.getDefault(), "%.2f", total)
            writeln(out, "$ $totalTxt")
            write(out, EscPos.size(0x00))
            write(out, EscPos.BOLD_OFF)
            writeln(out, SEP)

            // (Opcional) QR centrado: si quieres mostrar validación/código
            // Ahorro de papel: lo dejamos comentado. Si lo activas, se respeta 1 salto.
            // printQRCodeWithSize(qrData, out, moduleSize = 7)
            // writeln(out) // exactamente 1 salto si activas el QR

            // Leyenda compacta centrada
            write(out, EscPos.ALIGN_CENTER)
            write(out, EscPos.FONT_SMALL)
            info.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { writeln(out, it) }
            write(out, EscPos.FONT_STD)

            // Espacio extra al final para evitar corte de letras
            feedLines(out, 3)
            out.flush()
            delay(250L)
        }

        try { socket.close() } catch (_: Exception) {}
        true to null
    } catch (e: Exception) {
        Log.e(TAG, "Error imprimiendo salida", e)
        false to (e.message ?: "Error desconocido")
    }
}
