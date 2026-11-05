package com.ghajni.smsforwarder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghajni.smsforwarder.R
import com.ghajni.smsforwarder.data.AppPreferences
import com.ghajni.smsforwarder.receiver.SmsReceiver
import com.ghajni.smsforwarder.ui.MainActivity

class SmsForwarderService : Service() {

    private var dynamicSmsReceiver: SmsReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerDynamicReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = AppPreferences(this)
        if (!prefs.isForwardingEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification(prefs.targetPhoneNumber)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterDynamicReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerDynamicReceiver() {
        if (dynamicSmsReceiver == null) {
            try {
                dynamicSmsReceiver = SmsReceiver()
                val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                    priority = 999
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(dynamicSmsReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(dynamicSmsReceiver, filter)
                }
                Log.d("SmsForwarderService", "Dynamic SmsReceiver registered successfully.")
            } catch (e: Exception) {
                Log.e("SmsForwarderService", "Failed to register dynamic SmsReceiver", e)
            }
        }
    }

    private fun unregisterDynamicReceiver() {
        dynamicSmsReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("SmsForwarderService", "Dynamic SmsReceiver unregistered.")
            } catch (e: Exception) {
                Log.w("SmsForwarderService", "Error unregistering receiver", e)
            }
            dynamicSmsReceiver = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Auto Forwarder Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active forwarding status in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(targetPhone: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (targetPhone.isNotBlank()) {
            "Forwarding active to $targetPhone"
        } else {
            "Forwarding active (Target number not configured)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle("SMS Auto-Forwarder Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "sms_forwarder_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SmsForwarderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsForwarderService::class.java)
            context.stopService(intent)
        }
    }
}
