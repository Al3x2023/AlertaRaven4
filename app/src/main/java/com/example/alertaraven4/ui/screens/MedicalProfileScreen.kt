package com.example.alertaraven4.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.alertaraven4.ui.icons.Bloodtype
import com.example.alertaraven4.ui.icons.Height
import com.example.alertaraven4.ui.icons.Medication
import com.example.alertaraven4.ui.icons.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.alertaraven4.data.BloodType
import com.example.alertaraven4.data.MedicalProfile
import com.example.alertaraven4.medical.MedicalProfileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalProfileScreen(
    navController: NavHostController,
    medicalProfileManager: MedicalProfileManager
) {
    val currentProfile by medicalProfileManager.medicalProfile.collectAsState()
    
    var fullName by remember { mutableStateOf(currentProfile?.fullName ?: "") }
    var age by remember { mutableStateOf(currentProfile?.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(currentProfile?.weight?.toString() ?: "") }
    var height by remember { mutableStateOf(currentProfile?.height?.toString() ?: "") }
    var bloodType by remember { mutableStateOf(currentProfile?.bloodType ?: BloodType.UNKNOWN) }
    var allergies by remember { mutableStateOf(currentProfile?.allergies?.joinToString(", ") ?: "") }
    var medicalConditions by remember { mutableStateOf(currentProfile?.medicalConditions?.joinToString(", ") ?: "") }
    var medications by remember { mutableStateOf(currentProfile?.medications?.joinToString(", ") ?: "") }
    var additionalNotes by remember { mutableStateOf(currentProfile?.additionalNotes ?: "") }
    
    var showBloodTypeDropdown by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil Médico") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showSaveDialog = true }
                    ) {
                        Text("GUARDAR", fontWeight = FontWeight.Bold)
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Información Personal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Nombre Completo *") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it },
                                label = { Text("Edad *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.Cake, contentDescription = null)
                                }
                            )
                            
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("Peso (kg) *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.MonitorWeight, contentDescription = null)
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it },
                                label = { Text("Altura (cm) *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.Height, contentDescription = null)
                                }
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = showBloodTypeDropdown,
                                onExpandedChange = { showBloodTypeDropdown = !showBloodTypeDropdown },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = getBloodTypeText(bloodType),
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("Tipo de Sangre *") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBloodTypeDropdown)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Bloodtype, contentDescription = null)
                                    }
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = showBloodTypeDropdown,
                                    onDismissRequest = { showBloodTypeDropdown = false }
                                ) {
                                    BloodType.values().forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(getBloodTypeText(type)) },
                                            onClick = {
                                                bloodType = type
                                                showBloodTypeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Información Médica",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = allergies,
                            onValueChange = { allergies = it },
                            label = { Text("Alergias (separadas por comas)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            leadingIcon = {
                                Icon(Icons.Default.Warning, contentDescription = null)
                            },
                            placeholder = { Text("Ej: Penicilina, Mariscos, Polen") }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = medicalConditions,
                            onValueChange = { medicalConditions = it },
                            label = { Text("Condiciones Médicas (separadas por comas)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            leadingIcon = {
                                Icon(Icons.Default.LocalHospital, contentDescription = null)
                            },
                            placeholder = { Text("Ej: Diabetes, Hipertensión, Asma") }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = medications,
                            onValueChange = { medications = it },
                            label = { Text("Medicamentos Actuales (separados por comas)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            leadingIcon = {
                                Icon(Icons.Default.Medication, contentDescription = null)
                            },
                            placeholder = { Text("Ej: Metformina 500mg, Losartán 50mg") }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = additionalNotes,
                            onValueChange = { additionalNotes = it },
                            label = { Text("Notas Adicionales") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            leadingIcon = {
                                Icon(Icons.Default.Notes, contentDescription = null)
                            },
                            placeholder = { Text("Información adicional relevante para emergencias") }
                        )
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2196F3).copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Información",
                                tint = Color(0xFF2196F3)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "Información Importante",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2196F3)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• Esta información será enviada a los servicios de emergencia en caso de accidente\n" +
                                  "• Mantén tu perfil actualizado\n" +
                                  "• Los campos marcados con * son obligatorios\n" +
                                  "• Tu información está protegida y solo se comparte en emergencias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Diálogo de confirmación para guardar
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Guardar Perfil Médico") },
            text = { Text("¿Deseas guardar los cambios en tu perfil médico?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveProfile(
                            medicalProfileManager = medicalProfileManager,
                            fullName = fullName,
                            age = age,
                            weight = weight,
                            height = height,
                            bloodType = bloodType,
                            allergies = allergies,
                            medicalConditions = medicalConditions,
                            medications = medications,
                            additionalNotes = additionalNotes
                        )
                        showSaveDialog = false
                        navController.navigateUp()
                    }
                ) {
                    Text("GUARDAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

private fun saveProfile(
    medicalProfileManager: MedicalProfileManager,
    fullName: String,
    age: String,
    weight: String,
    height: String,
    bloodType: BloodType,
    allergies: String,
    medicalConditions: String,
    medications: String,
    additionalNotes: String
) {
    val profile = MedicalProfile(
        fullName = fullName.trim(),
        age = age.toIntOrNull() ?: 0,
        weight = weight.toFloatOrNull() ?: 0f,
        height = height.toFloatOrNull() ?: 0f,
        bloodType = bloodType,
        allergies = allergies.split(",").map { it.trim() }.filter { it.isNotBlank() },
        medicalConditions = medicalConditions.split(",").map { it.trim() }.filter { it.isNotBlank() },
        medications = medications.split(",").map { it.trim() }.filter { it.isNotBlank() },
        additionalNotes = additionalNotes.trim()
    )
    
    medicalProfileManager.saveMedicalProfile(profile)
}

private fun getBloodTypeText(bloodType: BloodType): String {
    return when (bloodType) {
        BloodType.A_POSITIVE -> "A+"
        BloodType.A_NEGATIVE -> "A-"
        BloodType.B_POSITIVE -> "B+"
        BloodType.B_NEGATIVE -> "B-"
        BloodType.AB_POSITIVE -> "AB+"
        BloodType.AB_NEGATIVE -> "AB-"
        BloodType.O_POSITIVE -> "O+"
        BloodType.O_NEGATIVE -> "O-"
        BloodType.UNKNOWN -> "Seleccionar"
    }
}