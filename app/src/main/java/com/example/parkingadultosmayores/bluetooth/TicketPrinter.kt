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

/** ---------- Ticket de INGRESO ---------- */
@SuppressLint("MissingPermission")
suspend fun printTicketIngresoVerbose(
    macAddress: String,
    sucuName: String,
    ubicacion: String,
    placa: String,
    fecha: String,
    hora: String,
    info: String,
    qrData: String
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    try {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext false to "Este dispositivo no tiene Bluetooth"
        if (!adapter.isEnabled) return@withContext false to "Bluetooth apagado"

        // Verificar emparejamiento primero
        val bonded = adapter.bondedDevices.any { it.address.equals(macAddress, ignoreCase = true) }
        if (!bonded) return@withContext false to "La impresora no está emparejada"

        val device = adapter.getRemoteDevice(macAddress)

        // Conexión con fallback (seguro → inseguro → canal 1)
        val socket: BluetoothSocket = try {
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

        socket.outputStream.use { out ->
            fun write(b: ByteArray) = out.write(b)
            fun writeln(t: String = "") = write((t + "\n").toByteArray(UTF8))

            // Cabecera
            write(byteArrayOf(0x1B, 0x45, 0x01))      // bold ON
            write(byteArrayOf(0x1D, 0x21, 0x11))      // double W/H
            write(byteArrayOf(0x1B, 0x61, 0x01))      // center
            writeln(sucuName)
            write(byteArrayOf(0x1D, 0x21, 0x00))
            write(byteArrayOf(0x1B, 0x45, 0x00))      // bold OFF
            writeln("PARKING CLUB")
            writeln(ubicacion)
            writeln("--------------------------------")

            // Título
            write(byteArrayOf(0x1B, 0x45, 0x01))
            writeln("TICKET DE INGRESO")
            write(byteArrayOf(0x1B, 0x45, 0x00))
            writeln("--------------------------------")

            // Datos
            write(byteArrayOf(0x1B, 0x61, 0x00))      // left
            writeln("Placa: $placa")
            writeln("Fecha: $fecha")
            writeln("Hora de ingreso: $hora")
            writeln()

            // QR (usa el ID del ingreso o lo que envíes en qrData)
            write(byteArrayOf(0x1B, 0x61, 0x01))      // center
            printQRCode(qrData, out)
            write(byteArrayOf(0x1B, 0x61, 0x00))      // left
            writeln()

            // Texto informativo
            write(byteArrayOf(0x1B, 0x4D, 0x01))      // fuente pequeña
            info.split('\n').forEach { writeln(it) }
            write(byteArrayOf(0x1B, 0x4D, 0x00))

            // Feed
            writeln(); writeln(); writeln()
            out.flush()
            delay(1000L)
        }

        try { socket.close() } catch (_: Exception) {}
        true to null
    } catch (se: SecurityException) {
        Log.e(TAG, "Permiso faltante", se)
        false to "Falta permiso de Bluetooth"
    } catch (e: Exception) {
        Log.e(TAG, "Error imprimiendo", e)
        false to (e.message ?: "Fallo desconocido")
    }
}

/** QR ESC/POS: GS ( k */
fun printQRCode(data: String, outputStream: java.io.OutputStream) {
    // Modelo
    outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
    // Tamaño módulo
    outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08))
    // Corrección de error
    outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
    // Datos
    val payload = data.toByteArray(UTF8)
    val len = payload.size + 3
    val pL = (len % 256).toByte()
    val pH = (len / 256).toByte()
    outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
    outputStream.write(payload)
    // Imprimir
    outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
}

/** ---------- Ticket de SALIDA ---------- */
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
    info: String,
    qrData: String
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    try {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext false to "Sin Bluetooth"
        if (!adapter.isEnabled) return@withContext false to "Bluetooth apagado"

        val device = adapter.getRemoteDevice(macAddress)
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

        adapter.cancelDiscovery()
        socket.connect()

        socket.outputStream.use { out ->
            fun write(b: ByteArray) = out.write(b)
            fun writeln(t: String = "") = write((t + "\n").toByteArray(UTF8))

            // Cabecera
            write(byteArrayOf(0x1B, 0x61, 0x01))
            write(byteArrayOf(0x1D, 0x21, 0x11))
            write(byteArrayOf(0x1B, 0x45, 0x01))
            writeln(sucuName)
            write(byteArrayOf(0x1D, 0x21, 0x00))
            write(byteArrayOf(0x1B, 0x45, 0x00))
            writeln(ubicacion)
            writeln("--------------------------------")
            write(byteArrayOf(0x1B, 0x45, 0x01))
            writeln("TICKET DE SALIDA")
            write(byteArrayOf(0x1B, 0x45, 0x00))
            writeln("--------------------------------")

            // Detalle
            write(byteArrayOf(0x1B, 0x61, 0x00))
            writeln("Placa: $placa")
            writeln("Fecha: $fecha")
            writeln("Entrada: $horaIngreso")
            writeln("Salida: $horaSalida")
            writeln("--------------------------------")

            // Total
            write(byteArrayOf(0x1B, 0x45, 0x01))
            writeln("TOTAL:")
            write(byteArrayOf(0x1D, 0x21, 0x11))
            val totalTxt = String.format(Locale.getDefault(), "%.2f", total)
            writeln("$ $totalTxt")
            write(byteArrayOf(0x1D, 0x21, 0x00))
            write(byteArrayOf(0x1B, 0x45, 0x00))
            writeln("--------------------------------")

            // QR con el ID (opcional)
            write(byteArrayOf(0x1B, 0x61, 0x01))
            //printQRCode(qrData, out)
            //write(byteArrayOf(0x1B, 0x61, 0x00))

            // Políticas
            write(byteArrayOf(0x1B, 0x61, 0x01))
            writeln(info)

            // Feed
            writeln(); writeln(); writeln()
            out.flush()
            delay(1000L)
        }

        try { socket.close() } catch (_: Exception) {}
        true to null
    } catch (e: Exception) {
        Log.e(TAG, "Error imprimiendo salida", e)
        false to (e.message ?: "Error desconocido")
    }
}
