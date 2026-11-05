package com.ghajni.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
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
        if (messages.isNullOrEmpty()) return

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

            // 1. Check if forwarding is enabled
            if (!prefs.isForwardingEnabled) {
                Log.d(TAG, "Forwarding is disabled, skipping message.")
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
                        status = "FAILED (No Target)"
                    )
                )
                notifyUi(context)
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
                            status = "FILTERED"
                        )
                    )
                    notifyUi(context)
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

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_LOGS_UPDATED = "com.ghajni.smsforwarder.ACTION_LOGS_UPDATED"
    }
}

