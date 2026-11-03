package com.phonapps.qrscanner.util

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.widget.Toast

/** Best-effort Wi-Fi handoff. Never connects silently: the user always acts. */
object WifiConnector {

    fun connect(context: Context, ssid: String, password: String, security: String): Boolean {
        if (ssid.isBlank()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .apply {
                    val secured = security.uppercase() != "OPEN" &&
                        security.uppercase() != "NOPASS" &&
                        password.isNotEmpty()
                    if (secured) {
                        if (security.uppercase() == "WPA3") setWpa3Passphrase(password)
                        else setWpa2Passphrase(password)
                    }
                }
                .build()
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val status = wifi?.addNetworkSuggestions(listOf(suggestion))
                ?: WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Toast.makeText(
                    context,
                    "Could not register this network — open Wi-Fi settings to join manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // Pre-Android 10 cannot register suggestions; copy the password so
            // joining from system settings is one paste away.
            IntentActions.copyText(context, "Wi-Fi password", password)
            Toast.makeText(
                context,
                "Password copied — select \"$ssid\" in Wi-Fi settings.",
                Toast.LENGTH_LONG
            ).show()
        }
        return IntentActions.openWifiSettings(context)
    }
}
