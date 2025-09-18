package com.example.alertaraven4.alert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Servicio para manejar la cancelación de alertas de emergencia
 */
class AlertCancelService : Service() {
    
    companion object {
        private const val TAG = "AlertCancelService"
        const val ACTION_CANCEL_ALERT = "com.example.alertaraven4.CANCEL_ALERT"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        
        // El servicio se detiene automáticamente después de procesar
        stopSelf(startId)
        
        return START_NOT_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL_ALERT -> {
                Log.i(TAG, "Procesando cancelación de alerta")
                
                // Aquí se comunicaría con el EmergencyAlertManager
                // Por ahora, enviamos un broadcast local
                val cancelBroadcast = Intent("com.example.alertaraven4.ALERT_CANCELLED")
                cancelBroadcast.setPackage(packageName)
                sendBroadcast(cancelBroadcast)
                
                Log.i(TAG, "Alerta cancelada exitosamente")
            }
        }
    }
}