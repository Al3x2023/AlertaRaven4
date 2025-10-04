package com.example.alertaraven4.medical

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.alertaraven4.data.BloodType
import com.example.alertaraven4.data.MedicalProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor de perfil médico del usuario
 */
class MedicalProfileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MedicalProfileManager"
        private const val PREFS_NAME = "medical_profile_prefs"
        private const val KEY_MEDICAL_PROFILE = "medical_profile"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val KEY_PROFILE_COMPLETE = "profile_complete"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Listener para detectar cambios externos en SharedPreferences y recargar flujos
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        try {
            when (key) {
                KEY_MEDICAL_PROFILE -> {
                    // Recargar perfil médico desde almacenamiento
                    val json = sharedPrefs.getString(KEY_MEDICAL_PROFILE, null)
                    if (json != null) {
                        val profile = gson.fromJson(json, MedicalProfile::class.java)
                        _medicalProfile.value = profile
                        updateProfileCompleteness()
                        Log.i(TAG, "Perfil médico actualizado desde SharedPreferences")
                    } else {
                        _medicalProfile.value = null
                        updateProfileCompleteness()
                        Log.i(TAG, "Perfil médico limpiado desde SharedPreferences")
                    }
                }
                KEY_EMERGENCY_CONTACTS -> {
                    // Recargar contactos de emergencia desde almacenamiento
                    val json = sharedPrefs.getString(KEY_EMERGENCY_CONTACTS, null)
                    if (json != null) {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.example.alertaraven4.data.EmergencyContact>>() {}.type
                        val contacts = gson.fromJson<List<com.example.alertaraven4.data.EmergencyContact>>(json, type)
                        _emergencyContacts.value = contacts ?: emptyList()
                        updateProfileCompleteness()
                        Log.i(TAG, "Contactos de emergencia actualizados desde SharedPreferences: ${contacts?.size ?: 0} contactos")
                    } else {
                        _emergencyContacts.value = emptyList()
                        updateProfileCompleteness()
                        Log.i(TAG, "Contactos de emergencia limpiados desde SharedPreferences")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando cambio en SharedPreferences para clave: $key", e)
        }
    }
    
    private val _medicalProfile = MutableStateFlow<MedicalProfile?>(null)
    val medicalProfile: StateFlow<MedicalProfile?> = _medicalProfile.asStateFlow()
    
    private val _emergencyContacts = MutableStateFlow<List<com.example.alertaraven4.data.EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<com.example.alertaraven4.data.EmergencyContact>> = _emergencyContacts.asStateFlow()
    
    private val _isProfileComplete = MutableStateFlow(false)
    val isProfileComplete: StateFlow<Boolean> = _isProfileComplete.asStateFlow()
    
    init {
        loadMedicalProfile()
        loadEmergencyContacts()
        updateProfileCompleteness()
        // Registrar listener para sincronizar cambios realizados por otras instancias
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }
    
    /**
     * Guarda el perfil médico del usuario
     */
    fun saveMedicalProfile(profile: MedicalProfile): Boolean {
        return try {
            val json = gson.toJson(profile)
            prefs.edit()
                .putString(KEY_MEDICAL_PROFILE, json)
                .apply()
            
            _medicalProfile.value = profile
            updateProfileCompleteness()
            
            Log.i(TAG, "Perfil médico guardado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando perfil médico", e)
            false
        }
    }
    
    /**
     * Carga el perfil médico desde almacenamiento
     */
    private fun loadMedicalProfile() {
        try {
            val json = prefs.getString(KEY_MEDICAL_PROFILE, null)
            if (json != null) {
                val profile = gson.fromJson(json, MedicalProfile::class.java)
                _medicalProfile.value = profile
                Log.i(TAG, "Perfil médico cargado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando perfil médico", e)
        }
    }
    
    /**
     * Guarda la lista de contactos de emergencia
     */
    fun saveEmergencyContacts(contacts: List<com.example.alertaraven4.data.EmergencyContact>): Boolean {
        return try {
            val json = gson.toJson(contacts)
            prefs.edit()
                .putString(KEY_EMERGENCY_CONTACTS, json)
                .apply()
            
            _emergencyContacts.value = contacts
            updateProfileCompleteness()
            
            Log.i(TAG, "Contactos de emergencia guardados: ${contacts.size} contactos")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando contactos de emergencia", e)
            false
        }
    }
    
    /**
     * Carga los contactos de emergencia desde almacenamiento
     */
    private fun loadEmergencyContacts() {
        try {
            val json = prefs.getString(KEY_EMERGENCY_CONTACTS, null)
            if (json != null) {
                val type = object : TypeToken<List<com.example.alertaraven4.data.EmergencyContact>>() {}.type
                val contacts = gson.fromJson<List<com.example.alertaraven4.data.EmergencyContact>>(json, type)
                _emergencyContacts.value = contacts ?: emptyList()
                Log.i(TAG, "Contactos de emergencia cargados: ${contacts?.size ?: 0} contactos")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando contactos de emergencia", e)
        }
    }
    
    /**
     * Agrega un contacto de emergencia
     */
    fun addEmergencyContact(contact: com.example.alertaraven4.data.EmergencyContact): Boolean {
        return try {
            val currentContacts = _emergencyContacts.value.toMutableList()
            
            // Si es contacto principal, quitar el flag de otros contactos
            if (contact.isPrimary) {
                for (i in currentContacts.indices) {
                    if (currentContacts[i].isPrimary) {
                        currentContacts[i] = currentContacts[i].copy(isPrimary = false)
                    }
                }
            }
            
            currentContacts.add(contact)
            saveEmergencyContacts(currentContacts)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding emergency contact", e)
            false
        }
    }
    
    /**
     * Elimina un contacto de emergencia
     */
    fun removeEmergencyContact(contact: com.example.alertaraven4.data.EmergencyContact): Boolean {
        return try {
            val currentContacts = _emergencyContacts.value.filter { it.id != contact.id }
            saveEmergencyContacts(currentContacts)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing emergency contact", e)
            false
        }
    }
    
    /**
     * Elimina un contacto de emergencia por ID
     */
    fun removeEmergencyContactById(contactId: String): Boolean {
        return try {
            val currentContacts = _emergencyContacts.value.toMutableList()
            val removed = currentContacts.removeIf { it.id == contactId }
            
            if (removed) {
                saveEmergencyContacts(currentContacts)
            } else {
                Log.w(TAG, "No se encontró contacto con ID: $contactId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing emergency contact", e)
            false
        }
    }
    
    /**
     * Actualiza un contacto de emergencia existente
     */
    fun updateEmergencyContact(oldContact: com.example.alertaraven4.data.EmergencyContact, newContact: com.example.alertaraven4.data.EmergencyContact): Boolean {
        return try {
            val currentContacts = _emergencyContacts.value.toMutableList()
            val index = currentContacts.indexOfFirst { it.id == oldContact.id }
            
            if (index != -1) {
                // Si es contacto principal, quitar el flag de otros contactos
                if (newContact.isPrimary) {
                    for (i in currentContacts.indices) {
                        if (currentContacts[i].isPrimary) {
                            currentContacts[i] = currentContacts[i].copy(isPrimary = false)
                        }
                    }
                }
                
                currentContacts[index] = newContact
                saveEmergencyContacts(currentContacts)
            } else {
                Log.w(TAG, "No se encontró contacto con ID: ${oldContact.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating emergency contact", e)
            false
        }
    }
    
    /**
     * Actualiza un contacto de emergencia existente por ID
     */
    fun updateEmergencyContactById(updatedContact: com.example.alertaraven4.data.EmergencyContact): Boolean {
        return try {
            val currentContacts = _emergencyContacts.value.toMutableList()
            val index = currentContacts.indexOfFirst { it.id == updatedContact.id }
            
            if (index != -1) {
                // Si es contacto principal, quitar el flag de otros contactos
                if (updatedContact.isPrimary) {
                    for (i in currentContacts.indices) {
                        if (currentContacts[i].isPrimary) {
                            currentContacts[i] = currentContacts[i].copy(isPrimary = false)
                        }
                    }
                }
                
                currentContacts[index] = updatedContact
                saveEmergencyContacts(currentContacts)
            } else {
                Log.w(TAG, "No se encontró contacto con ID: ${updatedContact.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating emergency contact", e)
            false
        }
    }
    
    /**
     * Obtiene el perfil médico actual
     */
    fun getCurrentMedicalProfile(): MedicalProfile? {
        return _medicalProfile.value
    }
    
    /**
     * Obtiene los contactos de emergencia actuales
     */
    fun getCurrentEmergencyContacts(): List<com.example.alertaraven4.data.EmergencyContact> {
        return _emergencyContacts.value
    }
    


    /**
     * Genera un resumen médico para emergencias
     */
    fun generateEmergencyMedicalSummary(): String {
        val profile = _medicalProfile.value ?: return "Sin información médica disponible"
        
        val summary = StringBuilder()
        
        // Información básica
        summary.append("=== INFORMACIÓN MÉDICA DE EMERGENCIA ===\n\n")
        
        // Datos personales
        summary.append("DATOS PERSONALES:\n")
        summary.append("Nombre: ${profile.fullName}\n")
        summary.append("Edad: ${profile.age} años\n")
        summary.append("Tipo de sangre: ${getBloodTypeText(profile.bloodType)}\n")
        summary.append("Peso: ${profile.weight} kg\n")
        summary.append("Altura: ${profile.height} cm\n\n")
        
        // Alergias
        if (profile.allergies.isNotEmpty()) {
            summary.append("⚠️ ALERGIAS IMPORTANTES:\n")
            profile.allergies.forEach { allergy ->
                summary.append("• $allergy\n")
            }
            summary.append("\n")
        }
        
        // Condiciones médicas
        if (profile.medicalConditions.isNotEmpty()) {
            summary.append("🏥 CONDICIONES MÉDICAS:\n")
            profile.medicalConditions.forEach { condition ->
                summary.append("• $condition\n")
            }
            summary.append("\n")
        }
        
        // Medicamentos
        if (profile.medications.isNotEmpty()) {
            summary.append("💊 MEDICAMENTOS ACTUALES:\n")
            profile.medications.forEach { medication ->
                summary.append("• $medication\n")
            }
            summary.append("\n")
        }
        
        // Contactos de emergencia
        val contacts = _emergencyContacts.value
        if (contacts.isNotEmpty()) {
            summary.append("📞 CONTACTOS DE EMERGENCIA:\n")
            contacts.forEach { contact ->
                summary.append("• ${contact.name} (${contact.relationship}): ${contact.phoneNumber}\n")
            }
            summary.append("\n")
        }
        
        // Información adicional
        if (profile.additionalNotes.isNotBlank()) {
            summary.append("ℹ️ INFORMACIÓN ADICIONAL:\n")
            summary.append("${profile.additionalNotes}\n\n")
        }
        
        summary.append("Generado por AlertaRaven - ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        return summary.toString()
    }
    
    /**
     * Verifica si el perfil está completo
     */
    private fun updateProfileCompleteness() {
        val profile = _medicalProfile.value
        val contacts = _emergencyContacts.value
        
        val isComplete = profile != null && 
                        profile.fullName.isNotBlank() &&
                        profile.bloodType != BloodType.UNKNOWN &&
                        contacts.isNotEmpty()
        
        _isProfileComplete.value = isComplete
        
        prefs.edit()
            .putBoolean(KEY_PROFILE_COMPLETE, isComplete)
            .apply()
        
        Log.i(TAG, "Perfil completo: $isComplete")
    }
    
    /**
     * Convierte tipo de sangre a texto legible
     */
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
            BloodType.UNKNOWN -> "Desconocido"
        }
    }
    
    /**
     * Exporta el perfil médico como texto para compartir
     */
    fun exportMedicalProfile(): String {
        return generateEmergencyMedicalSummary()
    }
    
    /**
     * Limpia todos los datos médicos
     */
    fun clearAllMedicalData(): Boolean {
        return try {
            prefs.edit()
                .remove(KEY_MEDICAL_PROFILE)
                .remove(KEY_EMERGENCY_CONTACTS)
                .remove(KEY_PROFILE_COMPLETE)
                .apply()
            
            _medicalProfile.value = null
            _emergencyContacts.value = emptyList()
            _isProfileComplete.value = false
            
            Log.i(TAG, "Datos médicos eliminados")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando datos médicos", e)
            false
        }
    }

    /**
     * Debe llamarse cuando ya no se necesite este manager para evitar fugas.
     */
    fun unregisterListener() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
            // No-op
        }
    }
    
    /**
     * Valida un perfil médico antes de guardarlo
     */
    fun validateMedicalProfile(profile: MedicalProfile): List<String> {
        val errors = mutableListOf<String>()
        
        if (profile.fullName.isBlank()) {
            errors.add("El nombre completo es obligatorio")
        }
        
        if (profile.age <= 0 || profile.age > 150) {
            errors.add("La edad debe estar entre 1 y 150 años")
        }
        
        if (profile.weight <= 0 || profile.weight > 500) {
            errors.add("El peso debe estar entre 1 y 500 kg")
        }
        
        if (profile.height <= 0 || profile.height > 300) {
            errors.add("La altura debe estar entre 1 y 300 cm")
        }
        
        return errors
    }
    
    /**
     * Valida un contacto de emergencia
     */
    fun validateEmergencyContact(contact: com.example.alertaraven4.data.EmergencyContact): List<String> {
        val errors = mutableListOf<String>()
        
        if (contact.name.isBlank()) {
            errors.add("El nombre del contacto es obligatorio")
        }
        
        if (contact.phoneNumber.isBlank()) {
            errors.add("El número de teléfono es obligatorio")
        } else if (!isValidPhoneNumber(contact.phoneNumber)) {
            errors.add("El número de teléfono no es válido")
        }
        
        if (contact.relationship.isBlank()) {
            errors.add("La relación con el contacto es obligatoria")
        }
        
        return errors
    }
    
    /**
     * Valida formato de número telefónico
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        return cleanNumber.length >= 10 && cleanNumber.matches(Regex("^\\+?[1-9]\\d{1,14}$"))
    }
}