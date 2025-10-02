package com.example.alertaraven4.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.alertaraven4.R
import com.example.alertaraven4.data.*
import com.example.alertaraven4.location.LocationManager
import com.example.alertaraven4.api.AlertApiService
import com.example.alertaraven4.api.AlertSendResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestor de alertas de emergencia
 */
class EmergencyAlertManager(
    private val context: Context,
    private val locationManager: LocationManager
) {
    companion object {
        private const val TAG = "EmergencyAlertManager"
        private const val NOTIFICATION_CHANNEL_ID = "emergency_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val CANCEL_ACTION = "CANCEL_ALERT"
        private const val CONFIRM_ACTION = "CONFIRM_ALERT"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
    
    // Servicio de API para enviar alertas al servidor
    private val apiService = AlertApiService(context)
    
    // Referencia al ringtone para poder detenerlo
    private var currentRingtone: android.media.Ringtone? = null
    
    private val _currentAlert = MutableStateFlow<EmergencyAlert?>(null)
    val currentAlert: StateFlow<EmergencyAlert?> = _currentAlert
    
    private val _alertSettings = MutableStateFlow(AlertSettings())
    val alertSettings: StateFlow<AlertSettings> = _alertSettings
    
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts
    
    private val _medicalProfile = MutableStateFlow(MedicalProfile())
    val medicalProfile: StateFlow<MedicalProfile> = _medicalProfile
    
    private var cancelTimerJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        createNotificationChannel()
        setupApiMonitoring()
    }
    
    /**
     * Inicia una alerta de emergencia
     */
    suspend fun triggerEmergencyAlert(accidentEvent: AccidentEvent) {
        if (_currentAlert.value?.status == AlertStatus.PENDING) {
            Log.w(TAG, "Ya hay una alerta pendiente")
            return
        }
        
        val location = locationManager.getEmergencyLocation()
        val alert = EmergencyAlert(
            accidentEvent = accidentEvent,
            location = location,
            medicalInfo = _medicalProfile.value
        )
        
        _currentAlert.value = alert
        
        Log.i(TAG, "Alerta de emergencia iniciada: ${accidentEvent.type}")
        
        // Mostrar notificación de cancelación
        showCancelNotification(alert)
        
        // Reproducir sonido y vibración
        if (_alertSettings.value.alertSound) {
            playAlertSound()
        }
        
        if (_alertSettings.value.vibration) {
            triggerVibration()
        }
        
        // Iniciar temporizador de cancelación
        startCancelTimer(alert)
    }
    
    /**
     * Inicia el temporizador de cancelación de 15 segundos
     */
    private fun startCancelTimer(alert: EmergencyAlert) {
        cancelTimerJob?.cancel()
        
        cancelTimerJob = coroutineScope.launch {
            var timeRemaining = _alertSettings.value.cancelTimeoutSeconds
            
            while (timeRemaining > 0 && _currentAlert.value?.status == AlertStatus.PENDING) {
                _currentAlert.value = _currentAlert.value?.copy(cancelTimeRemaining = timeRemaining)
                
                delay(1000) // Esperar 1 segundo
                timeRemaining--
            }
            
            // Si llegamos aquí y la alerta sigue pendiente, enviar automáticamente
            if (_currentAlert.value?.status == AlertStatus.PENDING) {
                confirmAlert()
            }
        }
    }
    
    /**
     * Cancela la alerta actual
     */
    fun cancelAlert() {
        val currentAlert = _currentAlert.value
        if (currentAlert?.status != AlertStatus.PENDING) {
            return
        }
        
        cancelTimerJob?.cancel()
        _currentAlert.value = currentAlert.copy(status = AlertStatus.CANCELLED)
        
        // Detener sonido de alerta
        stopAlertSound()
        
        // Cancelar notificación
        notificationManager.cancel(NOTIFICATION_ID)
        
        Log.i(TAG, "Alerta cancelada por el usuario")
        
        // Limpiar alerta después de un tiempo
        coroutineScope.launch {
            delay(5000)
            if (_currentAlert.value?.status == AlertStatus.CANCELLED) {
                _currentAlert.value = null
            }
        }
    }
    
    /**
     * Confirma la alerta y envía notificaciones
     */
    private suspend fun confirmAlert() {
        val currentAlert = _currentAlert.value ?: return
        
        if (currentAlert.status != AlertStatus.PENDING) {
            return
        }
        
        cancelTimerJob?.cancel()
        
        // Detener sonido de alerta
        stopAlertSound()
        
        Log.i(TAG, "Confirmando alerta de emergencia")
        
        // Actualizar estado
        _currentAlert.value = currentAlert.copy(status = AlertStatus.CONFIRMED)
        
        // Enviar alerta a la API (sin bloquear el flujo principal)
        coroutineScope.launch {
            sendAlertToApi(currentAlert)
        }
        
        // Enviar alertas a contactos (funcionalidad original)
        val success = sendEmergencyNotifications(currentAlert)
        
        // Actualizar estado final
        _currentAlert.value = currentAlert.copy(
            status = if (success) AlertStatus.SENT else AlertStatus.FAILED
        )
        
        // Cancelar notificación de cancelación
        notificationManager.cancel(NOTIFICATION_ID)
        
        // Mostrar notificación de confirmación
        showConfirmationNotification(currentAlert, success)
    }
    
    /**
     * Envía notificaciones a todos los contactos de emergencia
     */
    private suspend fun sendEmergencyNotifications(alert: EmergencyAlert): Boolean {
        val contacts = _emergencyContacts.value.filter { it.isActive }
        
        if (contacts.isEmpty()) {
            Log.w(TAG, "No hay contactos de emergencia configurados")
            return false
        }
        
        val message = buildEmergencyMessage(alert)
        var successCount = 0
        
        for (contact in contacts) {
            try {
                if (_alertSettings.value.sendSMS) {
                    val smsSuccess = sendSMS(contact.phoneNumber, message)
                    if (smsSuccess) successCount++
                }
                
                // Realizar llamada automática si está habilitado
                if (_alertSettings.value.makeCall) {
                    val callSuccess = makeEmergencyCall(contact.phoneNumber)
                    if (callSuccess) {
                        Log.i(TAG, "Llamada automática iniciada a ${contact.name}")
                        // Solo hacer una llamada al primer contacto para evitar múltiples llamadas simultáneas
                        break
                    }
                }
                
                // Pequeña pausa entre envíos
                delay(500)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando alerta a ${contact.name}", e)
            }
        }
        
        Log.i(TAG, "Alertas enviadas: $successCount de ${contacts.size}")
        return successCount > 0
    }
    
    /**
     * Construye el mensaje de emergencia
     */
    private fun buildEmergencyMessage(alert: EmergencyAlert): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date(alert.timestamp))
        
        val message = StringBuilder()
        message.append("🚨 ALERTA DE EMERGENCIA 🚨\n\n")
        message.append("Se ha detectado un posible accidente vehicular.\n")
        message.append("Tipo: ${getAccidentTypeText(alert.accidentEvent.type)}\n")
        message.append("Hora: $timestamp\n")
        
        // Agregar ubicación si está disponible
        alert.location?.let { location ->
            if (_alertSettings.value.sendLocation) {
                message.append("\nUbicación:\n")
                message.append(locationManager.formatLocation(location))
                message.append("\nMapa: ${locationManager.getGoogleMapsLink(location)}\n")
            }
        }
        
        // Agregar información médica si está configurado
        if (_alertSettings.value.includeMedicalInfo) {
            val medical = alert.medicalInfo
            if (medical != null && medical.fullName.isNotEmpty()) {
                message.append("\nInformación médica:\n")
                message.append("Nombre: ${medical.fullName}\n")
                
                if (medical.bloodType != BloodType.UNKNOWN) {
                    message.append("Tipo de sangre: ${medical.bloodType.displayName}\n")
                }
                
                if (medical.allergies.isNotEmpty()) {
                    message.append("Alergias: ${medical.allergies.joinToString(", ")}\n")
                }
                
                if (medical.medicalConditions.isNotEmpty()) {
                    message.append("Condiciones médicas: ${medical.medicalConditions.joinToString(", ")}\n")
                }
                
                if (medical.emergencyMedicalInfo.isNotEmpty()) {
                    message.append("Info adicional: ${medical.emergencyMedicalInfo}\n")
                }
            }
        }
        
        message.append("\nEsta es una alerta automática de AlertaRaven.")
        
        return message.toString()
    }
    
    /**
     * Envía un SMS
     */
    private fun sendSMS(phoneNumber: String, message: String): Boolean {
        if (!hasSMSPermission()) {
            Log.e(TAG, "Sin permisos para enviar SMS")
            return false
        }
        
        return try {
            val smsManager = SmsManager.getDefault()
            
            // Dividir mensaje si es muy largo
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            
            Log.i(TAG, "SMS enviado a $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SMS a $phoneNumber", e)
            false
        }
    }
    
    /**
     * Muestra notificación para cancelar alerta
     */
    private fun showCancelNotification(alert: EmergencyAlert) {
        val cancelIntent = Intent(context, AlertCancelReceiver::class.java).apply {
            action = CANCEL_ACTION
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚠️ Accidente Detectado")
            .setContentText("Se enviará alerta en ${alert.cancelTimeRemaining}s. Toca para cancelar.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "CANCELAR", cancelPendingIntent)
            .setSound(null) // No reproducir sonido desde la notificación, solo desde currentRingtone
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Muestra notificación de confirmación
     */
    private fun showConfirmationNotification(alert: EmergencyAlert, success: Boolean) {
        val title = if (success) "✅ Alerta Enviada" else "❌ Error al Enviar"
        val text = if (success) {
            "Contactos de emergencia notificados"
        } else {
            "Error enviando alertas. Verifica configuración."
        }
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    /**
     * Reproduce sonido de alerta
     */
    private fun playAlertSound() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // Detener sonido anterior si existe
                currentRingtone?.let { ringtone ->
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                        Log.d(TAG, "Deteniendo sonido anterior")
                    }
                }
                currentRingtone = null
                
                // Crear nuevo ringtone
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                currentRingtone = RingtoneManager.getRingtone(context, uri)
                
                currentRingtone?.let { ringtone ->
                    if (!ringtone.isPlaying) {
                        ringtone.play()
                        Log.i(TAG, "Sonido de alerta iniciado")
                    }
                } ?: Log.e(TAG, "No se pudo crear el ringtone")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error reproduciendo sonido de alerta", e)
                currentRingtone = null
            }
        }
    }
    
    /**
     * Detiene el sonido de alerta
     */
    private fun stopAlertSound() {
        // Asegurar que se ejecute en el hilo principal
        coroutineScope.launch(Dispatchers.Main) {
            try {
                currentRingtone?.let { ringtone ->
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                        Log.i(TAG, "Sonido de alerta detenido")
                    } else {
                        Log.d(TAG, "El ringtone no estaba reproduciéndose")
                    }
                } ?: Log.d(TAG, "No hay ringtone activo para detener")
                
                currentRingtone = null
                Log.d(TAG, "Referencia de ringtone limpiada")
            } catch (e: Exception) {
                Log.e(TAG, "Error deteniendo sonido de alerta", e)
                // Forzar limpieza de la referencia incluso si hay error
                currentRingtone = null
            }
        }
    }
    
    /**
     * Activa vibración
     */
    private fun triggerVibration() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        }
    }
    
    /**
     * Crea el canal de notificaciones
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alertas de Emergencia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para alertas de emergencia por accidentes"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    null
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Realiza una llamada automática de emergencia
     */
    private fun makeEmergencyCall(phoneNumber: String): Boolean {
        if (!hasCallPermission()) {
            Log.e(TAG, "Sin permisos para realizar llamadas")
            return false
        }
        
        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(callIntent)
            Log.i(TAG, "Llamada automática iniciada a $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error realizando llamada automática a $phoneNumber", e)
            false
        }
    }
    
    /**
     * Verifica permisos de SMS
     */
    private fun hasSMSPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Verifica si tiene permisos para realizar llamadas
     */
    private fun hasCallPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Convierte tipo de accidente a texto legible
     */
    private fun getAccidentTypeText(type: AccidentType): String {
        return when (type) {
            AccidentType.COLLISION -> "Colisión"
            AccidentType.SUDDEN_STOP -> "Frenado brusco"
            AccidentType.ROLLOVER -> "Volcadura"
            AccidentType.FALL -> "Caída"
            AccidentType.UNKNOWN -> "Desconocido"
        }
    }
    
    /**
     * Actualiza configuración de alertas
     */
    fun updateAlertSettings(settings: AlertSettings) {
        _alertSettings.value = settings
    }
    
    /**
     * Actualiza contactos de emergencia
     */
    fun updateEmergencyContacts(contacts: List<EmergencyContact>) {
        _emergencyContacts.value = contacts
    }
    
    /**
     * Actualiza perfil médico
     */
    fun updateMedicalProfile(profile: MedicalProfile) {
        _medicalProfile.value = profile
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        cancelTimerJob?.cancel()
        stopAlertSound()
        apiService.cleanup()
        coroutineScope.cancel()
    }
    
    /**
     * Configura el monitoreo de resultados de la API
     */
    private fun setupApiMonitoring() {
        coroutineScope.launch {
            apiService.alertResults.collect { result ->
                when (result) {
                    is AlertSendResult.Success -> {
                        Log.i(TAG, "Alerta enviada exitosamente a la API: ${result.response.alertId}")
                    }
                    is AlertSendResult.Error -> {
                        Log.w(TAG, "Error enviando alerta a la API: ${result.message}")
                    }
                    is AlertSendResult.ValidationError -> {
                        Log.e(TAG, "Error de validación enviando alerta a la API: ${result.errors}")
                    }
                }
            }
        }
    }
    
    /**
     * Envía la alerta a la API
     */
    private suspend fun sendAlertToApi(alert: EmergencyAlert) {
        try {
            Log.d(TAG, "Enviando alerta a la API...")
            
            val result = apiService.sendEmergencyAlert(
                accidentEvent = alert.accidentEvent,
                location = alert.location,
                medicalProfile = alert.medicalInfo,
                emergencyContacts = _emergencyContacts.value
            )
            
            when (result) {
                is AlertSendResult.Success -> {
                    Log.i(TAG, "Alerta enviada exitosamente a la API: ${result.response.alertId}")
                }
                is AlertSendResult.Error -> {
                    Log.w(TAG, "Error enviando alerta a la API: ${result.message}")
                }
                is AlertSendResult.ValidationError -> {
                    Log.e(TAG, "Error de validación: ${result.errors.joinToString(", ")}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción enviando alerta a la API", e)
        }
    }
    
    /**
     * Configura la URL base de la API
     */
    fun setApiBaseUrl(url: String) {
        apiService.setApiBaseUrl(url)
        Log.i(TAG, "URL de API configurada: $url")
    }
    
    /**
      * Obtiene estadísticas del servicio de API
      */
     fun getApiServiceStats() = apiService.getServiceStats()
}