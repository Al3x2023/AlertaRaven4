package com.example.alertaraven4.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receptor para manejar la cancelación de alertas desde notificaciones
 */
class AlertCancelReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlertCancelReceiver"
        const val ACTION_CANCEL_ALERT = "com.example.alertaraven4.CANCEL_ALERT"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        
        when (intent.action) {
            ACTION_CANCEL_ALERT, "CANCEL_ALERT" -> {
                Log.i(TAG, "Cancelación de alerta recibida")
                
                // Enviar intent al servicio para cancelar la alerta
                val cancelIntent = Intent(context, AlertCancelService::class.java).apply {
                    action = AlertCancelService.ACTION_CANCEL_ALERT
                }
                
                try {
                    context.startService(cancelIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando servicio de cancelación", e)
                }
            }
        }
    }
}