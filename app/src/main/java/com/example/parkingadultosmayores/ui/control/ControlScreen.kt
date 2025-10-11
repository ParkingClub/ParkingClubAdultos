package com.example.parkingadultosmayores.ui.control

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.bluetooth.printTicketIngresoVerbose
import com.example.parkingadultosmayores.data.model.IngresoStore
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ingresos by remember { mutableStateOf<List<IngresoRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Para saber cuál tarjeta está reimprimiendo
    var printingId by remember { mutableStateOf<String?>(null) }
    // Para recordar qué registro quería reimprimir si hubo que pedir permisos
    var pendingReprint by remember { mutableStateOf<IngresoRecord?>(null) }

    fun cargarHoy() {
        loading = true
        scope.launch {
            ingresos = IngresoStore.getToday(context)
            loading = false
        }
    }
    LaunchedEffect(Unit) { cargarHoy() }

    // Permisos (Android 12+) para BT CONNECT + SCAN
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val okConnect = result[Manifest.permission.BLUETOOTH_CONNECT] == true
        val okScan    = result[Manifest.permission.BLUETOOTH_SCAN] == true
        if (okConnect && okScan && pendingReprint != null) {
            doReprint(
                record = pendingReprint!!,
                setPrinting = { printingId = it },
                onDone = { ok, msg ->
                    Toast.makeText(context, msg ?: if (ok) "Reimpreso" else "Error al reimprimir", Toast.LENGTH_LONG).show()
                },
                scope = scope
            )
        } else if (!okConnect || !okScan) {
            Toast.makeText(context, "Permisos Bluetooth denegados", Toast.LENGTH_LONG).show()
        }
        pendingReprint = null
    }

    fun onReprintClick(rec: IngresoRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needConnect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
            val needScan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED

            if (needConnect || needScan) {
                pendingReprint = rec
                requestBtPerms.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } else {
                doReprint(
                    record = rec,
                    setPrinting = { printingId = it },
                    onDone = { ok, msg ->
                        Toast.makeText(context, msg ?: if (ok) "Reimpreso" else "Error al reimprimir", Toast.LENGTH_LONG).show()
                    },
                    scope = scope
                )
            }
        } else {
            doReprint(
                record = rec,
                setPrinting = { printingId = it },
                onDone = { ok, msg ->
                    Toast.makeText(context, msg ?: if (ok) "Reimpreso" else "Error al reimprimir", Toast.LENGTH_LONG).show()
                },
                scope = scope
            )
        }
    }

    val bg = Brush.verticalGradient(listOf(Color(0xFF141728), Color(0xFF1F2233)))
    Box(
        Modifier.fillMaxSize().background(bg)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("◀ Atrás", color = Color(0xFFB9BFD6)) }
                Button(onClick = { cargarHoy() }) { Text("Actualizar") }
            }

            Spacer(Modifier.height(8.dp))
            Text("Registros", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

            CounterPill(label = "Total", value = ingresos.size)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CounterPill("Carros", ingresos.count { it.tipoVehiculo.equals("Carro", true) })
                CounterPill("Motos", ingresos.count { it.tipoVehiculo.equals("Moto", true) })
            }
            Spacer(Modifier.height(8.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (ingresos.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(ingresos) { rec ->
                        IngresoCard(
                            rec = rec,
                            printing = printingId == rec.id,
                            onReprint = { onReprintClick(rec) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IngresoCard(
    rec: IngresoRecord,
    printing: Boolean,
    onReprint: () -> Unit
) {
    val tile = Brush.verticalGradient(listOf(Color(0xFF2B2F44), Color(0xFF25293A)))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            Modifier.background(tile).fillMaxWidth().padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                // Fila superior: placa + ID + botón reimprimir
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(rec.placa, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("ID: ${rec.id}", color = Color(0xFFB9BFD6), fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (printing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = onReprint, enabled = !printing) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reimprimir", tint = Color.White)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hora: ${rec.hora}", color = Color(0xFFD9DDF0), fontSize = 14.sp)
                    Text("${rec.tipoVehiculo} • ${rec.jornada}", color = Color(0xFFD9DDF0), fontSize = 14.sp)
                }

                val tarifaFmt = String.format(Locale.getDefault(), "%.2f", rec.tarifa)
                Text("Tarifa: $ $tarifaFmt", color = Color(0xFFB9BFD6), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF25293A),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sin ingresos registrados hoy", color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Registra un Ingreso y vuelve a esta pantalla.", color = Color(0xFFB9BFD6), fontSize = 12.sp)
        }
    }
}

/* ---------- Lógica de reimpresión ---------- */

private fun doReprint(
    record: IngresoRecord,
    setPrinting: (String?) -> Unit,
    onDone: (Boolean, String?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val tarifaTxt = String.format(Locale.getDefault(), "%.2f", record.tarifa)

    // ✅ Heredamos la descripción global ya configurada en PrinterConfig.INFO_INGRESO
    val info = buildString {
        appendLine("Tipo: ${record.tipoVehiculo}  ·  Jornada: ${record.jornada}")
        appendLine("Tarifa base: $ $tarifaTxt")
        appendLine("--------------------------------")
        appendLine(PrinterConfig.INFO_INGRESO)
    }

    setPrinting(record.id)
    scope.launch {
        val (ok, err) = printTicketIngresoVerbose(
            macAddress = PrinterConfig.MAC,
            sucuName = PrinterConfig.SUCU_NAME,
            ubicacion = PrinterConfig.UBICACION,
            placa = record.placa,
            fecha = record.fecha,
            hora = record.hora,
            info = info,
            qrData = record.id     // QR con el ID del registro
        )
        setPrinting(null)
        onDone(ok, err)
    }
}

@Composable
private fun CounterPill(
    label: String,
    value: Int
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF34384E),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                color = Color(0xFFD9DDF0),
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = value.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
