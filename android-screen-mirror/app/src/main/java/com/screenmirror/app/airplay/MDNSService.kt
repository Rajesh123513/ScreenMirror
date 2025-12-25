package com.screenmirror.app.airplay

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.util.concurrent.Executors

/**
 * mDNS/Bonjour service discovery using JmDNS
 * Makes the Android TV discoverable as the actual device name in Mac's AirPlay menu
 */
class MDNSService(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var jmdns: JmDNS? = null
    private var isRunning = false
    
    // Fetch actual device name
    private val deviceName: String
        get() {
            var name = Settings.Global.getString(context.contentResolver, "device_name")
            if (name.isNullOrEmpty()) {
                name = Build.MODEL
            }
            return name ?: "Android TV"
        }
    
    companion object {
        private const val TAG = "MDNSService"
        private const val AIRPLAY_PORT = 7000
        private const val AIRTUNES_PORT = 5000
    }
    
    fun start() {
        if (isRunning) return
        
        isRunning = true
        executor.execute {
            try {
                // Get local IP address
                val localIpAddress = getLocalIpInetAddress()
                if (localIpAddress == null) {
                    Log.e(TAG, "Could not get local IP address")
                    return@execute
                }

                Log.d(TAG, "Starting JmDNS on address: ${localIpAddress.hostAddress}")
                
                // Create JmDNS instance
                // Hostname must be safe (no spaces, alphanumeric)
                val safeHostName = deviceName.replace("[^a-zA-Z0-9]".toRegex(), "")
                jmdns = JmDNS.create(localIpAddress, safeHostName)

                val currentDeviceName = deviceName
                val deviceId = getDeviceIdAsMac()
                // Features bitmask: 0x5A7FFFF7
                val features = "0x5A7FFFF7" 
                
                // Instead of falsely advertising as AirPlay/RAOP (which requires
                // implementing the full/proprietary AirPlay protocol), register a
                // custom service so clients won't try native AirPlay and fail.
                val customPort = 9000
                val customProps = hashMapOf(
                    "deviceid" to deviceId,
                    "model" to "AndroidTV",
                    "name" to currentDeviceName,
                    "version" to "1.0"
                )

                val customService = ServiceInfo.create(
                    "_screen-mirror._tcp.local.",
                    currentDeviceName,
                    customPort,
                    0,
                    0,
                    customProps
                )
                jmdns?.registerService(customService)
                Log.d(TAG, "Registered custom screen-mirror service: $currentDeviceName on port $customPort")
                
                // Keep the service alive
                while (isRunning) {
                   try {
                       Thread.sleep(10000)
                   } catch (e: InterruptedException) {
                       break
                   }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting mDNS service", e)
            }
        }
    }
    
    private fun getLocalIpInetAddress(): InetAddress? {
         try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // Filter for IPv4 and ensure it's a site-local address (192.168.x.x, 10.x.x.x, etc.)
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address && address.isSiteLocalAddress) {
                        return address
                    }
                }
            }
            
            // Fallback if no site-local address found
            val interfaces2 = NetworkInterface.getNetworkInterfaces()
             while (interfaces2.hasMoreElements()) {
                val networkInterface = interfaces2.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address
                    }
                }
             }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return null
    }

    private fun getDeviceIdAsMac(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "0000000000000000"
        
        val cleanId = (androidId + "000000000000").take(12)
        val sb = StringBuilder()
        for (i in 0 until 12 step 2) {
            if (i > 0) sb.append(":")
            sb.append(cleanId.substring(i, i + 2))
        }
        return sb.toString().uppercase()
    }
    
    fun stop() {
        isRunning = false
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
            jmdns = null
            Log.d(TAG, "mDNS service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mDNS service", e)
        }
    }
}
