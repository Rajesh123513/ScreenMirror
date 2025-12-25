package com.screenmirror.app.airplay

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.util.concurrent.Executors

/**
 * mDNS/Bonjour service discovery using JmDNS
 * Makes the Android TV discoverable as "Android TV" in Mac's AirPlay menu
 */
class MDNSService(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var jmdns: JmDNS? = null
    private var isRunning = false
    private val deviceName = "Android TV"
    
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
                // Use a safe hostname without spaces
                jmdns = JmDNS.create(localIpAddress, "AndroidTV")

                val deviceId = getDeviceIdAsMac()
                // Features bitmask: 0x5A7FFFF7
                // This indicates support for video v2, audio, screen mirroring, etc.
                val features = "0x5A7FFFF7" 
                
                // 1. Register AirPlay Service (_airplay._tcp.local.)
                val airplayProps = hashMapOf(
                    "deviceid" to deviceId,
                    "features" to "$features,0x1E",
                    "model" to "AppleTV3,2", // AppleTV3,2 is widely compatible
                    "srcvers" to "220.68",
                    "flags" to "0x4",
                    "vv" to "2",
                    "pk" to "b07727d6f6cd6e08b58c98a7e206fc2848a9187319202e77840132b70f058043",
                    "pi" to "b07727d6f6cd6e08b58c98a7e206fc2848a9187319202e77840132b70f058043"
                )
                
                val airplayService = ServiceInfo.create(
                    "_airplay._tcp.local.",
                    deviceName,
                    AIRPLAY_PORT,
                    0,
                    0,
                    airplayProps
                )
                jmdns?.registerService(airplayService)
                Log.d(TAG, "Registered AirPlay service: $deviceName on port $AIRPLAY_PORT")

                // 2. Register AirTunes/RAOP Service (_raop._tcp.local.)
                // Name format MUST be "MAC@DeviceName" for RAOP
                // MAC address must be without colons
                val macNoColons = deviceId.replace(":", "")
                val raopName = "$macNoColons@$deviceName"
                
                val raopProps = hashMapOf(
                    "ch" to "2",
                    "cn" to "0,1,2,3",
                    "da" to "true",
                    "et" to "0,1",
                    "md" to "0,1,2",
                    "pw" to "false",
                    "sr" to "44100",
                    "ss" to "16",
                    "sv" to "false",
                    "tp" to "UDP",
                    "vn" to "65537",
                    "vs" to "220.68",
                    "sf" to "0x4",
                    "am" to "AppleTV3,2"
                )
                
                // NOTE: Use port 7000 (AirPlay) for RAOP too if 5000 is causing issues on some Macs
                // But standard RAOP is 5000+. Let's stick to 5000 unless we see conflicts.
                
                val raopService = ServiceInfo.create(
                    "_raop._tcp.local.",
                    raopName,
                    AIRTUNES_PORT,
                    0, 
                    0,
                    raopProps
                )
                jmdns?.registerService(raopService)
                Log.d(TAG, "Registered RAOP service: $raopName on port $AIRTUNES_PORT")
                
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
                    // This prevents picking up internal docker/vm IPs on some setups
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
        // Generate a stable MAC-like string from Android ID
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "0000000000000000"
        
        // Take first 12 chars or pad
        val cleanId = (androidId + "000000000000").take(12)
        val sb = StringBuilder()
        for (i in 0 until 12 step 2) {
            if (i > 0) sb.append(":")
            sb.append(cleanId.substring(i, i + 2))
        }
        // Mac address should be uppercased for consistency
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
