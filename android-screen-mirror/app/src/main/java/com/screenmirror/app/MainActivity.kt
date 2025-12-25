package com.screenmirror.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.screenmirror.app.airplay.AirPlayReceiver
import com.screenmirror.app.network.NetworkSpeedMonitor
import com.screenmirror.app.network.WifiConnectionManager
import com.screenmirror.app.video.VideoReceiver
import kotlinx.coroutines.launch
import java.net.InetAddress

class MainActivity : AppCompatActivity() {
    
    private lateinit var videoSurface: SurfaceView
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusBar: View
    private lateinit var statusBarText: TextView
    private lateinit var statusBarIndicator: View
    private lateinit var deviceName: TextView
    private lateinit var localhostAddress: TextView
    private lateinit var networkSpeed: TextView
    private lateinit var fpsCounter: TextView
    private lateinit var disconnectButtonBar: Button
    private lateinit var helpText: TextView
    private lateinit var mainContentLayout: View
    
    private var wifiManager: WifiManager? = null
    private var connectionManager: WifiConnectionManager? = null
    private var videoReceiver: VideoReceiver? = null
    private var networkSpeedMonitor: NetworkSpeedMonitor? = null
    private var airPlayReceiver: AirPlayReceiver? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private val PERMISSIONS_REQUEST_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system UI for full screen experience
        hideSystemUI()
        
        setContentView(R.layout.activity_main)
        
        initializeViews()
        checkPermissions()
        initializeComponents()
        setupListeners()
        startDiscovery()
    }
    
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    private fun initializeViews() {
        videoSurface = findViewById(R.id.videoSurface)
        statusText = findViewById(R.id.statusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusBar = findViewById(R.id.statusBar)
        statusBarText = findViewById(R.id.statusBarText)
        statusBarIndicator = findViewById(R.id.statusBarIndicator)
        deviceName = findViewById(R.id.deviceName)
        localhostAddress = findViewById(R.id.localhostAddress)
        networkSpeed = findViewById(R.id.networkSpeed)
        fpsCounter = findViewById(R.id.fpsCounter)
        disconnectButtonBar = findViewById(R.id.disconnectButtonBar)
        helpText = findViewById(R.id.helpText)
        mainContentLayout = findViewById(R.id.mainContentLayout)
        
        // Set device name
        deviceName.text = Build.MODEL.ifEmpty { "Android TV" }
        
        // Setup disconnect button in status bar
        disconnectButtonBar.setOnClickListener {
            disconnect()
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO
        )

        // Add CHANGE_WIFI_MULTICAST_STATE if running on SDK < 33 or if it's available
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
             permissions.add(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
        } else {
             permissions.add(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
             // permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES) // Consider for Android 13+
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }
    
    private fun initializeComponents() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        // Acquire Multicast Lock for mDNS discovery
        multicastLock = wifiManager?.createMulticastLock("multicastLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()

        connectionManager = WifiConnectionManager(this) { ipAddress ->
            runOnUiThread {
                updateLocalhostAddress(ipAddress)
            }
        }
        
        videoSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Pass surface to AirPlayReceiver if it's active
                airPlayReceiver?.setSurface(holder.surface)
                
                // Keep videoReceiver for legacy/custom connections if needed
                videoReceiver = VideoReceiver(
                    holder.surface,
                    onConnectionStateChanged = { isConnected ->
                        runOnUiThread {
                            updateConnectionStatus(isConnected)
                        }
                    },
                    onFpsUpdate = { fps ->
                        runOnUiThread {
                            updateFps(fps)
                        }
                    }
                )
            }
            
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                videoReceiver?.disconnect()
                videoReceiver = null
                airPlayReceiver?.setSurface(holder.surface) // Will likely be invalid or null
            }
        })
        
        networkSpeedMonitor = NetworkSpeedMonitor(this) { speed ->
            runOnUiThread {
                updateNetworkSpeed(speed)
            }
        }
    }
    
    private fun setupListeners() {
        // No longer need manual connect buttons
    }
    
    private fun startDiscovery() {
        lifecycleScope.launch {
            try {
                val localIp = connectionManager?.getLocalIpAddress()
                localIp?.let {
                    updateLocalhostAddress(it)
                }
                
                // Start AirPlay receiver - makes TV discoverable by Mac
                airPlayReceiver = AirPlayReceiver(this@MainActivity) { socket ->
                    // Mac connected via AirPlay Handshake (TCP)
                    runOnUiThread {
                        handleMacConnection(socket)
                    }
                }
                
                // Ensure we pass the surface if it's already created
                if (videoSurface.holder.surface.isValid) {
                     airPlayReceiver?.setSurface(videoSurface.holder.surface)
                }
                
                airPlayReceiver?.start()
                
                // Start network monitoring
                networkSpeedMonitor?.startMonitoring()
                
                // Update UI to show waiting for Mac
                statusText.text = getString(R.string.waiting_for_mac_connection)
                helpText.text = getString(R.string.use_mac_screen_mirroring)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting discovery", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleMacConnection(socket: java.net.Socket) {
        try {
            Log.d(TAG, "Mac connected via AirPlay")
            updateConnectionStatus(true)
            
            // Note: Data is now flowing via UDP to AirPlayReceiver -> H264Decoder
            // We just update UI here
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Mac connection", e)
        }
    }
    
    private fun disconnect() {
        videoReceiver?.disconnect()
        updateConnectionStatus(false)
        // Ideally restart AirPlay receiver listener if needed
    }
    
    private fun updateLocalhostAddress(ipAddress: String) {
        localhostAddress.text = ipAddress
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            // Connected state
            statusText.text = getString(R.string.connected)
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_connected)
            statusBarText.text = getString(R.string.mirroring_active)
            statusBarIndicator.setBackgroundResource(R.drawable.status_indicator_connected)
            
            // Hide main content, show status bar
            mainContentLayout.visibility = View.GONE
            statusBar.visibility = View.VISIBLE
            videoSurface.visibility = View.VISIBLE
            
            helpText.visibility = View.GONE
        } else {
            // Disconnected state
            statusText.text = getString(R.string.ready_to_connect)
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_disconnected)
            
            // Show main content, hide status bar
            mainContentLayout.visibility = View.VISIBLE
            statusBar.visibility = View.GONE
            videoSurface.visibility = View.GONE
            
            helpText.visibility = View.VISIBLE
        }
    }
    
    private fun updateNetworkSpeed(speed: Double) {
        networkSpeed.text = "${String.format("%.1f", speed)} ${getString(R.string.mbps)}"
    }
    
    private fun updateFps(fps: Double) {
        fpsCounter.text = "${String.format("%.0f", fps)} ${getString(R.string.fps)}"
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startDiscovery()
            } else {
                Toast.makeText(this, "Permissions required for screen mirroring", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        videoReceiver?.disconnect()
        networkSpeedMonitor?.stopMonitoring()
        connectionManager?.stopServer()
        airPlayReceiver?.stop()
        if (multicastLock != null && multicastLock!!.isHeld) {
            multicastLock!!.release()
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}
