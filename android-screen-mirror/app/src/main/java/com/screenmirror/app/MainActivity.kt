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
    
    private lateinit var videoSurface: org.webrtc.SurfaceViewRenderer
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusBar: View
    private lateinit var statusBarText: TextView
    private lateinit var statusBarIndicator: View
    private lateinit var deviceName: TextView
    private lateinit var localhostAddress: TextView
    private lateinit var networkSpeed: TextView
    private lateinit var fpsCounter: TextView
    private lateinit var bitrateDisplay: TextView
    private lateinit var disconnectButtonBar: Button
    private lateinit var helpText: TextView
    private lateinit var mainContentLayout: View
    
    private var wifiManager: WifiManager? = null
    private var connectionManager: WifiConnectionManager? = null
    private var videoReceiver: VideoReceiver? = null
    private var networkSpeedMonitor: NetworkSpeedMonitor? = null
    private var airPlayReceiver: AirPlayReceiver? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var signalingServer: com.screenmirror.app.signaling.SignalingServer? = null
    private var webRtcClient: com.screenmirror.app.webrtc.WebRTCClient? = null
    
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
        bitrateDisplay = findViewById(R.id.bitrateDisplay)
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
        
        // Initialize SurfaceViewRenderer for WebRTC remote rendering
        videoSurface.init(org.webrtc.EglBase.create().eglBaseContext, null)
        videoSurface.setMirror(false)
        videoSurface.setZOrderMediaOverlay(true)

        // Keep videoReceiver for legacy/custom connections if needed (not used for WebRTC)
        videoReceiver = null
        
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
                
                // Start WebSocket signaling server for WebRTC-based mirroring
                signalingServer = com.screenmirror.app.signaling.SignalingServer(9000)
                signalingServer?.start()

                // Create WebRTC client to handle offers from browser
                webRtcClient = com.screenmirror.app.webrtc.WebRTCClient(this@MainActivity)
                webRtcClient?.init()
                // Provide the SurfaceViewRenderer for rendering remote video
                webRtcClient?.setSurfaceViewRenderer(videoSurface)
                webRtcClient?.createPeerConnection()

                // Forward ICE candidates from WebRTC client to signaling
                webRtcClient?.onIceCandidate = { candidate ->
                    try {
                        val c = mapOf(
                            "type" to "candidate",
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "candidate" to candidate.sdp
                        )
                        // Broadcast to connected clients (usually single)
                        signalingServer?.broadcast(c)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending local candidate", e)
                    }
                }

                signalingServer?.onMessage = { msg, conn ->
                    try {
                        val gson = com.google.gson.Gson()
                        val map = gson.fromJson(msg, Map::class.java)
                        val type = map["type"] as? String
                        when (type) {
                            "offer" -> {
                                val sdp = map["sdp"] as? String ?: ""
                                webRtcClient?.handleOffer(sdp, { answerSdp ->
                                    val resp = mapOf("type" to "answer", "sdp" to answerSdp)
                                    signalingServer?.send(conn, resp)
                                }, { candidate ->
                                    val c = mapOf(
                                        "type" to "candidate",
                                        "sdpMid" to candidate.sdpMid,
                                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                                        "candidate" to candidate.sdp
                                    )
                                    signalingServer?.send(conn, c)
                                })
                            }
                            "candidate" -> {
                                val sdpMid = map["sdpMid"] as? String ?: ""
                                val sdpMLineIndex = (map["sdpMLineIndex"] as? Double)?.toInt() ?: 0
                                val candidate = map["candidate"] as? String ?: ""
                                webRtcClient?.addIceCandidate(sdpMid, sdpMLineIndex, candidate)
                            }
                            "ice-config" -> {
                                // Recreate PeerConnection with provided ICE servers
                                try {
                                    val iceList = map["iceServers"] as? List<*>
                                    val iceServers = mutableListOf<org.webrtc.PeerConnection.IceServer>()
                                    iceList?.forEach { item ->
                                        val m = item as? Map<*, *>
                                        val url = m?.get("urls") as? String
                                        val username = m?.get("username") as? String
                                        val credential = m?.get("credential") as? String
                                        if (!url.isNullOrEmpty()) {
                                            val builder = org.webrtc.PeerConnection.IceServer.builder(url)
                                            if (!username.isNullOrEmpty()) builder.setUsername(username)
                                            if (!credential.isNullOrEmpty()) builder.setPassword(credential)
                                            iceServers.add(builder.createIceServer())
                                        }
                                    }
                                    webRtcClient?.close()
                                    webRtcClient = com.screenmirror.app.webrtc.WebRTCClient(this@MainActivity)
                                    webRtcClient?.init()
                                    webRtcClient?.setSurfaceViewRenderer(videoSurface)
                                    webRtcClient?.createPeerConnection(iceServers)
                                    webRtcClient?.onIceCandidate = { candidate ->
                                        val c = mapOf(
                                            "type" to "candidate",
                                            "sdpMid" to candidate.sdpMid,
                                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                                            "candidate" to candidate.sdp
                                        )
                                        signalingServer?.broadcast(c)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error applying ice-config", e)
                                }
                            }
                            "set-bitrate" -> {
                                val kbps = (map["kbps"] as? Double)?.toInt() ?: 0
                                webRtcClient?.maxBitrateKbps = kbps
                                runOnUiThread {
                                    bitrateDisplay.text = if (kbps > 0) "${kbps} kbps" else "auto"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error handling signaling message", e)
                    }
                }
                
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
        signalingServer?.stop()
        webRtcClient?.close()
        if (multicastLock != null && multicastLock!!.isHeld) {
            multicastLock!!.release()
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}
