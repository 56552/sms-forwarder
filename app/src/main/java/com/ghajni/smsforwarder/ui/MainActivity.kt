package com.ghajni.smsforwarder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghajni.smsforwarder.R
import com.ghajni.smsforwarder.data.AppPreferences
import com.ghajni.smsforwarder.data.SmsLogDbHelper
import com.ghajni.smsforwarder.databinding.ActivityMainBinding
import com.ghajni.smsforwarder.receiver.SmsReceiver
import com.ghajni.smsforwarder.service.SmsForwarderService
import com.ghajni.smsforwarder.util.SmsSenderHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var dbHelper: SmsLogDbHelper
    private lateinit var logAdapter: LogAdapter

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "SMS permissions are required for the app to function.",
                Toast.LENGTH_LONG
            ).show()
        }
        updatePermissionStatus()
    }

    // Local receiver to auto-refresh logs when a new SMS arrives
    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        dbHelper = SmsLogDbHelper(this)

        setupViews()
        setupListeners()
        loadPreferences()
        setupRecyclerView()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateBatteryOptimizationStatus()
        loadLogs()
        val filter = IntentFilter(SmsReceiver.ACTION_LOGS_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logUpdateReceiver)
        } catch (_: Exception) {}
    }

    private fun setupViews() {
        // Setup SIM spinner
        val simOptions = listOf("Default SIM", "SIM 1", "SIM 2")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simOptions)
        binding.spinnerSimSlot.adapter = spinnerAdapter
    }

    private fun setupListeners() {
        // Master switch toggle
        binding.switchForwarding.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasRequiredPermissions()) {
                binding.switchForwarding.isChecked = false
                Toast.makeText(this, "Please grant SMS permissions first.", Toast.LENGTH_SHORT).show()
                requestRequiredPermissions()
                return@setOnCheckedChangeListener
            }

            prefs.isForwardingEnabled = isChecked
            updateStatusUi(isChecked)

            if (isChecked) {
                SmsForwarderService.start(this)
            } else {
                SmsForwarderService.stop(this)
            }
        }

        // Save target phone number
        binding.btnSaveTarget.setOnClickListener {
            val phone = binding.etTargetNumber.text.toString().trim()
            if (phone.isEmpty()) {
                binding.tilTargetNumber.error = "Please enter a valid phone number"
                return@setOnClickListener
            }
            binding.tilTargetNumber.error = null
            prefs.targetPhoneNumber = phone
            Toast.makeText(this, "Target number saved: $phone", Toast.LENGTH_SHORT).show()
            if (prefs.isForwardingEnabled) {
                SmsForwarderService.start(this) // Refresh notification
            }
        }

        // Test SMS send button
        binding.btnTestSend.setOnClickListener {
            val target = prefs.targetPhoneNumber
            if (target.isBlank()) {
                Toast.makeText(this, "Save a target number before testing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Send SMS permission is missing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val testMessage = "Test auto-forward message from SMS Auto-Forwarder App."
            val success = SmsSenderHelper.forwardSms(
                context = this,
                sender = "Test-Sender",
                body = testMessage,
                targetNumber = target,
                simSlot = prefs.simSlot
            )

            if (success) {
                Toast.makeText(this, "Test SMS queued successfully to $target!", Toast.LENGTH_LONG).show()
                dbHelper.insertLog(
                    com.ghajni.smsforwarder.data.SmsLogItem(
                        sender = "TEST",
                        body = testMessage,
                        forwardedTo = target,
                        timestamp = System.currentTimeMillis(),
                        status = "SENT"
                    )
                )
                loadLogs()
            } else {
                Toast.makeText(this, "Failed to send test SMS. Check SIM credit/signal.", Toast.LENGTH_LONG).show()
            }
        }

        // Filter Mode Radio Group
        binding.rgFilterMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbFilterKeywords) {
                binding.tilKeywords.visibility = View.VISIBLE
            } else {
                binding.tilKeywords.visibility = View.GONE
            }
        }

        // Save Filters Button
        binding.btnSaveFilters.setOnClickListener {
            val mode = if (binding.rbFilterKeywords.isChecked) {
                AppPreferences.MODE_KEYWORDS
            } else {
                AppPreferences.MODE_ALL
            }
            prefs.filterMode = mode
            prefs.keywords = binding.etKeywords.text.toString().trim()
            prefs.simSlot = binding.spinnerSimSlot.selectedItemPosition
            Toast.makeText(this, "Filter & SIM settings saved!", Toast.LENGTH_SHORT).show()
        }

        // Permission Buttons
        binding.btnGrantPerms.setOnClickListener {
            requestRequiredPermissions()
        }

        binding.btnDisableBatteryOpt.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        // Clear Logs Button
        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all forwarding logs?")
                .setPositiveButton("Clear") { _, _ ->
                    dbHelper.clearAllLogs()
                    loadLogs()
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.isForwardingEnabled
        binding.switchForwarding.isChecked = isEnabled
        updateStatusUi(isEnabled)

        binding.etTargetNumber.setText(prefs.targetPhoneNumber)
        binding.etKeywords.setText(prefs.keywords)

        if (prefs.filterMode == AppPreferences.MODE_KEYWORDS) {
            binding.rbFilterKeywords.isChecked = true
            binding.tilKeywords.visibility = View.VISIBLE
        } else {
            binding.rbForwardAll.isChecked = true
            binding.tilKeywords.visibility = View.GONE
        }

        binding.spinnerSimSlot.setSelection(prefs.simSlot.coerceIn(0, 2))
    }

    private fun updateStatusUi(isActive: Boolean) {
        if (isActive) {
            binding.tvStatusBadge.text = "ACTIVE"
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.tvStatusDesc.text = "Auto-forwarder is running in background and monitoring SMS."
        } else {
            binding.tvStatusBadge.text = "INACTIVE"
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.tvStatusDesc.text = "Toggle the switch above to activate auto-forwarding."
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
    }

    private fun loadLogs() {
        val logs = dbHelper.getAllLogs(100)
        if (logs.isEmpty()) {
            binding.tvEmptyLogs.visibility = View.VISIBLE
            binding.rvLogs.visibility = View.GONE
        } else {
            binding.tvEmptyLogs.visibility = View.GONE
            binding.rvLogs.visibility = View.VISIBLE
            logAdapter.updateData(logs)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val required = getRequiredPermissionsList()
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissionsList(): List<String> {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    private fun checkAndRequestPermissions() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
        }
    }

    private fun requestRequiredPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissionsList().toTypedArray())
    }

    private fun updatePermissionStatus() {
        val granted = hasRequiredPermissions()
        if (granted) {
            binding.ivPermStatus.setImageResource(R.drawable.ic_check)
            binding.tvPermStatus.text = "All required permissions granted"
            binding.btnGrantPerms.visibility = View.GONE
        } else {
            binding.ivPermStatus.setImageResource(R.drawable.ic_warning)
            binding.tvPermStatus.text = "Permissions missing (SMS, Phone State)"
            binding.btnGrantPerms.visibility = View.VISIBLE
        }
    }

    private fun updateBatteryOptimizationStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        } else {
            true
        }

        if (isIgnoring) {
            binding.ivBatteryStatus.setImageResource(R.drawable.ic_check)
            binding.tvBatteryStatus.text = "Battery optimization disabled (Background safe)"
            binding.btnDisableBatteryOpt.visibility = View.GONE
        } else {
            binding.ivBatteryStatus.setImageResource(R.drawable.ic_warning)
            binding.tvBatteryStatus.text = "App subject to OEM background sleep"
            binding.btnDisableBatteryOpt.visibility = View.VISIBLE
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to standard battery settings
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }
}

