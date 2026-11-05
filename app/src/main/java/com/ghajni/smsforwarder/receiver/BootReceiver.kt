package com.ghajni.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghajni.smsforwarder.data.AppPreferences
import com.ghajni.smsforwarder.service.SmsForwarderService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Device rebooted, checking SMS forwarder service state...")
            val prefs = AppPreferences(context)
            if (prefs.isForwardingEnabled) {
                Log.d(TAG, "Restarting SmsForwarderService after boot.")
                SmsForwarderService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

