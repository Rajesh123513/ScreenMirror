package com.screenmirror.app.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.util.concurrent.Executors

class WifiConnectionManager(
    private val context: Context,
    private val onIpAddressFound: (String) -> Unit
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val executor = Executors.newSingleThreadExecutor()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        Log.d(TAG, "Found local IP: $ip")
                        return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return null
    }
    
    fun startServer() {
        executor.execute {
            try {
                serverSocket = ServerSocket(8080)
                isRunning = true
                Log.d(TAG, "Server started on port 8080")
                
                val localIp = getLocalIpAddress()
                localIp?.let { onIpAddressFound(it) }
                
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        Log.d(TAG, "Client connected: ${clientSocket?.inetAddress}")
                        // Handle client connection if needed
                        clientSocket?.close()
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
            }
        }
    }
    
    suspend fun discoverMacDevice(): String? = withContext(Dispatchers.IO) {
        try {
            val localIp = getLocalIpAddress()
            if (localIp == null) {
                Log.e(TAG, "Could not determine local IP")
                return@withContext null
            }
            
            // Extract network prefix (e.g., 192.168.1 from 192.168.1.100)
            val parts = localIp.split(".")
            if (parts.size != 4) {
                Log.e(TAG, "Invalid IP address format: $localIp")
                return@withContext null
            }
            
            val networkPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            val localHostPart = parts[3].toInt()
            
            Log.d(TAG, "Scanning network: $networkPrefix.x")
            
            // Scan common IPs in the network
            val candidates = mutableListOf<String>()
            for (i in 1..254) {
                if (i != localHostPart) {
                    candidates.add("$networkPrefix.$i")
                }
            }
            
            // Try to connect to potential Mac devices
            for (ip in candidates) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, 8080), 500) // 500ms timeout
                    socket.close()
                    Log.d(TAG, "Found device at: $ip")
                    return@withContext ip
                } catch (e: Exception) {
                    // Continue scanning
                }
            }
            
            Log.d(TAG, "No Mac device found on network")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering Mac device", e)
            null
        }
    }
    
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            Log.d(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }
    
    companion object {
        private const val TAG = "WifiConnectionManager"
    }
}

