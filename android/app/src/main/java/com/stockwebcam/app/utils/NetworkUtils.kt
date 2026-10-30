package com.stockwebcam.app.utils

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Finds the device IP address.
     * Prioritizes Wi-Fi interfaces (wlan0) and Direct USB (ADB forwarding localhost).
     * Does NOT require or rely on USB tethering.
     */
    fun getStreamIpAddress(context: Context): NetworkIpInfo {
        var wifiIp: String? = null
        var fallbackIp: String? = null

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                
                val name = intf.name.lowercase()
                val addrs = Collections.list(intf.inetAddresses)
                
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (name.contains("wlan") || name.contains("wifi")) {
                            wifiIp = hostAddress
                        } else if (fallbackIp == null) {
                            fallbackIp = hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return when {
            wifiIp != null -> NetworkIpInfo(wifiIp, ConnectionType.WIFI)
            fallbackIp != null -> NetworkIpInfo(fallbackIp, ConnectionType.LOCAL_NETWORK)
            else -> NetworkIpInfo("127.0.0.1", ConnectionType.ADB_USB_CABLE)
        }
    }

    enum class ConnectionType {
        ADB_USB_CABLE,
        WIFI,
        LOCAL_NETWORK
    }

    data class NetworkIpInfo(
        val ipAddress: String,
        val type: ConnectionType
    ) {
        fun getStreamUrl(port: Int = 8080): String = "http://$ipAddress:$port"
        fun getAdbUrl(port: Int = 8080): String = "http://localhost:$port"
    }
}
