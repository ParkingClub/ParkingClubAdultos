// file: ui/ingreso/IngresoScreen.kt
package com.example.parkingadultosmayores.ui.ingreso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parkingadultosmayores.bluetooth.printTicketIngresoVerbose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.data.model.IngresoStore
import kotlin.random.Random
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.parkingadultosmayores.bluetooth.PrinterConfig
import com.example.parkingadultosmayores.domain.TarifaService

// -------- Utilidades --------
private fun newTicketId(): String {
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    val rnd = Random.nextInt(1000, 9999)
    return "$ts-$rnd"
}

// Usamos la MAC desde la configuración central
private const val PRINTER_MAC = PrinterConfig.MAC

// -------- Pantalla --------
@Composable
fun IngresoScreen(placaInicial: String, onBack: () -> Unit) {
    // Fondo
    val bg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF111827),
            Color(0xFF0B1220)
        )
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scheme = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = scheme.surfaceVariant.copy(alpha = 0.9f),
        unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.75f),
        disabledContainerColor  = scheme.surfaceVariant.copy(alpha = 0.6f),
        focusedTextColor        = scheme.onSurface,
        unfocusedTextColor      = scheme.onSurface,
        cursorColor             = scheme.primary,
        focusedLabelColor       = scheme.onSurfaceVariant,
        unfocusedLabelColor     = scheme.onSurfaceVariant,
        focusedBorderColor      = scheme.outline.copy(alpha = 0.7f),
        unfocusedBorderColor    = scheme.outline.copy(alpha = 0.4f),
    )

    var placa by remember(placaInicial) { mutableStateOf(placaInicial) }
    var tipoVehiculo by remember { mutableStateOf("Carro") } // Por defecto "Carro"
    var jornada by remember { mutableStateOf("Dia") }        // Por defecto "Dia"
    var isPrinting by remember { mutableStateOf(false) }

    // NUEVO: indicador de éxito para mostrar el mensaje y volver al inicio
    var showSuccess by remember { mutableStateOf(false) }

    // Tarifa unificada (desde TarifaService)
    val tarifa = remember(tipoVehiculo, jornada) {
        TarifaService.tarifaBase(tipoVehiculo, jornada)
    }

    // Al activar showSuccess, esperamos un instante y regresamos al inicio
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1200)          // tiempo para ver el “visto”
            onBack()             // regresar a la pantalla de inicio
        }
    }

    // --- Función que arma datos y lanza impresión (usa versión VERBOSE) ---
    val lanzarImpresion: () -> Unit = {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val id    = newTicketId()

        // Datos desde PrinterConfig
        val sucuName  = PrinterConfig.SUCU_NAME
        val ubicacion = PrinterConfig.UBICACION

        val info = buildString {
            appendLine("Tipo: $tipoVehiculo  ·  Jornada: $jornada")
            appendLine("Tarifa base: $ ${String.format(Locale.getDefault(), "%.2f", tarifa)}")
            appendLine("--------------------------------")
            appendLine(PrinterConfig.INFO_INGRESO)
        }

        isPrinting = true
        scope.launch {
            val (ok, errorMsg) = printTicketIngresoVerbose(
                macAddress = PRINTER_MAC,
                sucuName = sucuName,
                ubicacion = ubicacion,
                placa = placa,
                fecha = fecha,
                hora = hora,
                info = info,
                qrData = id
            )
            isPrinting = false

            if (!ok) {
                Toast.makeText(context, errorMsg ?: "No se pudo imprimir", Toast.LENGTH_LONG).show()
            } else {
                // Guardar el registro de ingreso
                val record = IngresoRecord(
                    id = id,
                    placa = placa,
                    tipoVehiculo = tipoVehiculo,
                    jornada = jornada,
                    tarifa = tarifa, // mantiene referencia de la base informada al cliente
                    fecha = fecha,
                    hora = hora
                )
                IngresoStore.add(context, record)

                // Mostrar mensaje de éxito y luego regresar
                showSuccess = true
            }
        }
    }

    // --- Permisos BT (Android 12+) ---
    val requestBtPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        val okConnect = grantedMap[Manifest.permission.BLUETOOTH_CONNECT] == true
        val okScan    = grantedMap[Manifest.permission.BLUETOOTH_SCAN] == true
        if (okConnect && okScan) {
            lanzarImpresion()
        } else {
            Toast.makeText(context, "Permisos Bluetooth denegados", Toast.LENGTH_LONG).show()
        }
    }

    // ---- Layout ----
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta central
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                scheme.surface.copy(alpha = 0.85f),
                                scheme.surface.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                // Header
                TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("◀ Atrás", color = scheme.primary)
                }
                Text(
                    "Ingreso vehicular",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Registra placa, tipo y jornada.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant)
                )

                Spacer(Modifier.height(14.dp))

                // --- Placa ---
                OutlinedTextField(
                    value = placa,
                    onValueChange = { placa = it.uppercase(Locale.getDefault()) },
                    label = { Text("Placa") },
                    placeholder = { Text("ABC-1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = scheme.onSurface),
                    colors = fieldColors,
                    shape = fieldShape
                )

                Spacer(Modifier.height(16.dp))

                // --- Selector de Tipo de Vehículo ---
                OptionSelector(
                    label = "Tipo de vehículo",
                    options = listOf("Carro", "Moto"),
                    selectedOption = tipoVehiculo,
                    onOptionSelected = { tipoVehiculo = it }
                )

                Spacer(Modifier.height(16.dp))

                // --- Selector de Jornada ---
                OptionSelector(
                    label = "Jornada",
                    options = listOf("Dia", "Noche", "Diario", "Nocturno"),
                    selectedOption = jornada,
                    onOptionSelected = { jornada = it }
                )

                Spacer(Modifier.height(6.dp))

                // Tarifa (desde servicio unificado)
                AssistChip(
                    onClick = { /* info opcional */ },
                    label = {
                        Text(
                            "Tarifa: $ ${String.format(Locale.getDefault(), "%.2f", tarifa)}",
                            color = scheme.onSecondaryContainer
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = scheme.secondaryContainer.copy(alpha = 0.9f),
                        labelColor = scheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(14.dp))

                // Botón
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = placa.isNotBlank() && !isPrinting,
                    onClick = {
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
                                lanzarImpresion()
                            }
                        } else {
                            lanzarImpresion()
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (isPrinting) "Imprimiendo..." else "Imprimir")
                }
            }
        }

        // Overlay mientras imprime
        if (isPrinting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        // NUEVO: Mensaje de éxito con ícono de visto (se cierra solo y navega atrás)
        if (showSuccess) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
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
                        Text("Registro exitoso", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "El ingreso fue registrado e impreso correctamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB9BFD6)
                        )
                    }
                }
            }
        }
    }
}

/**
 * --- Componente reutilizable ---
 */
@Composable
private fun OptionSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                val buttonColors = if (isSelected) {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceVariant.copy(alpha = 0.75f),
                        contentColor = scheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    colors = buttonColors
                ) {
                    Text(text = option, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
