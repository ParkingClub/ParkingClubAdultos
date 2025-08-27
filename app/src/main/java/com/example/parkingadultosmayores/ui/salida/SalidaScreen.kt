// file: ui/salida/SalidaScreen.kt
package com.example.parkingadultosmayores.ui.salida

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.data.model.IngresoStore
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.bluetooth.printTicketSalidaVerbose
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalidaScreen(
    idInicial: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bg = Brush.verticalGradient(listOf(Color(0xFF141728), Color(0xFF1F2233)))

    var placa by remember { mutableStateOf("") }
    var record by remember { mutableStateOf<IngresoRecord?>(null) }
    var calc by remember { mutableStateOf<CalculoCobro?>(null) }
    var isPrinting by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Permisos BT (Android 12+)
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        record?.let { r ->
            cobrarEImprimir(r, calc, scope, { isPrinting = it }) { ok, msg ->
                Toast.makeText(
                    context,
                    msg ?: if (ok) "Salida impresa" else "Error al imprimir",
                    Toast.LENGTH_LONG
                ).show()
                if (ok) scope.launch { IngresoStore.removeById(context, r.id) }
            }
        }
    }

    // Si llega ID desde QR
    LaunchedEffect(idInicial) {
        if (!idInicial.isNullOrBlank()) {
            val r = IngresoStore.getById(context, idInicial)
            record = r
            calc = r?.let { calcularCobro(it) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            // Barra inferior: solo se muestra si hay un registro cargado
            if (record != null) {
                BottomAppBar(
                    containerColor = Color(0xFF2A2E44),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(), // evita solaparse con barra del sistema
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPrinting,
                            onClick = {
                                val r = record!!
                                val c = calc!!
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val needConnect = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                    val needScan = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.BLUETOOTH_SCAN
                                    ) != PackageManager.PERMISSION_GRANTED
                                    if (needConnect || needScan) {
                                        requestBtPerms.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                Manifest.permission.BLUETOOTH_SCAN
                                            )
                                        )
                                    } else {
                                        cobrarEImprimir(r, c, scope, { isPrinting = it }) { ok, msg ->
                                            Toast.makeText(
                                                context,
                                                msg ?: if (ok) "Salida impresa" else "Error al imprimir",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            if (ok) scope.launch { IngresoStore.removeById(context, r.id) }
                                        }
                                    }
                                } else {
                                    cobrarEImprimir(r, c, scope, { isPrinting = it }) { ok, msg ->
                                        Toast.makeText(
                                            context,
                                            msg ?: if (ok) "Salida impresa" else "Error al imprimir",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        if (ok) scope.launch { IngresoStore.removeById(context, r.id) }
                                    }
                                }
                            }
                        ) {
                            Text(if (isPrinting) "Imprimiendo..." else "Cobrar e imprimir")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .padding(innerPadding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState) // <- hace la pantalla deslizable
                    .imePadding(),               // <- evita que el teclado tape campos
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onBack) { Text("◀ Atrás", color = Color(0xFFB9BFD6)) }
                Text("Salida", color = Color.White, style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = placa,
                    onValueChange = { placa = it.uppercase(Locale.getDefault()) },
                    label = { Text("Placa (manual)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val placaTxt = placa
                        scope.launch {
                            val r = IngresoStore.getLatestByPlaca(context, placaTxt)
                            record = r
                            calc = r?.let { calcularCobro(it) }
                            if (r == null) {
                                Toast.makeText(context, "No se encontró ingreso para $placaTxt", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Buscar") }

                    if (record != null) {
                        OutlinedButton(onClick = { record = null; calc = null }) { Text("Limpiar") }
                    }
                }

                Divider(color = Color(0x33FFFFFF))

                if (record == null) {
                    Text("Busca por placa o entra por QR desde el menú.", color = Color(0xFFB9BFD6))
                } else {
                    val r = record!!
                    val c = calc!!
                    ResumenSalida(r, c)

                    // Deja espacio al final para que el contenido no quede oculto detrás de la BottomAppBar
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }
}

/* ---------- Cálculo de cobro (API 24) ---------- */

data class CalculoCobro(
    val minutos: Long,
    val horasCobradas: Int,
    val base: Double,
    val total: Double,
    val horaSalida: String
)

private fun calcularCobro(rec: IngresoRecord): CalculoCobro {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val entrada: Date? = try { sdf.parse("${rec.fecha} ${rec.hora}") } catch (_: Exception) { null }
    val now = Date()
    val minutos = if (entrada != null) ((now.time - entrada.time) / 60000L).coerceAtLeast(0L) else 0L

    val base = calcularTarifa(rec.tipoVehiculo, rec.jornada)
    val horas = if (rec.jornada.equals("Completo", true)) 1
    else max(1, ceil(minutos / 60.0).toInt())

    val total = if (rec.jornada.equals("Completo", true)) base else base * horas
    val horaSalida = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

    return CalculoCobro(minutos, horas, base, total, horaSalida)
}

private fun calcularTarifa(tipoVehiculo: String, jornada: String): Double =
    when (tipoVehiculo) {
        "Carro" -> when (jornada) {
            "Dia" -> 1.0
            "Noche" -> 2.0
            "Completo" -> 3.0
            else -> 0.0
        }
        "Moto" -> when (jornada) {
            "Dia" -> 0.50
            "Noche" -> 1.0
            "Completo" -> 2.0
            else -> 0.0
        }
        else -> 0.0
    }

@Composable
private fun ResumenSalida(r: IngresoRecord, c: CalculoCobro) {
    Text("Placa: ${r.placa}", color = Color.White)
    Text("Entrada: ${r.hora}", color = Color.White)
    Text("Salida: ${c.horaSalida}", color = Color.White)
    Text("Jornada: ${r.jornada}  •  Tipo: ${r.tipoVehiculo}", color = Color(0xFFD9DDF0))
    Text("Tiempo: ${c.minutos} min  (horas cobradas: ${c.horasCobradas})", color = Color(0xFFD9DDF0))
    val baseTxt = String.format(Locale.getDefault(), "%.2f", c.base)
    val totalTxt = String.format(Locale.getDefault(), "%.2f", c.total)
    Text("Tarifa base: $ $baseTxt", color = Color(0xFFB9BFD6))
    Text("TOTAL: $ $totalTxt", color = Color(0xFFFFF59D), style = MaterialTheme.typography.titleMedium)
}

/* ---------- Impresión de salida ---------- */

private fun cobrarEImprimir(
    rec: IngresoRecord,
    calc: CalculoCobro?,
    scope: kotlinx.coroutines.CoroutineScope,
    setPrinting: (Boolean) -> Unit,
    onDone: (Boolean, String?) -> Unit
) {
    val c = calc ?: calcularCobro(rec)
    val info = buildString {
        appendLine("Tipo: ${rec.tipoVehiculo} - ${rec.jornada}")
        appendLine("Horas cobradas: ${c.horasCobradas}")
        appendLine(PrinterConfig.EMAIL)
        appendLine(PrinterConfig.PHONE)
        appendLine("Gracias por su visita!")
    }

    setPrinting(true)
    scope.launch {
        val (ok, err) = printTicketSalidaVerbose(
            macAddress = PrinterConfig.MAC,
            sucuName = PrinterConfig.SUCU_NAME,
            ubicacion = PrinterConfig.UBICACION,
            placa = rec.placa,
            fecha = rec.fecha,
            horaIngreso = rec.hora,
            horaSalida = c.horaSalida,
            total = c.total,
            info = info,
            qrData = rec.id
        )
        setPrinting(false)
        onDone(ok, err)
    }
}
