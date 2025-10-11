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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.bluetooth.printTicketSalidaVerbose
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.data.model.IngresoStore
import com.example.parkingadultosmayores.data.model.RecaudacionRecord
import com.example.parkingadultosmayores.data.model.RecaudacionStore
import kotlinx.coroutines.delay
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

    // Estado de trabajo para deshabilitar acciones
    var isWorking by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // --- Helpers de lógica ---

    suspend fun registrarRecaudacionYRemover(r: IngresoRecord, c: CalculoCobro): String? {
        return try {
            val hoyStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val rec = RecaudacionRecord(
                id = UUID.randomUUID().toString(),
                idIngreso = r.id,
                placa = r.placa,
                tipoVehiculo = r.tipoVehiculo,
                jornada = r.jornada,
                fecha = hoyStr,          // día del cobro
                horaEntrada = r.hora,    // hora del ingreso
                horaSalida = c.horaSalida,
                monto = c.total
            )
            RecaudacionStore.add(context, rec)
            IngresoStore.removeById(context, r.id)
            null
        } catch (e: Exception) {
            e.message ?: "Error desconocido al registrar recaudación"
        }
    }

    fun volverAlInicio() {
        // Hacemos 2 "back" para garantizar regresar al menú
        scope.launch {
            onBack()        // salida -> (scanqr o menú)
            delay(100)
            onBack()        // si venía de scanqr, ahora -> menú; si ya estaba en menú, no hace nada
        }
    }

    // Permisos BT (Android 12+) para la opción "Finalizar e imprimir"
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        record?.let { r ->
            val cNow = calc ?: calcularCobro(r)
            finalizarEImprimir(r, cNow, setWorking = { isWorking = it }) { ok, msg ->
                if (ok) {
                    scope.launch {
                        val err = registrarRecaudacionYRemover(r, cNow)
                        if (err == null) {
                            Toast.makeText(context, msg ?: "Pago finalizado e impreso", Toast.LENGTH_LONG).show()
                            volverAlInicio()
                        } else {
                            Toast.makeText(context, "Impreso, pero no se guardó: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, msg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
                }
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
            if (record != null) {
                BottomAppBar(
                    containerColor = Color(0xFF2A2E44),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón: Finalizar sin impresión
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking && record != null && calc != null,
                            onClick = {
                                val r = record!!
                                val c = calc!!
                                scope.launch {
                                    isWorking = true
                                    val err = registrarRecaudacionYRemover(r, c)
                                    isWorking = false
                                    if (err == null) {
                                        Toast.makeText(context, "Pago finalizado", Toast.LENGTH_LONG).show()
                                        volverAlInicio()
                                    } else {
                                        Toast.makeText(context, "No se pudo finalizar: $err", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text(if (isWorking) "Procesando..." else "Finalizar sin impresión")
                        }

                        Spacer(Modifier.width(12.dp))

                        // Botón: Finalizar e imprimir
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking && record != null && calc != null,
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
                                        finalizarEImprimir(r, c, setWorking = { isWorking = it }) { ok, msg ->
                                            if (ok) {
                                                scope.launch {
                                                    val err = registrarRecaudacionYRemover(r, c)
                                                    if (err == null) {
                                                        Toast.makeText(context, msg ?: "Pago finalizado e impreso", Toast.LENGTH_LONG).show()
                                                        volverAlInicio()
                                                    } else {
                                                        Toast.makeText(context, "Impreso, pero no se guardó: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, msg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } else {
                                    finalizarEImprimir(r, c, setWorking = { isWorking = it }) { ok, msg ->
                                        if (ok) {
                                            scope.launch {
                                                val err = registrarRecaudacionYRemover(r, c)
                                                if (err == null) {
                                                    Toast.makeText(context, msg ?: "Pago finalizado e impreso", Toast.LENGTH_LONG).show()
                                                    volverAlInicio()
                                                } else {
                                                    Toast.makeText(context, "Impreso, pero no se guardó: $err", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, msg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(if (isWorking) "Imprimiendo..." else "Finalizar e imprimir")
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
                    .verticalScroll(scrollState)
                    .imePadding(),
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
                        val placaTxt = placa.trim()
                        if (placaTxt.isEmpty()) {
                            Toast.makeText(context, "Ingresa una placa para buscar", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
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
                    Spacer(Modifier.height(100.dp)) // espacio para BottomAppBar
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
    val horaSalida: String,
    val esTarifaPlana: Boolean
)

private fun esTarifaPlana(jornada: String): Boolean {
    return jornada.equals("Diario", true) ||
            jornada.equals("Nocturno", true) ||
            jornada.equals("Completo", true) // legado
}

private fun calcularCobro(rec: IngresoRecord): CalculoCobro {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val entrada: Date? = try { sdf.parse("${rec.fecha} ${rec.hora}") } catch (_: Exception) { null }
    val now = Date()
    val minutos = if (entrada != null) ((now.time - entrada.time) / 60000L).coerceAtLeast(0L) else 0L

    val base = calcularTarifa(rec.tipoVehiculo, rec.jornada)
    val tarifaPlana = esTarifaPlana(rec.jornada)

    val horasRedondeadas = max(1, ceil(minutos / 60.0).toInt())
    val horasCobradas = if (tarifaPlana) 1 else horasRedondeadas.coerceAtMost(6)

    val total = if (tarifaPlana) base else base * horasCobradas
    val horaSalida = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

    return CalculoCobro(minutos, horasCobradas, base, total, horaSalida, tarifaPlana)
}

private fun calcularTarifa(tipoVehiculo: String, jornada: String): Double {
    return when (tipoVehiculo) {
        "Carro" -> when (jornada) {
            "Dia"      -> 0.75
            "Noche"    -> 1.0
            "Diario"   -> 5.0
            "Nocturno" -> 5.0
            else       -> 0.0
        }
        "Moto" -> when (jornada) {
            "Dia"      -> 0.75
            "Noche"    -> 1.0
            "Diario"   -> 5.0
            "Nocturno" -> 5.0
            else       -> 0.0
        }
        else -> 0.0
    }
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

private fun finalizarEImprimir(
    rec: IngresoRecord,
    calc: CalculoCobro?,
    setWorking: (Boolean) -> Unit,
    onDone: (Boolean, String?) -> Unit
) {
    val c = calc ?: calcularCobro(rec)
    val info = buildString {
        appendLine("Tipo: ${rec.tipoVehiculo} - ${rec.jornada}")
        appendLine("Horas cobradas: ${c.horasCobradas}")
        //appendLine(PrinterConfig.EMAIL)
        appendLine(PrinterConfig.PHONE)
        appendLine("Gracias por su visita!")
    }

    setWorking(true)
    // La impresión se hace en background
    // Cuando termine, devolvemos el resultado por onDone
    kotlinx.coroutines.GlobalScope.launch {
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
        setWorking(false)
        onDone(ok, err)
    }
}
