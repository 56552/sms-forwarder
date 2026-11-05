package com.ghajni.smsforwarder.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghajni.smsforwarder.R
import com.ghajni.smsforwarder.data.AppPreferences
import com.ghajni.smsforwarder.data.SmsLogDbHelper
import com.ghajni.smsforwarder.data.SmsLogItem
import com.ghajni.smsforwarder.util.SmsSenderHelper

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = AppPreferences(context)
        val dbHelper = SmsLogDbHelper(context)

        // Read all message segments
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "Received SMS_RECEIVED action but messages array was empty.")
            return
        }

        // Group segments by sender
        val senderMap = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "Unknown"
            val body = sms.displayMessageBody ?: ""
            senderMap.getOrPut(sender) { StringBuilder() }.append(body)
        }

        for ((sender, bodyBuilder) in senderMap) {
            val fullBody = bodyBuilder.toString()
            val targetNumber = prefs.targetPhoneNumber
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "Incoming SMS from $sender: $fullBody")

            // Prevent duplicate executions (if both Manifest & Foreground service catch same intent)
            if (isDuplicate(sender, fullBody)) {
                Log.d(TAG, "Ignoring duplicate broadcast for SMS from $sender")
                continue
            }

            // 1. Check if forwarding is enabled
            if (!prefs.isForwardingEnabled) {
                Log.d(TAG, "Forwarding is disabled in settings. Logging and skipping.")
                dbHelper.insertLog(
                    SmsLogItem(
                        sender = sender,
                        body = fullBody,
                        forwardedTo = if (targetNumber.isNotBlank()) targetNumber else "None",
                        timestamp = timestamp,
                        status = "DISABLED (Toggle is OFF)"
                    )
                )
                notifyUi(context)
                showNotification(context, "SMS Received (Forwarding OFF)", "From: $sender")
                continue
            }

            // 2. Validate target number
            if (targetNumber.isBlank()) {
                Log.w(TAG, "Target number is blank. Cannot forward.")
                dbHelper.insertLog(
                    SmsLogItem(
                        sender = sender,
                        body = fullBody,
                        forwardedTo = "N/A",
                        timestamp = timestamp,
                        status = "FAILED (No Target Number)"
                    )
                )
                notifyUi(context)
                showNotification(context, "SMS Not Forwarded", "Target phone number is not configured")
                continue
            }

            // 3. Prevent infinite loops (do not forward our own forwarded messages)
            if (fullBody.contains("[FWD from") || isSameNumber(sender, targetNumber)) {
                Log.w(TAG, "Preventing SMS loop from $sender.")
                continue
            }

            // 4. Keyword filter check
            if (prefs.filterMode == AppPreferences.MODE_KEYWORDS) {
                val matchesKeyword = matchesAnyKeyword(fullBody, prefs.keywords)
                if (!matchesKeyword) {
                    Log.d(TAG, "Message from $sender did not match any keyword filter.")
                    dbHelper.insertLog(
                        SmsLogItem(
                            sender = sender,
                            body = fullBody,
                            forwardedTo = targetNumber,
                            timestamp = timestamp,
                            status = "FILTERED (No Keyword Match)"
                        )
                    )
                    notifyUi(context)
                    showNotification(context, "SMS Skipped (Filtered)", "From $sender did not match keywords")
                    continue
                }
            }

            // 5. Send forwarded SMS
            val isSuccess = SmsSenderHelper.forwardSms(
                context = context,
                sender = sender,
                body = fullBody,
                targetNumber = targetNumber,
                simSlot = prefs.simSlot
            )

            val status = if (isSuccess) "SENT" else "FAILED"
            dbHelper.insertLog(
                SmsLogItem(
                    sender = sender,
                    body = fullBody,
                    forwardedTo = targetNumber,
                    timestamp = timestamp,
                    status = status
                )
            )

            notifyUi(context)

            if (isSuccess) {
                showNotification(context, "SMS Forwarded Successfully", "Forwarded from $sender to $targetNumber")
            } else {
                showNotification(context, "SMS Forwarding Failed", "Failed to send to $targetNumber. Check SIM balance.")
            }
        }
    }

    private fun matchesAnyKeyword(body: String, keywordsCsv: String): Boolean {
        if (keywordsCsv.isBlank()) return true
        val lowerBody = body.lowercase()
        val keywordList = keywordsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (keywordList.isEmpty()) return true

        return keywordList.any { keyword -> lowerBody.contains(keyword) }
    }

    private fun isSameNumber(num1: String, num2: String): Boolean {
        val clean1 = num1.replace("[^0-9]".toRegex(), "")
        val clean2 = num2.replace("[^0-9]".toRegex(), "")
        if (clean1.isEmpty() || clean2.isEmpty()) return false
        return clean1.endsWith(clean2) || clean2.endsWith(clean1)
    }

    private fun notifyUi(context: Context) {
        val updateIntent = Intent(ACTION_LOGS_UPDATED)
        updateIntent.setPackage(context.packageName)
        context.sendBroadcast(updateIntent)
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ALERTS,
                "SMS Forwarding Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_LOGS_UPDATED = "com.ghajni.smsforwarder.ACTION_LOGS_UPDATED"
        const val NOTIF_CHANNEL_ALERTS = "sms_forwarder_alerts"

        private var lastSender = ""
        private var lastBodyHash = 0
        private var lastTimestamp = 0L

        @Synchronized
        fun isDuplicate(sender: String, body: String): Boolean {
            val now = System.currentTimeMillis()
            val hash = body.hashCode()
            if (sender == lastSender && hash == lastBodyHash && (now - lastTimestamp) < 3000) {
                return true
            }
            lastSender = sender
            lastBodyHash = hash
            lastTimestamp = now
            return false
        }
    }
}
