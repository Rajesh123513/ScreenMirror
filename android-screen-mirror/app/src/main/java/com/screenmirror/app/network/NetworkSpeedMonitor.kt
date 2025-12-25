package com.screenmirror.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NetworkSpeedMonitor(
    private val context: Context,
    private val onSpeedUpdate: (Double) -> Unit
) {
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastBytesReceived = 0L
    private var lastTestTime = 0L
    
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        lastBytesReceived = 0L
        lastTestTime = System.currentTimeMillis()
        
        // Initial speed test
        testNetworkSpeed()
        
        // Monitor speed every 5 seconds
        handler.postDelayed(speedMonitorRunnable, 5000)
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(speedMonitorRunnable)
    }
    
    private val speedMonitorRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                testNetworkSpeed()
                handler.postDelayed(this, 5000)
            }
        }
    }
    
    private fun testNetworkSpeed() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()
                val url = URL("https://www.google.com")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                
                val responseCode = connection.responseCode
                val endTime = System.currentTimeMillis()
                val duration = (endTime - startTime).toDouble() / 1000.0 // seconds
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Estimate speed based on connection time
                    // This is a simplified calculation
                    val estimatedSpeed = calculateEstimatedSpeed(duration)
                    onSpeedUpdate(estimatedSpeed)
                } else {
                    // Fallback to WiFi link speed
                    val wifiSpeed = getWifiLinkSpeed()
                    onSpeedUpdate(wifiSpeed)
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error testing network speed", e)
                // Fallback to WiFi link speed
                val wifiSpeed = getWifiLinkSpeed()
                onSpeedUpdate(wifiSpeed)
            }
        }
    }
    
    private fun calculateEstimatedSpeed(duration: Double): Double {
        // Simplified calculation - in real implementation, 
        // you'd measure actual bytes transferred
        // For now, estimate based on connection quality
        return when {
            duration < 0.1 -> 100.0 // Very fast
            duration < 0.5 -> 50.0  // Fast
            duration < 1.0 -> 25.0  // Medium
            else -> 10.0             // Slow
        }
    }
    
    private fun getWifiLinkSpeed(): Double {
        try {
            val connectivityManager = 
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            val linkSpeedMbps = capabilities?.getLinkDownstreamBandwidthKbps()?.div(1000.0)
            return linkSpeedMbps ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi link speed", e)
            return 0.0
        }
    }
    
    companion object {
        private const val TAG = "NetworkSpeedMonitor"
    }
}

