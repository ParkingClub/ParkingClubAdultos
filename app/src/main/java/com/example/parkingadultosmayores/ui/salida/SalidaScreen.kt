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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.bluetooth.printTicketSalidaVerbose
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.data.model.IngresoStore
import com.example.parkingadultosmayores.data.model.RecaudacionRecord
import com.example.parkingadultosmayores.data.model.RecaudacionStore
import com.example.parkingadultosmayores.domain.TarifaService
import com.example.parkingadultosmayores.domain.TarifaService.CalculoCobro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class ProcessAction { NO_PRINT, PRINT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalidaScreen(
    idInicial: String?,
    onBack: () -> Unit,
    onFinish: () -> Unit // ← navega explícito a la pantalla principal (Home)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bg = Brush.verticalGradient(listOf(Color(0xFF141728), Color(0xFF1F2233)))

    // ---- Estados de UI ----
    var placa by remember { mutableStateOf("") }
    var record by remember { mutableStateOf<IngresoRecord?>(null) }
    var calc by remember { mutableStateOf<CalculoCobro?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Banner “ticket encontrado”
    var showFoundBanner by remember { mutableStateOf(false) }

    // Overlay “ticket procesado”
    var showProcessedOverlay by remember { mutableStateOf(false) }

    // Diálogo de confirmación
    var confirmAction by remember { mutableStateOf<ProcessAction?>(null) }

    val scrollState = rememberScrollState()

    // ---- Helpers ----
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

    // Banner “ticket encontrado” autodescarta
    LaunchedEffect(showFoundBanner) {
        if (showFoundBanner) {
            delay(1400)
            showFoundBanner = false
        }
    }

    // Overlay “ticket procesado” y regreso explícito (QR o manual)
    LaunchedEffect(showProcessedOverlay) {
        if (showProcessedOverlay) {
            delay(1100)
            onFinish() // ← navegar directo a Home (o donde decidas)
        }
    }

    // Permisos BT (Android 12+) tras confirmar PRINT
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val r = record ?: return@rememberLauncherForActivityResult
        val cNow = calc ?: TarifaService.calcularCobro(r)
        finalizarEImprimir(r, cNow, setWorking = { isWorking = it }) { ok, msg ->
            if (ok) {
                scope.launch {
                    val err = registrarRecaudacionYRemover(r, cNow)
                    if (err == null) {
                        Toast.makeText(context, msg ?: "Pago finalizado e impreso", Toast.LENGTH_LONG).show()
                        showProcessedOverlay = true
                    } else {
                        Toast.makeText(context, "Impreso, pero no se guardó: $err", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, msg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- Escaneo QR entrante ----
    LaunchedEffect(idInicial) {
        if (!idInicial.isNullOrBlank()) {
            val r = IngresoStore.getById(context, idInicial)
            if (r != null) {
                record = r
                calc = TarifaService.calcularCobro(r)
                errorMsg = null
                showFoundBanner = true
            } else {
                record = null
                calc = null
                errorMsg = "No se encontró el ticket escaneado.\nEste ticket ya fue procesado o no existe."
            }
        }
    }

    // Acciones confirmadas
    fun doProcessNoPrint() {
        val r = record ?: return
        val c = calc ?: TarifaService.calcularCobro(r)
        scope.launch {
            isWorking = true
            val err = registrarRecaudacionYRemover(r, c)
            isWorking = false
            if (err == null) {
                Toast.makeText(context, "Pago finalizado", Toast.LENGTH_LONG).show()
                showProcessedOverlay = true
            } else {
                Toast.makeText(context, "No se pudo finalizar: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun doProcessWithPrint() {
        val r = record ?: return
        val c = calc ?: TarifaService.calcularCobro(r)

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
                return
            }
        }

        finalizarEImprimir(r, c, setWorking = { isWorking = it }) { ok, msg ->
            if (ok) {
                scope.launch {
                    val err = registrarRecaudacionYRemover(r, c)
                    if (err == null) {
                        Toast.makeText(context, msg ?: "Pago finalizado e impreso", Toast.LENGTH_LONG).show()
                        showProcessedOverlay = true
                    } else {
                        Toast.makeText(context, "Impreso, pero no se guardó: $err", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, msg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
            }
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
                        // Finalizar sin impresión (con confirmación)
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking && record != null && calc != null,
                            onClick = { confirmAction = ProcessAction.NO_PRINT }
                        ) {
                            Text(if (isWorking) "Procesando..." else "Finalizar sin impresión")
                        }

                        Spacer(Modifier.width(12.dp))

                        // Finalizar e imprimir (con confirmación)
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking && record != null && calc != null,
                            onClick = { confirmAction = ProcessAction.PRINT }
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

                // Banner “ticket encontrado”
                if (showFoundBanner && record != null) {
                    FoundBanner()
                }

                // Mensaje de error sutil (solo si NO hay record)
                if (errorMsg != null && record == null) {
                    ErrorCard(message = errorMsg!!)
                }

                // 1) Si hay record, mostrar SOLO el resumen (buscador oculto)
                if (record != null && calc != null) {
                    ResumenSalida(record!!, calc!!)
                    Spacer(Modifier.height(100.dp)) // espacio para BottomAppBar
                } else {
                    // 2) Si no hay record, búsqueda manual
                    SearchSection(
                        placa = placa,
                        onPlacaChange = { placa = it.uppercase(Locale.getDefault()) },
                        onBuscar = {
                            val placaTxt = placa.trim()
                            if (placaTxt.isEmpty()) {
                                errorMsg = "Ingresa una placa para buscar."
                                record = null
                                calc = null
                                return@SearchSection
                            }
                            scope.launch {
                                val r = IngresoStore.getLatestByPlaca(context, placaTxt)
                                if (r != null) {
                                    record = r
                                    calc = TarifaService.calcularCobro(r)
                                    errorMsg = null
                                    showFoundBanner = true
                                } else {
                                    record = null
                                    calc = null
                                    errorMsg = "No se encontró un ingreso para $placaTxt.\nPuede que ya fue procesado o no existe."
                                }
                            }
                        },
                        onLimpiar = {
                            placa = ""
                            record = null
                            calc = null
                            errorMsg = null
                        }
                    )
                }
            }

            // Overlay “ticket procesado” ✅
            if (showProcessedOverlay) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Éxito",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Ticket procesado", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Se registró el pago correctamente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB9BFD6)
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación con estilos de la app (Surface + Dialog)
    if (confirmAction != null && record != null && calc != null) {
        val r = record!!
        val c = calc!!
        val totalTxt = String.format(Locale.getDefault(), "%.2f", c.total)
        ConfirmDialog(
            title = "Confirmar proceso",
            body = "¿Está seguro que desea procesar el ticket?\nPlaca: ${r.placa}\nTotal: $ $totalTxt",
            isWorking = isWorking,
            onDismiss = { if (!isWorking) confirmAction = null },
            onConfirm = {
                val action = confirmAction
                confirmAction = null
                when (action) {
                    ProcessAction.NO_PRINT -> doProcessNoPrint()
                    ProcessAction.PRINT    -> doProcessWithPrint()
                    else -> {}
                }
            }
        )
    }
}

@Composable
private fun FoundBanner() {
    Surface(
        color = Color(0xFF1F3A2F),
        contentColor = Color(0xFFCFF6DA),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Encontrado",
                tint = Color(0xFF22C55E)
            )
            Text("Ticket encontrado", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SearchSection(
    placa: String,
    onPlacaChange: (String) -> Unit,
    onBuscar: () -> Unit,
    onLimpiar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = scheme.surfaceVariant.copy(alpha = 0.95f),
        unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.85f),
        disabledContainerColor  = scheme.surfaceVariant.copy(alpha = 0.6f),
        focusedTextColor        = scheme.onSurface,
        unfocusedTextColor      = scheme.onSurface,
        cursorColor             = scheme.primary,
        focusedLabelColor       = scheme.onSurfaceVariant,
        unfocusedLabelColor     = scheme.onSurfaceVariant,
        focusedBorderColor      = scheme.outline.copy(alpha = 0.7f),
        unfocusedBorderColor    = scheme.outline.copy(alpha = 0.4f),
    )

    OutlinedTextField(
        value = placa,
        onValueChange = onPlacaChange,
        label = { Text("Placa (manual)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii
        ),
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = scheme.onSurface),
        colors = fieldColors
    )

    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBuscar, modifier = Modifier.weight(1f)) { Text("Buscar") }
        OutlinedButton(onClick = onLimpiar, modifier = Modifier.weight(1f)) { Text("Limpiar") }
    }

    Divider(color = Color(0x33FFFFFF), thickness = 1.dp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = Color(0xFF2E3247),
        contentColor = Color(0xFFE6E8F5),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Error",
                tint = Color(0xFFFFB4AB)
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
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
    val c = calc ?: TarifaService.calcularCobro(rec)
    val info = buildString {
        appendLine("Tipo: ${rec.tipoVehiculo} - ${rec.jornada}")
        appendLine("Horas cobradas: ${c.horasCobradas}")
        appendLine(PrinterConfig.PHONE)
        appendLine("¡Gracias por su visita!")
    }

    setWorking(true)
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

/* ---------- Diálogo de confirmación con estilos de la app ---------- */
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { if (!isWorking) onDismiss() }) {
        Surface(
            color = Color(0xFF1E293B),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9BFD6))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancelar") }

                    Button(
                        onClick = onConfirm,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sí, continuar") }
                }
            }
        }
    }
}
