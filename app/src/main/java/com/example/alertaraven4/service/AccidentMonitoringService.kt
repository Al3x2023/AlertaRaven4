package com.example.alertaraven4.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.alertaraven4.R
import com.example.alertaraven4.alert.EmergencyAlertManager
import com.example.alertaraven4.data.AccidentEvent
import com.example.alertaraven4.data.EmergencyContact
import com.example.alertaraven4.data.MedicalProfile
import com.example.alertaraven4.medical.MedicalProfileManager
import com.example.alertaraven4.location.LocationManager
import com.example.alertaraven4.sensors.AccidentDetector
import com.example.alertaraven4.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Servicio en primer plano para monitoreo continuo de accidentes
 */
class AccidentMonitoringService : Service() {
    
    companion object {
        private const val TAG = "AccidentMonitoringService"
        private const val NOTIFICATION_ID = 1000
        private const val CHANNEL_ID = "accident_monitoring"
        
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_TOGGLE_MONITORING = "TOGGLE_MONITORING"
        
        fun startService(context: Context) {
            val intent = Intent(context, AccidentMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AccidentMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
        
        fun toggleService(context: Context) {
            val intent = Intent(context, AccidentMonitoringService::class.java).apply {
                action = ACTION_TOGGLE_MONITORING
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    private lateinit var accidentDetector: AccidentDetector
    private lateinit var locationManager: LocationManager
    private lateinit var emergencyAlertManager: EmergencyAlertManager
    private lateinit var medicalProfileManager: MedicalProfileManager
    private lateinit var settingsManager: SettingsManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    // Optimizado: Usar Dispatchers.Default para mejor rendimiento en operaciones intensivas
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var isMonitoring = false
    private var isInitialized = false
    
    // Cache para optimizar rendimiento
    private var lastNotificationUpdate = 0L
    private val NOTIFICATION_UPDATE_INTERVAL = 5000L // 5 segundos
    
    // Receptor para cancelación de alertas
    private val alertCancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.alertaraven4.ALERT_CANCELLED") {
                emergencyAlertManager.cancelAlert()
                Log.i(TAG, "Alerta cancelada desde notificación")
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Servicio de monitoreo creado")
        
        // Inicialización optimizada en background thread
        serviceScope.launch {
            try {
                // Inicializar componentes de forma asíncrona
                locationManager = LocationManager(this@AccidentMonitoringService)
                accidentDetector = AccidentDetector(this@AccidentMonitoringService)
                medicalProfileManager = MedicalProfileManager(this@AccidentMonitoringService)
                settingsManager = SettingsManager(this@AccidentMonitoringService)
                emergencyAlertManager = EmergencyAlertManager(this@AccidentMonitoringService, locationManager)
                
                // Configurar sincronización de datos entre MedicalProfileManager y EmergencyAlertManager
                setupDataSynchronization()
                
                // Configurar wake lock para mantener el servicio activo
                mainScope.launch {
                    setupWakeLock()
                }
                
                // Observar detecciones de accidentes
                observeAccidentDetection()
                
                // Marcar como inicializado
                isInitialized = true
                Log.i(TAG, "Inicialización del servicio completada")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error durante la inicialización: ${e.message}", e)
                stopSelf()
            }
        }
        
        // Operaciones que deben ejecutarse en el hilo principal
        mainScope.launch {
            // Registrar receptor para cancelación de alertas
            val filter = IntentFilter("com.example.alertaraven4.ALERT_CANCELLED")
            ContextCompat.registerReceiver(this@AccidentMonitoringService, alertCancelReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            
            // Crear canal de notificación
            createNotificationChannel()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_TOGGLE_MONITORING -> toggleMonitoring()
            else -> {
                // Inicio por defecto
                if (!isMonitoring) {
                    startMonitoring()
                }
            }
        }
        
        return START_STICKY // Reiniciar si el sistema mata el servicio
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.i(TAG, "Destruyendo servicio de monitoreo")
        
        // Limpiar recursos de forma optimizada
        stopMonitoring()
        
        // Cancelar coroutines de forma ordenada
        serviceScope.cancel()
        mainScope.cancel()
        
        try {
            unregisterReceiver(alertCancelReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error al desregistrar receptor", e)
        }
        
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        
        try {
            accidentDetector.stopMonitoring()
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener detector de accidentes", e)
        }
        
        try {
            locationManager.cleanup()
        } catch (e: Exception) {
            Log.w(TAG, "Error al limpiar location manager", e)
        }
        
        try {
            if (::emergencyAlertManager.isInitialized) {
                emergencyAlertManager.cleanup()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al limpiar emergency alert manager", e)
        }
        
        // MedicalProfileManager no requiere limpieza explícita
    }
    
    /**
     * Inicia el monitoreo de accidentes
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "El monitoreo ya está activo")
            return
        }
        
        // Verificar que la inicialización esté completa
        if (!isInitialized) {
            Log.w(TAG, "Servicio aún no inicializado, esperando...")
            // Intentar nuevamente después de un breve delay
            serviceScope.launch {
                delay(1000) // Esperar 1 segundo
                if (isInitialized) {
                    startMonitoring()
                } else {
                    Log.e(TAG, "Timeout esperando inicialización")
                    stopSelf()
                }
            }
            return
        }
        
        Log.i(TAG, "Iniciando monitoreo de accidentes")
        
        // Verificar sensores disponibles
        if (!accidentDetector.areSensorsAvailable()) {
            Log.e(TAG, "Sensores no disponibles")
            stopSelf()
            return
        }
        
        // Iniciar servicio en primer plano
        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        
        // Adquirir wake lock
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
        
        // Iniciar componentes
        val sensorStarted = accidentDetector.startMonitoring()
        val locationStarted = locationManager.startLocationUpdates()
        
        if (sensorStarted) {
            isMonitoring = true
            
            // Observar ubicación y actualizar detector
            serviceScope.launch {
                locationManager.currentLocation.collect { location ->
                    location?.let { accidentDetector.updateLocation(it) }
                }
            }
            
            Log.i(TAG, "Monitoreo iniciado exitosamente")
        } else {
            Log.e(TAG, "Error iniciando sensores")
            stopSelf()
        }
    }
    
    /**
     * Detiene el monitoreo
     */
    private fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        Log.i(TAG, "Deteniendo monitoreo de accidentes")
        
        isMonitoring = false
        
        // Detener componentes
        accidentDetector.stopMonitoring()
        locationManager.stopLocationUpdates()
        
        // Liberar wake lock
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        
        // Detener servicio en primer plano
        stopForeground(true)
        stopSelf()
    }
    
    /**
     * Alterna el estado del monitoreo
     */
    private fun toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }
    
    /**
     * Observa las detecciones de accidentes - Optimizado para latencia mínima
     */
    private fun observeAccidentDetection() {
        serviceScope.launch {
            accidentDetector.accidentDetected.collect { accidentEvent ->
                accidentEvent?.let { event ->
                    Log.w(TAG, "Accidente detectado: ${event.type} con confianza ${event.confidence}")
                    
                    // Procesar alerta de emergencia en paralelo para reducir latencia
                    launch {
                        emergencyAlertManager.triggerEmergencyAlert(event)
                    }
                    
                    // Resetear detector inmediatamente para próxima detección
                    launch {
                        accidentDetector.resetAccidentDetection()
                    }
                    
                    // Actualizar notificación de forma optimizada
                    launch {
                        updateNotificationForAlertOptimized(event)
                    }
                }
            }
        }
    }
    
    /**
     * Configura el wake lock
     */
    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlertaRaven::AccidentMonitoringWakeLock"
        )
    }
    
    /**
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Accidentes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para el monitoreo de accidentes"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Crea la notificación de monitoreo
     */
    private fun createMonitoringNotification(): Notification {
        val stopIntent = Intent(this, AccidentMonitoringService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AlertaRaven Activo")
            .setContentText("Monitoreando accidentes en segundo plano")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Detener",
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * Actualiza la notificación cuando se detecta un accidente - Optimizado
     */
    private fun updateNotificationForAlertOptimized(accidentEvent: AccidentEvent) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle de actualizaciones para evitar spam
        if (currentTime - lastNotificationUpdate < NOTIFICATION_UPDATE_INTERVAL) {
            return
        }
        
        lastNotificationUpdate = currentTime
        
        // Ejecutar en hilo principal para operaciones de UI
        mainScope.launch {
            val notification = NotificationCompat.Builder(this@AccidentMonitoringService, CHANNEL_ID)
                .setContentTitle("⚠️ Accidente Detectado")
                .setContentText("Tipo: ${getAccidentTypeText(accidentEvent.type)} - Confianza: ${String.format("%.1f", accidentEvent.confidence * 100)}%")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            // Volver a la notificación normal después de 10 segundos
            serviceScope.launch {
                delay(10000)
                if (isMonitoring) {
                    mainScope.launch {
                        notificationManager.notify(NOTIFICATION_ID, createMonitoringNotification())
                    }
                }
            }
        }
    }
    
    /**
     * Actualiza la notificación cuando se detecta un accidente (método legacy)
     */
    private fun updateNotificationForAlert(accidentEvent: AccidentEvent) {
        updateNotificationForAlertOptimized(accidentEvent)
    }
    
    /**
     * Configura la sincronización de datos entre MedicalProfileManager y EmergencyAlertManager
     */
    private fun setupDataSynchronization() {
        serviceScope.launch {
            // Sincronizar contactos de emergencia
            medicalProfileManager.emergencyContacts.collect { contacts ->
                emergencyAlertManager.updateEmergencyContacts(contacts)
                Log.d(TAG, "Contactos sincronizados con EmergencyAlertManager: ${contacts.size} contactos")
            }
        }
        
        serviceScope.launch {
            // Sincronizar perfil médico
            medicalProfileManager.medicalProfile.collect { profile ->
                profile?.let { medicalProfile ->
                    emergencyAlertManager.updateMedicalProfile(medicalProfile)
                    Log.d(TAG, "Perfil médico sincronizado con EmergencyAlertManager")
                }
            }
        }
        
        // Sincronizar configuraciones de SettingsManager con EmergencyAlertManager
        serviceScope.launch {
            settingsManager.autoCallEnabled.collect { autoCallEnabled ->
                val currentSettings = emergencyAlertManager.alertSettings.value
                val updatedSettings = currentSettings.copy(makeCall = autoCallEnabled)
                emergencyAlertManager.updateAlertSettings(updatedSettings)
                Log.d(TAG, "Configuración de llamadas automáticas actualizada: $autoCallEnabled")
            }
        }
        
        // Configurar alertas con valores iniciales desde SettingsManager
        val initialAlertSettings = com.example.alertaraven4.data.AlertSettings(
            makeCall = settingsManager.isAutoCallEnabled()
        )
        emergencyAlertManager.updateAlertSettings(initialAlertSettings)
        Log.d(TAG, "Configuración de alertas inicializada desde SettingsManager")
    }
    
    /**
     * Convierte tipo de accidente a texto
     */
    private fun getAccidentTypeText(type: com.example.alertaraven4.data.AccidentType): String {
        return when (type) {
            com.example.alertaraven4.data.AccidentType.COLLISION -> "Colisión"
            com.example.alertaraven4.data.AccidentType.SUDDEN_STOP -> "Frenado brusco"
            com.example.alertaraven4.data.AccidentType.ROLLOVER -> "Volcadura"
            com.example.alertaraven4.data.AccidentType.FALL -> "Caída"
            com.example.alertaraven4.data.AccidentType.UNKNOWN -> "Desconocido"
        }
    }
    

}