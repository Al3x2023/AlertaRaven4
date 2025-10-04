package com.example.alertaraven4.ui.screens

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.alertaraven4.ui.icons.Battery3Bar
import com.example.alertaraven4.ui.icons.ChevronRight
import com.example.alertaraven4.ui.icons.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.example.alertaraven4.utils.BatteryOptimizer
import com.example.alertaraven4.settings.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    batteryOptimizer: BatteryOptimizer,
    settingsManager: SettingsManager,
    onDisableOptimization: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    val batteryLevel by batteryOptimizer.batteryLevel.collectAsState()
    val powerSaveMode by batteryOptimizer.powerSaveMode.collectAsState()
    val isOptimizationDisabled = batteryOptimizer.isBatteryOptimizationDisabled()
    
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    
    // Estados de configuración desde SettingsManager
    val detectionSensitivity by settingsManager.detectionSensitivity.collectAsState()
    val alertTimer by settingsManager.alertTimer.collectAsState()
    val soundEnabled by settingsManager.soundEnabled.collectAsState()
    val vibrationEnabled by settingsManager.vibrationEnabled.collectAsState()
    val locationAccuracy by settingsManager.locationAccuracy.collectAsState()
    val autoStartMonitoring by settingsManager.autoStartMonitoring.collectAsState()
    val monitoringDelay by settingsManager.monitoringDelay.collectAsState()
    val requireConfirmation by settingsManager.requireConfirmation.collectAsState()
    val autoCallEnabled by settingsManager.autoCallEnabled.collectAsState()
    val reportTrainingDataEnabled by settingsManager.reportTrainingDataEnabled.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
            // Sección de Detección
            item {
                SettingsSection(title = "Detección de Accidentes") {
                    SettingsItem(
                        icon = Icons.Default.Tune,
                        title = "Sensibilidad de Detección",
                        subtitle = "Actual: $detectionSensitivity",
                        onClick = { showSensitivityDialog = true }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Tiempo de Cancelación",
                        subtitle = "Actual: $alertTimer segundos",
                        onClick = { showTimerDialog = true }
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Default.PlayArrow,
                        title = "Inicio Automático",
                        subtitle = "Iniciar monitoreo al abrir la app",
                        checked = autoStartMonitoring,
                        onCheckedChange = { settingsManager.setAutoStartMonitoring(it) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Delay de Inicio",
                        subtitle = "Actual: $monitoringDelay segundos",
                        onClick = { showDelayDialog = true }
                    )
                    
                SettingsToggleItem(
                    icon = Icons.Default.CheckCircle,
                    title = "Confirmación Requerida",
                    subtitle = "Mostrar diálogo antes de iniciar",
                    checked = requireConfirmation,
                    onCheckedChange = { settingsManager.setRequireConfirmation(it) }
                )

                SettingsToggleItem(
                    icon = Icons.Default.Tune,
                    title = "Enviar datos de entrenamiento",
                    subtitle = "Remitir predicciones y métricas para mejorar el modelo",
                    checked = reportTrainingDataEnabled,
                    onCheckedChange = { settingsManager.setReportTrainingDataEnabled(it) }
                )
            }
        }
            
            // Sección de Alertas
            item {
                SettingsSection(title = "Alertas y Notificaciones") {
                    SettingsToggleItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Sonido de Alerta",
                        subtitle = "Reproducir sonido durante emergencias",
                        checked = soundEnabled,
                        onCheckedChange = { settingsManager.setSoundEnabled(it) }
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Default.Vibration,
                        title = "Vibración",
                        subtitle = "Vibrar durante emergencias",
                        checked = vibrationEnabled,
                        onCheckedChange = { settingsManager.setVibrationEnabled(it) }
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Default.Phone,
                        title = "Llamadas Automáticas",
                        subtitle = "Realizar llamadas automáticas en emergencias",
                        checked = autoCallEnabled,
                        onCheckedChange = { settingsManager.setAutoCallEnabled(it) }
                    )
                }
            }
            
            // Sección de Ubicación
            item {
                SettingsSection(title = "Ubicación y GPS") {
                    SettingsItem(
                        icon = Icons.Default.GpsFixed,
                        title = "Precisión de Ubicación",
                        subtitle = "Actual: $locationAccuracy precisión",
                        onClick = { /* Implementar diálogo de precisión */ }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Map,
                        title = "Probar Ubicación",
                        subtitle = "Verificar funcionamiento del GPS",
                        onClick = { /* Implementar prueba de GPS */ }
                    )
                }
            }
            
            // Sección de Batería
            item {
                SettingsSection(title = "Optimización de Batería") {
                    BatteryStatusCard(
                         batteryLevel = batteryLevel,
                         isLowPowerMode = powerSaveMode != BatteryOptimizer.PowerSaveMode.NORMAL,
                         isOptimizationDisabled = isOptimizationDisabled,
                         onDisableOptimization = onDisableOptimization
                     )
                }
            }

            // Sección de Permisos Especiales
            item {
                SettingsSection(title = "Permisos Especiales") {
                    val context = LocalContext.current
                    val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else true

                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Mostrar sobre otras apps",
                        subtitle = if (overlayEnabled) "Permiso concedido" else "Permiso requerido",
                        onClick = { onRequestOverlay() }
                    )

                    SettingsItem(
                        icon = Icons.Default.BatteryAlert,
                        title = "Ignorar optimización de batería",
                        subtitle = if (isOptimizationDisabled) "Optimización ignorada" else "Optimización activa (recomendado desactivar)",
                        onClick = { onDisableOptimization() }
                    )
                }
            }
            
            // Sección de Información
            item {
                SettingsSection(title = "Información") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Acerca de",
                        subtitle = "Versión 1.0.0",
                        onClick = { showAboutDialog = true }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = "Ayuda y Soporte",
                        subtitle = "Guías de uso y contacto",
                        onClick = { /* Implementar ayuda */ }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Privacidad",
                        subtitle = "Política de privacidad",
                        onClick = { /* Implementar privacidad */ }
                    )
                }
            }
        }
    }
    
    // Diálogos
    if (showSensitivityDialog) {
        SensitivityDialog(
            currentSensitivity = detectionSensitivity,
            onDismiss = { showSensitivityDialog = false },
            onSelect = { 
                settingsManager.setDetectionSensitivity(it)
                showSensitivityDialog = false
            }
        )
    }
    
    if (showTimerDialog) {
        TimerDialog(
            currentTimer = alertTimer,
            onDismiss = { showTimerDialog = false },
            onSelect = { 
                settingsManager.setAlertTimer(it)
                showTimerDialog = false
            }
        )
    }
    
    if (showDelayDialog) {
        DelayDialog(
            currentDelay = monitoringDelay,
            onDismiss = { showDelayDialog = false },
            onSelect = { 
                settingsManager.setMonitoringDelay(it)
                showDelayDialog = false
            }
        )
    }
    
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Abrir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun BatteryStatusCard(
    batteryLevel: Int,
    isLowPowerMode: Boolean,
    isOptimizationDisabled: Boolean,
    onDisableOptimization: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOptimizationDisabled) 
                Color(0xFF4CAF50).copy(alpha = 0.1f) 
            else 
                Color(0xFFFF9800).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nivel de Batería: $batteryLevel%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = if (isLowPowerMode) "Modo de ahorro activado" else "Modo normal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = if (batteryLevel > 50) Icons.Default.BatteryFull 
                                 else if (batteryLevel > 20) Icons.Default.Battery3Bar
                                 else Icons.Default.BatteryAlert,
                    contentDescription = "Batería",
                    tint = if (batteryLevel > 20) Color(0xFF4CAF50) else Color.Red
                )
            }
            
            if (!isOptimizationDisabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "⚠️ Optimización de batería activa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Esto puede afectar el monitoreo en segundo plano",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDisableOptimization,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Desactivar Optimización")
                }
            }
        }
    }
}

@Composable
private fun SensitivityDialog(
    currentSensitivity: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("Baja", "Media", "Alta")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sensibilidad de Detección") },
        text = {
            Column {
                Text("Selecciona la sensibilidad para detectar accidentes:")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSensitivity == option,
                            onClick = { onSelect(option) }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR")
            }
        }
    )
}

@Composable
private fun TimerDialog(
    currentTimer: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val timerOptions = listOf(5, 10, 15, 20, 30, 45, 60)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiempo de Cancelación") },
        text = {
            LazyColumn {
                items(timerOptions) { timer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(timer) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = timer == currentTimer,
                            onClick = { onSelect(timer) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$timer segundos")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun DelayDialog(
    currentDelay: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val delayOptions = listOf(0, 3, 5, 10, 15, 30)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delay de Inicio") },
        text = {
            LazyColumn {
                items(delayOptions) { delay ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(delay) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = delay == currentDelay,
                            onClick = { onSelect(delay) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (delay == 0) "Sin delay" else "$delay segundos")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acerca de AlertaRaven") },
        text = {
            Column {
                Text(
                    text = "AlertaRaven v1.0.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Aplicación de detección automática de accidentes vehiculares con alertas de emergencia.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Características:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "• Detección automática de accidentes\n" +
                          "• Alertas a contactos de emergencia\n" +
                          "• Ubicación GPS en tiempo real\n" +
                          "• Perfil médico integrado\n" +
                          "• Optimización de batería",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Desarrollado para salvar vidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR")
            }
        }
    )
}