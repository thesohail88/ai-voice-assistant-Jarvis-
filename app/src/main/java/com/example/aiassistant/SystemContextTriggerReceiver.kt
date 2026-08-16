package com.example.aiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.util.Log

class SystemContextTriggerReceiver : BroadcastReceiver() {

    companion object {
        var currentBatteryLevel: Int = 100
        var isCharging: Boolean = false
        var currentWifiSSID: String = "Disconnected"

        fun getSystemContextSnapshot(context: Context): String {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            
            return "Battery: $battery%, Charging: $isCharging, Network: ${if (isWifi) "WiFi ($currentWifiSSID)" else "Cellular/Offline"}"
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                currentBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info: WifiInfo? = wifiManager.connectionInfo
                currentWifiSSID = info?.ssid?.replace("\"", "") ?: "Disconnected"
            }
        }
    }
}
