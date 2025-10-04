package com.example.alertaraven4.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.alertaraven4.api.ApiClient
import com.example.alertaraven4.api.models.EmergencyContact
import com.example.alertaraven4.medical.MedicalProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Sincroniza los contactos de emergencia entre almacenamiento local y backend
 */
class ContactSyncManager(
    private val context: Context,
    private val medicalProfileManager: MedicalProfileManager
) {
    companion object {
        private const val TAG = "ContactSyncManager"
    }

    private val apiClient = ApiClient.getInstance()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun start() {
        // Sincronización inicial y subscripción a cambios
        scope.launch { initialSync() }
        scope.launch { subscribeToChanges() }
    }

    fun stop() {
        try {
            job.cancel()
            Log.d(TAG, "ContactSyncManager detenido y coroutines canceladas")
        } catch (e: Exception) {
            Log.w(TAG, "Error deteniendo ContactSyncManager", e)
        }
    }

    private suspend fun initialSync() {
        try {
            val deviceId = getDeviceId()
            val local = medicalProfileManager.getCurrentEmergencyContacts()
            val remoteResult = apiClient.getContacts(deviceId)
            when (remoteResult) {
                is com.example.alertaraven4.api.models.ApiResult.Success -> {
                    val remote = remoteResult.data
                    if (local.isEmpty() && remote.isNotEmpty()) {
                        // Cargar del backend si local está vacío
                        val mapped = remote.map { apiContact ->
                            com.example.alertaraven4.data.EmergencyContact(
                                name = apiContact.name,
                                phoneNumber = apiContact.phone,
                                relationship = apiContact.relationship ?: "",
                                isPrimary = apiContact.isPrimary,
                                isActive = true
                            )
                        }
                        medicalProfileManager.saveEmergencyContacts(mapped)
                        Log.i(TAG, "Sincronización inicial: importados ${mapped.size} contactos del backend")
                    } else {
                        // Subir los locales al backend (reemplazo)
                        pushLocalContacts()
                    }
                }
                else -> {
                    Log.w(TAG, "No se pudieron obtener contactos remotos en sincronización inicial")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización inicial", e)
        }
    }

    private suspend fun subscribeToChanges() {
        try {
            medicalProfileManager.emergencyContacts.collect { contacts ->
                // Enviar cambios al backend
                pushLocalContacts(contacts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error suscribiendo a cambios de contactos", e)
        }
    }

    private suspend fun pushLocalContacts(localContacts: List<com.example.alertaraven4.data.EmergencyContact>? = null) {
        val deviceId = getDeviceId()
        val contacts = (localContacts ?: medicalProfileManager.getCurrentEmergencyContacts())
        val apiContacts = contacts.map { c ->
            EmergencyContact(
                name = c.name,
                phone = c.phoneNumber,
                relationship = c.relationship,
                isPrimary = c.isPrimary
            )
        }
        when (val result = apiClient.setContacts(deviceId, apiContacts)) {
            is com.example.alertaraven4.api.models.ApiResult.Success -> {
                Log.d(TAG, "Contactos sincronizados con backend: ${apiContacts.size}")
            }
            is com.example.alertaraven4.api.models.ApiResult.Error -> {
                Log.w(TAG, "Error API al sincronizar contactos: ${result.message}")
            }
            is com.example.alertaraven4.api.models.ApiResult.NetworkError -> {
                Log.w(TAG, "Error de red al sincronizar contactos: ${result.exception.message}")
            }
        }
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
}