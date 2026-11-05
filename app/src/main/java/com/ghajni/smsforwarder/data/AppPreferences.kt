package com.ghajni.smsforwarder.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, value).apply()

    var targetPhoneNumber: String
        get() = prefs.getString(KEY_TARGET_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TARGET_PHONE, value.trim()).apply()

    var filterMode: String
        get() = prefs.getString(KEY_FILTER_MODE, MODE_ALL) ?: MODE_ALL
        set(value) = prefs.edit().putString(KEY_FILTER_MODE, value).apply()

    var keywords: String
        get() = prefs.getString(
            KEY_KEYWORDS,
            "OTP, code, bank, alert, debit, credit, verification, balance"
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_KEYWORDS, value.trim()).apply()

    var simSlot: Int
        get() = prefs.getInt(KEY_SIM_SLOT, 0) // 0: Default, 1: SIM 1, 2: SIM 2
        set(value) = prefs.edit().putInt(KEY_SIM_SLOT, value).apply()

    companion object {
        private const val PREFS_NAME = "ghajni_sms_prefs"
        private const val KEY_FORWARDING_ENABLED = "key_forwarding_enabled"
        private const val KEY_TARGET_PHONE = "key_target_phone"
        private const val KEY_FILTER_MODE = "key_filter_mode"
        private const val KEY_KEYWORDS = "key_keywords"
        private const val KEY_SIM_SLOT = "key_sim_slot"

        const val MODE_ALL = "ALL"
        const val MODE_KEYWORDS = "KEYWORDS"
    }
}

