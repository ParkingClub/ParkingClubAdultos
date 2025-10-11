// file: ui/recaudaciones/RecaudacionesScreen.kt
package com.example.parkingadultosmayores.ui.recaudaciones

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.bluetooth.printTicketSalidaVerbose
import com.example.parkingadultosmayores.data.model.RecaudacionRecord
import com.example.parkingadultosmayores.data.model.RecaudacionStore
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun RecaudacionesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lista by remember { mutableStateOf<List<RecaudacionRecord>>(emptyList()) }
    var total by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }

    // Control de impresión (bloquear botón del item que imprime)
    var printingId by remember { mutableStateOf<String?>(null) }
    var pendingReprint: RecaudacionRecord? by remember { mutableStateOf(null) }

    val bg = Brush.verticalGradient(listOf(Color(0xFF141728), Color(0xFF1F2233)))

    fun cargar() {
        loading = true
        scope.launch {
            lista = RecaudacionStore.getToday(context)
            total = RecaudacionStore.totalToday(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) { cargar() }

    // --------- IMPORTANTE: definir la "función" de impresión ANTES de usarla ---------
    val imprimirRecibo: (RecaudacionRecord) -> Unit = { r ->
        val fechaParaTicket = r.fechaEntrada ?: r.fecha // preferimos la fecha real de entrada si está
        val info = buildString {
            appendLine("Tipo: ${r.tipoVehiculo} - ${r.jornada}")
            r.horasCobradas?.let { appendLine("Horas cobradas: $it") }
            //appendLine(PrinterConfig.EMAIL)
            appendLine(PrinterConfig.PHONE)
            appendLine("Reimpresion de recibo")
        }

        printingId = r.id
        scope.launch {
            val (ok, err) = printTicketSalidaVerbose(
                macAddress = PrinterConfig.MAC,
                sucuName = PrinterConfig.SUCU_NAME,
                ubicacion = PrinterConfig.UBICACION,
                placa = r.placa,
                fecha = fechaParaTicket,
                horaIngreso = r.horaEntrada,
                horaSalida = r.horaSalida,
                total = r.monto,
                info = info,
                qrData = r.idIngreso
            )
            printingId = null
            if (ok) {
                Toast.makeText(context, "Recibo reimpreso", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, err ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
            }
        }
    }
    // ---------------------------------------------------------------------------------

    // Permisos BT (Android 12+) para reimpresión
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingReprint?.let { r ->
            pendingReprint = null
            imprimirRecibo(r) // ahora sí está en alcance
        }
    }

    Scaffold(containerColor = Color.Transparent) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .padding(inner)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onBack) { Text("◀ Atrás", color = Color(0xFFB9BFD6)) }
                Text("Recaudaciones de hoy", color = Color.White, style = MaterialTheme.typography.titleLarge)

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Total del día
                    val totalTxt = String.format(Locale.getDefault(), "%.2f", total)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2A2E44),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL HOY", color = Color(0xFFB9BFD6))
                            Text("$ $totalTxt", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (lista.isEmpty()) {
                        Text("Aún no hay recaudaciones hoy.", color = Color(0xFFB9BFD6))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lista, key = { it.id }) { r ->
                                ItemRecaudacion(
                                    r = r,
                                    isPrinting = printingId == r.id,
                                    onReprintClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            val needConnect = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.BLUETOOTH_CONNECT
                                            ) != PackageManager.PERMISSION_GRANTED
                                            val needScan = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.BLUETOOTH_SCAN
                                            ) != PackageManager.PERMISSION_GRANTED
                                            if (needConnect || needScan) {
                                                pendingReprint = r
                                                requestBtPerms.launch(
                                                    arrayOf(
                                                        Manifest.permission.BLUETOOTH_CONNECT,
                                                        Manifest.permission.BLUETOOTH_SCAN
                                                    )
                                                )
                                            } else {
                                                imprimirRecibo(r)
                                            }
                                        } else {
                                            imprimirRecibo(r)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRecaudacion(
    r: RecaudacionRecord,
    isPrinting: Boolean,
    onReprintClick: () -> Unit
) {
    Surface(
        color = Color(0xFF2A2E44),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${r.placa} • ${r.tipoVehiculo} • ${r.jornada}", color = Color.White, fontWeight = FontWeight.SemiBold)

            val fechaEntradaTxt = r.fechaEntrada ?: "—"
            Text("Entrada: $fechaEntradaTxt ${r.horaEntrada}  •  Salida: ${r.horaSalida}", color = Color(0xFFD9DDF0))

            val montoTxt = String.format(Locale.getDefault(), "%.2f", r.monto)
            Text("Cobrado: $ $montoTxt", color = Color(0xFFB9BFD6))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    enabled = !isPrinting,
                    onClick = onReprintClick
                ) {
                    Text(if (isPrinting) "Imprimiendo..." else "Reimprimir recibo")
                }
            }
        }
    }
}
