package com.ghajni.smsforwarder.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import com.ghajni.smsforwarder.data.AppPreferences

object SmsSenderHelper {

    private const val TAG = "SmsSenderHelper"

    /**
     * Forwards an incoming SMS body from [sender] to the configured [targetNumber].
     * Returns true if queued successfully to the SMS radio.
     */
    fun forwardSms(
        context: Context,
        sender: String,
        body: String,
        targetNumber: String,
        simSlot: Int = 0
    ): Boolean {
        if (targetNumber.isBlank()) {
            Log.e(TAG, "Cannot forward SMS: target phone number is empty.")
            return false
        }

        val formattedMessage = "[FWD from $sender]:\n$body"

        return try {
            val smsManager = getSmsManagerForSlot(context, simSlot)
            val parts = smsManager.divideMessage(formattedMessage)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetNumber, null, formattedMessage, null, null)
            }
            Log.i(TAG, "SMS successfully forwarded to $targetNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $targetNumber", e)
            false
        }
    }

    /**
     * Obtains the SmsManager instance, taking into account dual-SIM preferences if available.
     */
    @SuppressLint("MissingPermission")
    private fun getSmsManagerForSlot(context: Context, simSlot: Int): SmsManager {
        if (simSlot > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subscriptionManager =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList: List<SubscriptionInfo>? =
                    subscriptionManager?.activeSubscriptionInfoList

                if (!activeList.isNullOrEmpty()) {
                    val targetIndex = simSlot - 1 // 1 -> 0, 2 -> 1
                    if (targetIndex in activeList.indices) {
                        val subId = activeList[targetIndex].subscriptionId
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java)
                                .createForSubscriptionId(subId)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getSmsManagerForSubscriptionId(subId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve specific SIM subscription, falling back to default.", e)
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}

