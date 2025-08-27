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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.parkingadultosmayores.data.model.IngresoRecord
import com.example.parkingadultosmayores.data.model.IngresoStore
import kotlin.random.Random
import androidx.compose.material3.OutlinedTextFieldDefaults

// -------- Utilidades --------
private fun newTicketId(): String {
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    val rnd = Random.nextInt(1000, 9999)
    return "$ts-$rnd"
}

private const val PRINTER_MAC = "00:AA:11:BB:22:CC"
//private const val PRINTER_MAC = "DC:0D:30:CC:8D:5A"

// -------- Pantalla --------
@Composable
fun IngresoScreen(placaInicial: String, onBack: () -> Unit) {
    // Fondo sutil (azules/grises) que funciona en claro/oscuro
    val bg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF111827), // gris azulado profundo
            Color(0xFF0B1220)  // casi negro azulado
        )
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Colores adaptativos basados en el tema actual
    val scheme = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)

    // Campos con contenedor suave y texto de alto contraste
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
    var tipoVehiculo by remember { mutableStateOf("Carro") } // Por defecto "Carro" seleccionado
    var jornada by remember { mutableStateOf("Dia") } // Por defecto "Dia" seleccionado
    var isPrinting by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf<Boolean?>(null) } // null = no mostrar

    val tarifa = remember(tipoVehiculo, jornada) { calcularTarifa(tipoVehiculo, jornada) }

    // --- Función que arma datos y lanza impresión (usa versión VERBOSE) ---
    val lanzarImpresion: () -> Unit = {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val id    = newTicketId()

        val sucuName  = "Parking Soprint"
        val ubicacion = "Sucre #3-48 y Tomas Ordonez"
        val tarifaTxt = String.format(Locale.getDefault(), "%.2f", tarifa)

        val info = buildString {
            appendLine("Si excede 10 min despues de la hora")
            appendLine("registrada se cobrara tarifa completa")
            appendLine("de la siguiente hora. Este ticket")
            appendLine("acredita el ingreso de su vehiculo")
            appendLine("y debe ser entregado al momento")
            appendLine("de su salida. La empresa no se")
            appendLine("responsabiliza por bienes dejados.")
            appendLine("Perdida de ticket 3 dolares.")
            appendLine("")
            appendLine("Tipo: $tipoVehiculo  $jornada - Tarifa: $tarifaTxt")
            appendLine("Tel:0958935190-0962796375")
            appendLine("ID: $id")
            appendLine("¡Un gusto servirle!")
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
            showResult = ok
            if (!ok && errorMsg != null) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            } else if (ok) {
                val record = IngresoRecord(
                    id = id,
                    placa = placa,
                    tipoVehiculo = tipoVehiculo,
                    jornada = jornada,
                    tarifa = tarifa,
                    fecha = fecha,
                    hora = hora
                )
                IngresoStore.add(context, record)
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
            showResult = false
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
            verticalArrangement = Arrangement.Center, // centrado vertical
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

                Spacer(Modifier.height(16.dp)) // Aumentado espacio

                // --- CAMBIO: Selector de Tipo de Vehículo ---
                OptionSelector(
                    label = "Tipo de vehículo",
                    options = listOf("Carro", "Moto"),
                    selectedOption = tipoVehiculo,
                    onOptionSelected = { tipoVehiculo = it }
                )

                Spacer(Modifier.height(16.dp)) // Aumentado espacio

                // --- CAMBIO: Selector de Jornada ---
                OptionSelector(
                    label = "Jornada",
                    options = listOf("Dia", "Noche", "Completo"),
                    selectedOption = jornada,
                    onOptionSelected = { jornada = it }
                )


                Spacer(Modifier.height(6.dp))

                // Tarifa
                AssistChip(
                    onClick = { /* info si quieres */ },
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

        // Resultado
        showResult?.let { ok ->
            AlertDialog(
                onDismissRequest = { showResult = null },
                title = { Text(if (ok) "Impresión completada" else "Error de impresión") },
                text = {
                    if (ok) Text("Se envió el ticket a la impresora.")
                    else    Text("No se pudo imprimir. Verifica que la impresora esté encendida y emparejada.")
                },
                confirmButton = { TextButton(onClick = { showResult = null }) { Text("Aceptar") } }
            )
        }
    }
}

/**
 * --- NUEVO COMPONENTE REUTILIZABLE ---
 * Un selector de opciones con botones que se adapta al estilo de la app.
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
        // Etiqueta (Label) que imita el estilo de OutlinedTextField
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Fila con los botones de selección
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp) // Espacio entre botones
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption

                // Determina los colores según si el botón está seleccionado o no
                val buttonColors = if (isSelected) {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.primary, // Color principal para el seleccionado
                        contentColor = scheme.onPrimary
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceVariant.copy(alpha = 0.75f), // Color sutil para los no seleccionados
                        contentColor = scheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.weight(1f), // Hace que todos los botones ocupen el mismo espacio
                    shape = RoundedCornerShape(12.dp),
                    colors = buttonColors,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(text = option, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


// --- Lógica de tarifas (sin cambios) ---
private fun calcularTarifa(tipoVehiculo: String, jornada: String): Double {
    return when (tipoVehiculo) {
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
}