package com.example.holdingreview.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.holdingreview.MainActivity
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将新生成的股票预警发送为系统通知。
 */
@Singleton
class StockMonitorNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun notify(alerts: List<MonitorAlert>) {
        val pushAlerts = alerts
            .filter { it.level == MonitorAlertLevel.CRITICAL || it.level == MonitorAlertLevel.WARNING }
            .take(5)
        if (pushAlerts.isEmpty()) return
        ensureChannel()
        if (!canPostNotifications()) return
        val manager = NotificationManagerCompat.from(context)
        pushAlerts.forEach { alert ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(alert.title)
                .setContentText(alert.message.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
                .setContentIntent(openAppIntent(alert.id))
                .setAutoCancel(true)
                .setPriority(if (alert.level == MonitorAlertLevel.CRITICAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(alert.id.hashCode(), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "股票预警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "股票监控规则触发时的提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(alertId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_ALERT_ID, alertId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "stock_monitor_alerts"
    }
}
