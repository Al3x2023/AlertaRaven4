package com.example.alertaraven4.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.alertaraven4.data.EmergencyContact
import com.example.alertaraven4.medical.MedicalProfileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    navController: NavHostController,
    medicalProfileManager: MedicalProfileManager
) {
    val currentProfile by medicalProfileManager.medicalProfile.collectAsState()
    val emergencyContacts by medicalProfileManager.emergencyContacts.collectAsState()
    
    var showAddContactDialog by remember { mutableStateOf(false) }
    var contactToEdit by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos de Emergencia") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = Color(0xFFE91E63)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar Contacto",
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (emergencyContacts.isEmpty()) {
                EmptyContactsState(
                    onAddContact = { showAddContactDialog = true }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Información",
                                        tint = Color(0xFF4CAF50)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Text(
                                        text = "Información Importante",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "• Se recomienda agregar al menos 2 contactos de emergencia\n" +
                                          "• Los contactos recibirán SMS con tu ubicación en caso de accidente\n" +
                                          "• Asegúrate de que los números sean correctos\n" +
                                          "• Informa a tus contactos sobre esta aplicación",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    items(emergencyContacts) { contact ->
                        ContactCard(
                            contact = contact,
                            onEdit = { contactToEdit = contact },
                            onDelete = { contactToDelete = contact }
                        )
                    }
                }
            }
        }
    }
    
    // Diálogo para agregar/editar contacto
    if (showAddContactDialog || contactToEdit != null) {
        AddEditContactDialog(
            contact = contactToEdit,
            onDismiss = {
                showAddContactDialog = false
                contactToEdit = null
            },
            onSave = { contact ->
                if (contactToEdit != null) {
                    medicalProfileManager.updateEmergencyContact(contactToEdit!!, contact)
                } else {
                    medicalProfileManager.addEmergencyContact(contact)
                }
                showAddContactDialog = false
                contactToEdit = null
            }
        )
    }
    
    // Diálogo de confirmación para eliminar
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Eliminar Contacto") },
            text = { Text("¿Estás seguro de que deseas eliminar a ${contact.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicalProfileManager.removeEmergencyContact(contact)
                        contactToDelete = null
                    }
                ) {
                    Text("ELIMINAR", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

@Composable
private fun EmptyContactsState(
    onAddContact: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ContactPhone,
            contentDescription = "Sin contactos",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No hay contactos de emergencia",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Agrega contactos que serán notificados en caso de emergencia",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAddContact,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE91E63)
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Agregar Contacto")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = contact.relationship,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2196F3)
                    )
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = Color(0xFF2196F3)
                        )
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = Color.Red
                        )
                    }
                }
            }
            
            if (contact.isPrimary) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Contacto principal",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Contacto Principal",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFC107),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditContactDialog(
    contact: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phoneNumber by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var relationship by remember { mutableStateOf(contact?.relationship ?: "") }
    var isPrimary by remember { mutableStateOf(contact?.isPrimary ?: false) }
    
    val isEditing = contact != null
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (isEditing) "Editar Contacto" else "Agregar Contacto") 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Número de Teléfono *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    placeholder = { Text("Ej: +1234567890") }
                )
                
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relación *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Group, contentDescription = null)
                    },
                    placeholder = { Text("Ej: Familiar, Amigo, Médico") }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Contacto Principal",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (isPrimary) {
                    Text(
                        text = "El contacto principal será notificado primero",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && phoneNumber.isNotBlank() && relationship.isNotBlank()) {
                        val newContact = EmergencyContact(
                            id = contact?.id ?: System.currentTimeMillis().toString(),
                            name = name.trim(),
                            phoneNumber = phoneNumber.trim(),
                            relationship = relationship.trim(),
                            isPrimary = isPrimary
                        )
                        onSave(newContact)
                    }
                },
                enabled = name.isNotBlank() && phoneNumber.isNotBlank() && relationship.isNotBlank()
            ) {
                Text(if (isEditing) "ACTUALIZAR" else "AGREGAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        }
    )
}