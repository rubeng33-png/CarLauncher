package com.example.carlauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.carlauncher.data.PrefsManager

class NotificationHelper(private val context: Context) {
    private val CHANNEL_ID = "maint_alerts"

    fun checkAndNotify() {
        val prefs = PrefsManager(context)
        val current = prefs.currentOdometer
        val engRem = (prefs.engineLastChangeKm + prefs.engineIntervalKm) - current
        val gbxRem = (prefs.gearboxLastChangeKm + prefs.gearboxIntervalKm) - current

        if (engRem > 500 && gbxRem > 500) return

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Mantenimiento", NotificationManager.IMPORTANCE_HIGH))

        val intent = Intent(context, MaintenanceActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val msg = if (engRem <= 500) "Motor próximo/vencido" else "Caja próxima/vencida"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Aviso de Mantenimiento")
            .setContentText(msg)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try { NotificationManagerCompat.from(context).notify(1001, notif) } catch (e: SecurityException) {}
    }
}
