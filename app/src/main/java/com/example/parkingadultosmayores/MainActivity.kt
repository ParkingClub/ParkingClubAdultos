package com.example.parkingadultosmayores.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.example.parkingadultosmayores.ui.theme.ParkingAdultosMayoresTheme
import com.example.parkingadultosmayores.ocr.recognizePlateFromBitmap
import com.example.parkingadultosmayores.ocr.scanQrFromBitmap
import com.example.parkingadultosmayores.ui.ingreso.IngresoScreen
import com.example.parkingadultosmayores.ui.control.ControlScreen
import com.example.parkingadultosmayores.ui.qr.QrScannerScreen
import com.example.parkingadultosmayores.ui.salida.SalidaScreen

// ======= NUEVO: imports para expiración y housekeeping =======
import com.example.parkingadultosmayores.licensing.ExpirationGate
import com.example.parkingadultosmayores.data.model.IngresoStore
import com.example.parkingadultosmayores.data.model.RecaudacionStore
import com.example.parkingadultosmayores.ui.qr.CameraPrewarmViewModel
import com.example.parkingadultosmayores.ui.recaudaciones.RecaudacionesScreen
import kotlinx.coroutines.runBlocking
// =============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ExpirationGate.isExpired()) {
            ExpirationGate.showExpiredAndClose(this)
            return
        }

        // Housekeeping diario: Ingresos + Recaudaciones (bucket del día)
        runBlocking {
            IngresoStore.dailyHousekeeping(applicationContext)
            RecaudacionStore.dailyHousekeeping(applicationContext)
        }

        setContent {
            ParkingAdultosMayoresTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()

    // ViewModel que pre-calienta cámara y scanner
    val camVm: CameraPrewarmViewModel = viewModel()

    // Observa cuando el provider ya está listo (suele estar listo al llegar)
    val provider by camVm.cameraProvider.collectAsState()

    NavHost(navController = nav, startDestination = "menu") {
        composable("menu") { MenuScreen(nav) }

        composable(
            route = "ingreso?placa={placa}",
            arguments = listOf(navArgument("placa") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStack ->
            val placaArg = backStack.arguments?.getString("placa")
            IngresoScreen(placaInicial = placaArg ?: "", onBack = { nav.popBackStack() })
        }

        composable("scanqr") {
            QrScannerScreen(
                onResult = { code ->
                    if (code != null) {
                        nav.navigate("salida?id=${Uri.encode(code)}")
                    } else {
                        nav.popBackStack()
                    }
                },
                onCancel = { nav.popBackStack() },
                externalProvider = provider,             // pre-warm camera provider
                externalScanner = camVm.scanner          // pre-warm barcode scanner
            )
        }

        composable(
            route = "salida?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStack ->
            val idArg = backStack.arguments?.getString("id")
            // ✅ Corrección: añadimos onFinish para navegación explícita a Home/Menu
            SalidaScreen(
                idInicial = idArg,
                onBack = { nav.popBackStack() },
                onFinish = {
                    nav.navigate("menu") {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("control") { ControlScreen(onBack = { nav.popBackStack() }) }

        // Ruta para la pantalla de Recaudaciones del día
        composable("recaudaciones") {
            RecaudacionesScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun MenuScreen(nav: NavHostController) {
    val context = LocalContext.current

    // Diálogos
    var showIngresoDialog by remember { mutableStateOf(false) }
    var showSalidaDialog by remember { mutableStateOf(false) }

    // Progresos independientes
    var loadingIngreso by remember { mutableStateOf(false) }
    var loadingSalida by remember { mutableStateOf(false) }

    // --- PERMISO CÁMARA PARA EL ESCÁNER QR ---
    var pendingScan by remember { mutableStateOf(false) }
    val requestCamSalida = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingScan) {
            pendingScan = false
            nav.navigate("scanqr")
        }
    }

    /** ------------ Ingreso por Cámara: foto y OCR de placa ------------ */
    val takePictureIngreso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            loadingIngreso = true
            recognizePlateFromBitmap(
                context = context,
                bitmap = bmp,
                onPlate = { plate ->
                    loadingIngreso = false
                    nav.navigate("ingreso?placa=${Uri.encode(plate)}")
                },
                onNoPlate = {
                    loadingIngreso = false
                    nav.navigate("ingreso") // sin placa
                },
                onError = {
                    loadingIngreso = false
                    nav.navigate("ingreso")
                }
            )
        }
    }
    var pendingCamIngreso by remember { mutableStateOf(false) }
    val requestCamIngreso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCamIngreso) {
            pendingCamIngreso = false
            takePictureIngreso.launch(null)
        }
    }

    // UI
    val bg = Brush.verticalGradient(listOf(Color(0xFF121320), Color(0xFF1D2030)))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TopBar()
            Spacer(Modifier.height(20.dp))

            val items = listOf(
                MenuItem(
                    title = "Ingreso",
                    subtitle = "Registrar entrada",
                    icon = Icons.Filled.CarCrash,
                    onClick = { showIngresoDialog = true }
                ),
                MenuItem(
                    title = "Salida",
                    subtitle = "Registrar cobro",
                    icon = Icons.Filled.ExitToApp,
                    onClick = { showSalidaDialog = true }
                ),
                MenuItem(
                    title = "Control",
                    subtitle = "Ver ingresos de hoy",
                    icon = Icons.Filled.List,
                    onClick = { nav.navigate("control") }
                ),
                // Acceso a Recaudaciones del día
                MenuItem(
                    title = "Recaudaciones",
                    subtitle = "Cobros del día",
                    icon = Icons.Filled.AttachMoney,
                    onClick = { nav.navigate("recaudaciones") }
                ),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { MenuCard(it) }
            }
        }

        // Diálogo emergente para Ingreso
        if (showIngresoDialog) {
            OptionsDialog(
                title = "Selecciona el tipo de ingreso",
                onDismiss = { showIngresoDialog = false },
                options = listOf(
                    DialogOption(
                        icon = Icons.Outlined.PhotoCamera,
                        label = "Cámara",
                        onClick = {
                            showIngresoDialog = false
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) takePictureIngreso.launch(null)
                            else {
                                pendingCamIngreso = true
                                requestCamIngreso.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ),
                    DialogOption(
                        icon = Icons.Outlined.Keyboard,
                        label = "Manual",
                        onClick = {
                            showIngresoDialog = false
                            nav.navigate("ingreso")
                        }
                    )
                )
            )
        }

        // Diálogo emergente para Salida
        if (showSalidaDialog) {
            OptionsDialog(
                title = "Selecciona el tipo de salida",
                onDismiss = { showSalidaDialog = false },
                options = listOf(
                    DialogOption(
                        icon = Icons.Outlined.Keyboard,
                        label = "Manual",
                        onClick = {
                            showSalidaDialog = false
                            nav.navigate("salida")
                        }
                    ),
                    DialogOption(
                        icon = Icons.Outlined.QrCodeScanner,
                        label = "Cámara QR",
                        onClick = {
                            showSalidaDialog = false
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                nav.navigate("scanqr")
                            } else {
                                pendingScan = true
                                requestCamSalida.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                )
            )
        }

        // Overlays de progreso
        if (loadingIngreso || loadingSalida) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }
}

// --- NUEVA ESTRUCTURA DE DATOS PARA LAS OPCIONES DEL DIALOGO ---
data class DialogOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

// --- COMPOSABLE REUTILIZABLE PARA LOS DIÁLOGOS ---
@Composable
fun OptionsDialog(
    title: String,
    onDismiss: () -> Unit,
    options: List<DialogOption>
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    options.forEach { option ->
                        OptionButton(
                            icon = option.icon,
                            label = option.label,
                            onClick = option.onClick
                        )
                    }
                }
            }
        }
    }
}

// --- BOTÓN GRANDE CON ICONO PARA DIALOGOS ---
@Composable
fun OptionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/* ====== UI helpers ====== */

@Composable
private fun TopBar() {
    val gradient = Brush.horizontalGradient(listOf(Color(0xFF5A60FF), Color(0xFF6C3BFF)))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text("Parking Club", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Estamos para servirte y que todo sea más fácil.", color = Color(0xFFE0E0E0), fontSize = 13.sp)
            }
        }
    }
}

data class MenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun MenuCard(item: MenuItem) {
    val tileGradient = Brush.verticalGradient(listOf(Color(0xFF2B2F44), Color(0xFF25293A)))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .height(140.dp)
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false)
            .clickable { item.onClick() }
    ) {
        Box(Modifier.background(tileGradient).fillMaxSize().padding(16.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3E46A8).copy(alpha = 0.85f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(item.icon, contentDescription = item.title, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Column {
                    Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.subtitle, color = Color(0xFFB9BFD6), fontSize = 12.sp)
                }
            }
        }
    }
}
