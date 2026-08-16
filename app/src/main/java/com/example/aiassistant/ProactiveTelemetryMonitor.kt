package com.example.aiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class ProactiveTelemetryMonitor(
    private val context: Context,
    private val onAlertTriggered: (String, AssistantPersona) -> Unit
) {

    private var lastBatteryLevel = 100
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    onAlertTriggered("External power connected, sir. Auxiliary systems charging.", AssistantPersona.JARVIS)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    onAlertTriggered("Running on secondary internal reserves, boss.", AssistantPersona.FRIDAY)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val currentLevel = (level * 100 / scale.toFloat()).toInt()

                    if (currentLevel <= 15 && lastBatteryLevel > 15) {
                        onAlertTriggered("Power levels at 15 percent, sir. Recommend connection to main power grid.", AssistantPersona.JARVIS)
                    }
                    lastBatteryLevel = currentLevel
                }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        isRegistered = true
    }

    fun stopMonitoring() {
        if (isRegistered) {
            context.unregisterReceiver(receiver)
            isRegistered = false
        }
    }
}
