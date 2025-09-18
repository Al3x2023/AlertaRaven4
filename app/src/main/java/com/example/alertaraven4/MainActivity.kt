package com.example.alertaraven4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.alertaraven4.medical.MedicalProfileManager
import com.example.alertaraven4.service.AccidentMonitoringService
import com.example.alertaraven4.permissions.PermissionManager
import com.example.alertaraven4.permissions.PermissionDialogs
import com.example.alertaraven4.ui.screens.SettingsScreen
import com.example.alertaraven4.ui.screens.MedicalProfileScreen
import com.example.alertaraven4.ui.screens.EmergencyContactsScreen
import com.example.alertaraven4.ui.theme.AlertaRaven4Theme
import com.example.alertaraven4.utils.BatteryOptimizer
import com.example.alertaraven4.settings.SettingsManager
import com.example.alertaraven4.ui.components.MonitoringConfirmationDialog
import com.example.alertaraven4.ui.components.MonitoringValidationDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var medicalProfileManager: MedicalProfileManager
    private lateinit var batteryOptimizer: BatteryOptimizer
    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionManager: PermissionManager
    private var isMonitoringActive by mutableStateOf(false)
    private var showConfirmationDialog by mutableStateOf(false)
    private var showValidationDialog by mutableStateOf(false)
    private var validationMessage by mutableStateOf("")
    private var permissionsRequested by mutableStateOf(false)
    
    // Launcher para solicitar permisos
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Algunos permisos son necesarios para el funcionamiento", Toast.LENGTH_LONG).show()
        }
    }
    
    // Launcher para optimización de batería
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (batteryOptimizer.isBatteryOptimizationDisabled()) {
            Toast.makeText(this, "Optimización de batería deshabilitada", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar componentes
        medicalProfileManager = MedicalProfileManager(this)
        batteryOptimizer = BatteryOptimizer(this)
        settingsManager = SettingsManager(this)
        
        // Inicializar el gestor de permisos
        permissionManager = PermissionManager(this) { allGranted, deniedPermissions ->
            handlePermissionsResult(allGranted, deniedPermissions)
        }
        
        // Verificar estado del servicio al iniciar
        isMonitoringActive = isServiceRunning(AccidentMonitoringService::class.java)
        
        // Auto-start del monitoreo si está habilitado
        lifecycleScope.launch {
            settingsManager.autoStartMonitoring.collect { autoStart ->
                if (autoStart && !isMonitoringActive && hasAllPermissions()) {
                    startMonitoringWithValidation()
                }
            }
        }
        
        // Solicitar permisos necesarios al iniciar
        if (!permissionsRequested) {
            requestPermissionsWithExplanation()
        }
        
        setContent {
            AlertaRaven4Theme {
                val navController = rememberNavController()
                
                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                            MainScreen(
                                navController = navController,
                                onToggleMonitoring = ::toggleMonitoring,
                                isMonitoringActive = isMonitoringActive,
                                batteryOptimizer = batteryOptimizer,
                                medicalProfileManager = medicalProfileManager,
                                settingsManager = settingsManager
                            )
                        }
                    composable("medical_profile") {
                        MedicalProfileScreen(
                            navController = navController,
                            medicalProfileManager = medicalProfileManager
                        )
                    }
                    composable("emergency_contacts") {
                        EmergencyContactsScreen(
                            navController = navController,
                            medicalProfileManager = medicalProfileManager
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            settingsManager = settingsManager,
                            batteryOptimizer = batteryOptimizer
                        )
                    }
                }
                
                // Diálogos
                if (showConfirmationDialog) {
                    val delay by settingsManager.monitoringDelay.collectAsState()
                    MonitoringConfirmationDialog(
                        isVisible = showConfirmationDialog,
                        delaySeconds = delay,
                        onConfirm = {
                            showConfirmationDialog = false
                            startMonitoringWithDelay()
                        },
                        onCancel = {
                            showConfirmationDialog = false
                        },
                        onDismiss = {
                            showConfirmationDialog = false
                        }
                    )
                }
                
                if (showValidationDialog) {
                    MonitoringValidationDialog(
                        isVisible = showValidationDialog,
                        missingItems = listOf(validationMessage),
                        onDismiss = { showValidationDialog = false },
                        onNavigateToProfile = {
                            showValidationDialog = false
                            navController.navigate("medical_profile")
                        },
                        onNavigateToContacts = {
                            showValidationDialog = false
                            navController.navigate("emergency_contacts")
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Solicita permisos con explicación previa
     */
    private fun requestPermissionsWithExplanation() {
        permissionsRequested = true
        
        // Verificar si ya tenemos todos los permisos
        if (permissionManager.areAllCriticalPermissionsGranted()) {
            // Verificar permisos adicionales
            permissionManager.checkAndRequestPermissions()
            return
        }
        
        // Mostrar diálogo explicativo antes de solicitar permisos
        PermissionDialogs.showPermissionExplanationDialog(
            context = this,
            onAccept = {
                // Usuario acepta, proceder con la solicitud
                permissionManager.checkAndRequestPermissions()
            },
            onDeny = {
                // Usuario rechaza, mostrar advertencia
                PermissionDialogs.showCriticalPermissionsMissingDialog(
                    context = this,
                    onGoToSettings = {
                        permissionManager.openAppSettings()
                    },
                    onContinueAnyway = {
                        // Continuar sin permisos (funcionalidad limitada)
                        Toast.makeText(this, "Funcionalidad limitada sin permisos", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
    
    /**
     * Maneja el resultado de la solicitud de permisos
     */
    private fun handlePermissionsResult(allGranted: Boolean, deniedPermissions: List<String>) {
        if (allGranted) {
            // Todos los permisos concedidos
            PermissionDialogs.showPermissionsGrantedDialog(
                context = this,
                onContinue = {
                    // Continuar con la configuración normal
                    Toast.makeText(this, "¡AlertaRaven está listo para protegerte!", Toast.LENGTH_SHORT).show()
                    
                    // Auto-iniciar monitoreo si está habilitado
                    lifecycleScope.launch {
                        val autoStart = settingsManager.autoStartMonitoring.value
                        if (autoStart && !isMonitoringActive) {
                            startMonitoringWithValidation()
                        }
                    }
                }
            )
        } else {
            // Algunos permisos fueron denegados
            PermissionDialogs.showPermissionDeniedDialog(
                context = this,
                deniedPermissions = deniedPermissions,
                permissionManager = permissionManager,
                onRetry = {
                    // Reintentar solicitud de permisos
                    permissionManager.checkAndRequestPermissions()
                },
                onContinueWithoutPermissions = {
                    // Continuar con funcionalidad limitada
                    val criticalDenied = deniedPermissions.any { permission ->
                        PermissionManager.CRITICAL_PERMISSIONS.contains(permission)
                    }
                    
                    if (criticalDenied) {
                        PermissionDialogs.showCriticalPermissionsMissingDialog(
                            context = this,
                            onGoToSettings = {
                                permissionManager.openAppSettings()
                            },
                            onContinueAnyway = {
                                Toast.makeText(this, "Funcionalidad limitada sin permisos críticos", Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        Toast.makeText(this, "Algunos permisos opcionales no están disponibles", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
    
    private fun requestBatteryOptimization() {
        val intent = batteryOptimizer.requestDisableBatteryOptimization()
        intent?.let { batteryOptimizationLauncher.launch(it) }
    }
    
    private fun toggleMonitoring() {
        if (isMonitoringActive) {
            stopMonitoring()
        } else {
            startMonitoringWithValidation()
        }
    }
    
    private fun stopMonitoring() {
        AccidentMonitoringService.stopService(this)
        isMonitoringActive = false
        Toast.makeText(this, "Monitoreo detenido", Toast.LENGTH_SHORT).show()
    }
    
    private fun startMonitoring() {
        AccidentMonitoringService.startService(this)
        isMonitoringActive = true
        Toast.makeText(this, "Monitoreo iniciado", Toast.LENGTH_SHORT).show()
    }
    
    private fun startMonitoringWithValidation() {
        // Validar configuración antes de iniciar
        val validationResult = validateMonitoringConfiguration()
        if (!validationResult.isValid) {
            validationMessage = validationResult.message
            showValidationDialog = true
            return
        }
        
        // Verificar si se requiere confirmación
        lifecycleScope.launch {
            val requireConfirmation = settingsManager.requireConfirmation.value
            if (requireConfirmation) {
                showConfirmationDialog = true
            } else {
                startMonitoringWithDelay()
            }
        }
    }
    
    private fun startMonitoringWithDelay() {
        lifecycleScope.launch {
            val delay = settingsManager.monitoringDelay.value
            if (delay > 0) {
                // Aquí podrías mostrar un countdown si quisieras
                kotlinx.coroutines.delay(delay * 1000L)
            }
            startMonitoring()
        }
    }
    
    private data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
    
    private fun validateMonitoringConfiguration(): ValidationResult {
        // Validar perfil médico
        val medicalProfile = medicalProfileManager.getCurrentMedicalProfile()
        if (medicalProfile == null || medicalProfile.fullName.isBlank()) {
            return ValidationResult(false, "Debes completar tu perfil médico antes de activar el monitoreo.")
        }
        
        // Validar contactos de emergencia
        val contacts = medicalProfileManager.getCurrentEmergencyContacts()
        if (contacts.isEmpty()) {
            return ValidationResult(false, "Debes agregar al menos un contacto de emergencia.")
        }
        
        // Validar permisos
        if (!hasAllPermissions()) {
            return ValidationResult(false, "Se requieren todos los permisos para activar el monitoreo.")
        }
        
        return ValidationResult(true, "")
    }
    
    private fun hasAllPermissions(): Boolean {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.VIBRATE)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        batteryOptimizer.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    onToggleMonitoring: () -> Unit,
    isMonitoringActive: Boolean,
    batteryOptimizer: BatteryOptimizer,
    medicalProfileManager: MedicalProfileManager,
    settingsManager: SettingsManager
) {
    val batteryLevel by batteryOptimizer.batteryLevel.collectAsState()
    val powerSaveMode by batteryOptimizer.powerSaveMode.collectAsState()
    val isProfileComplete by medicalProfileManager.isProfileComplete.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "AlertaRaven",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estado del monitoreo
            item {
                MonitoringStatusCard(
                    isActive = isMonitoringActive,
                    onToggle = onToggleMonitoring
                )
            }
            
            // Estado de la batería
            item {
                BatteryStatusCard(
                    batteryLevel = batteryLevel,
                    powerSaveMode = powerSaveMode
                )
            }
            
            // Estado del perfil médico
            item {
                ProfileStatusCard(
                    isComplete = isProfileComplete,
                    onNavigateToProfile = { navController.navigate("medical_profile") }
                )
            }
            
            // Acciones rápidas
            item {
                QuickActionsCard(navController = navController)
            }
            
            // Información de emergencia
            item {
                EmergencyInfoCard(settingsManager = settingsManager)
            }
        }
    }
}

@Composable
fun MonitoringStatusCard(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF4CAF50) else Color(0xFFFF9800)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Security else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isActive) "MONITOREO ACTIVO" else "MONITOREO INACTIVO",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = if (isActive) "Detectando accidentes en segundo plano" else "Toca para activar la protección",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = if (isActive) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            ) {
                Text(
                    text = if (isActive) "DETENER" else "ACTIVAR",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BatteryStatusCard(
    batteryLevel: Int,
    powerSaveMode: BatteryOptimizer.PowerSaveMode
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    batteryLevel > 50 -> Icons.Default.BatteryFull
                    batteryLevel > 20 -> Icons.Default.Battery6Bar
                    else -> Icons.Default.BatteryAlert
                },
                contentDescription = "Batería",
                tint = when {
                    batteryLevel > 50 -> Color(0xFF4CAF50)
                    batteryLevel > 20 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Batería: $batteryLevel%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = when (powerSaveMode) {
                        BatteryOptimizer.PowerSaveMode.NORMAL -> "Funcionamiento normal"
                        BatteryOptimizer.PowerSaveMode.POWER_SAVE -> "Modo ahorro de energía"
                        BatteryOptimizer.PowerSaveMode.CRITICAL -> "Batería crítica"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfileStatusCard(
    isComplete: Boolean,
    onNavigateToProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateToProfile
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = "Perfil médico",
                tint = if (isComplete) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Perfil Médico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = if (isComplete) "Configurado correctamente" else "Requiere configuración",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsCard(navController: NavHostController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Acciones Rápidas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Default.Person,
                    text = "Perfil\nMédico",
                    onClick = { navController.navigate("medical_profile") }
                )
                
                QuickActionButton(
                    icon = Icons.Default.Contacts,
                    text = "Contactos\nEmergencia",
                    onClick = { navController.navigate("emergency_contacts") }
                )
                
                QuickActionButton(
                    icon = Icons.Default.Settings,
                    text = "Configuración",
                    onClick = { navController.navigate("settings") }
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmergencyInfoCard(settingsManager: SettingsManager) {
    val alertTimer by settingsManager.alertTimer.collectAsState()
    val detectionSensitivity by settingsManager.detectionSensitivity.collectAsState()
    val autoStartMonitoring by settingsManager.autoStartMonitoring.collectAsState()
    val monitoringDelay by settingsManager.monitoringDelay.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Emergency,
                    contentDescription = "Emergencia",
                    tint = Color(0xFFF44336)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Configuración Actual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF44336)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "• Sensibilidad de detección: $detectionSensitivity\n" +
                      "• Tiempo para cancelar alerta: $alertTimer segundos\n" +
                      "• Delay de inicio: ${if (monitoringDelay == 0) "Sin delay" else "$monitoringDelay segundos"}\n" +
                      "• Inicio automático: ${if (autoStartMonitoring) "Activado" else "Desactivado"}\n" +
                      "• Mantén tu perfil médico actualizado\n" +
                      "• Configura al menos un contacto de emergencia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}