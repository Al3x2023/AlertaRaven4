package com.example.alertaraven4.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MonitoringConfirmationDialog(
    isVisible: Boolean,
    delaySeconds: Int = 3,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember(isVisible) { mutableStateOf(delaySeconds) }
    var isCountdownActive by remember(isVisible) { mutableStateOf(true) }
    
    // Efecto para el countdown
    LaunchedEffect(isVisible, countdown) {
        if (isVisible && isCountdownActive && countdown > 0) {
            delay(1000)
            countdown--
        } else if (countdown == 0 && isCountdownActive) {
            isCountdownActive = false
        }
    }
    
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Iniciar Monitoreo",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "¿Estás seguro de que quieres iniciar el monitoreo de accidentes?",
                        textAlign = TextAlign.Center
                    )
                    
                    if (isCountdownActive) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Iniciando en:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "$countdown",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Text(
                                    text = "segundos",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Text(
                            text = "El monitoreo se iniciará automáticamente o puedes cancelar.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "✓ Listo para iniciar",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    enabled = !isCountdownActive || countdown == 0
                ) {
                    Text(if (isCountdownActive) "Iniciar Ahora" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCancel()
                        onDismiss()
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
        
        // Auto-confirmar cuando el countdown llegue a 0
        LaunchedEffect(countdown, isCountdownActive) {
            if (countdown == 0 && !isCountdownActive) {
                delay(500) // Pequeña pausa para mostrar el estado "Listo"
                onConfirm()
                onDismiss()
            }
        }
    }
}

@Composable
fun MonitoringValidationDialog(
    isVisible: Boolean,
    missingItems: List<String>,
    onDismiss: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Configuración Incompleta",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Para iniciar el monitoreo necesitas completar:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    missingItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Estas configuraciones son esenciales para el funcionamiento correcto del sistema de emergencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (missingItems.any { it.contains("perfil", ignoreCase = true) }) {
                            onNavigateToProfile()
                        } else {
                            onNavigateToContacts()
                        }
                        onDismiss()
                    }
                ) {
                    Text("Configurar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
}