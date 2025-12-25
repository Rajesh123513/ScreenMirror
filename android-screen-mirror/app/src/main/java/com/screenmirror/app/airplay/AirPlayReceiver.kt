package com.screenmirror.app.airplay

import android.content.Context
import android.util.Log
import android.view.Surface
import com.screenmirror.app.video.H264Decoder
import com.screenmirror.app.video.VideoDecryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.*
import java.util.concurrent.Executors

/**
 * AirPlay Receiver - Makes Android TV discoverable as AirPlay device
 * Mac can connect using built-in Screen Mirroring without installing anything
 */
class AirPlayReceiver(
    private val context: Context,
    private val onConnectionReceived: (Socket) -> Unit
) {
    private val executor = Executors.newFixedThreadPool(12) // Increased thread pool for time sync
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var mdnsService: MDNSService? = null
    private val timeSync = TimeSync()
    
    // Video Decoder & Decryptor
    private var decoder: H264Decoder? = null
    private var decryptor: VideoDecryptor = VideoDecryptor()
    private var surface: Surface? = null
    
    // Data sockets
    private var videoDataSocket: DatagramSocket? = null
    private var videoControlSocket: DatagramSocket? = null
    private var audioDataSocket: DatagramSocket? = null
    private var audioControlSocket: DatagramSocket? = null
    
    companion object {
        private const val TAG = "AirPlayReceiver"
        private const val AIRPLAY_PORT = 7000
        // Data ports (Dynamic in real implementation, fixed for simplicity here)
        private const val VIDEO_DATA_PORT = 7010
        private const val VIDEO_CONTROL_PORT = 7011
        private const val AUDIO_DATA_PORT = 7012
        private const val AUDIO_CONTROL_PORT = 7013
    }
    
    fun setSurface(surface: Surface) {
        this.surface = surface
        this.decoder = H264Decoder(surface)
    }
    
    fun start() {
        if (isRunning) return
        
        isRunning = true
        executor.execute {
            try {
                // Start AirPlay server on port 7000
                serverSocket = ServerSocket(AIRPLAY_PORT)
                serverSocket?.reuseAddress = true
                
                Log.d(TAG, "AirPlay receiver started on port $AIRPLAY_PORT")
                
                // Start mDNS service discovery
                mdnsService = MDNSService(context)
                mdnsService?.start()
                
                // Pre-open data ports to ensure availability
                startDataListeners()
                
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            Log.d(TAG, "AirPlay connection from: ${socket.remoteSocketAddress}")
                            handleAirPlayConnection(socket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AirPlay receiver", e)
            }
        }
    }
    
    private fun startDataListeners() {
        try {
            // Video Data (UDP)
            if (videoDataSocket == null || videoDataSocket?.isClosed == true) {
                videoDataSocket = DatagramSocket(VIDEO_DATA_PORT)
                videoDataSocket?.reuseAddress = true
                startVideoListener(videoDataSocket!!)
            }
            
            // Video Control (UDP) - Handle TimeSync
            if (videoControlSocket == null || videoControlSocket?.isClosed == true) {
                videoControlSocket = DatagramSocket(VIDEO_CONTROL_PORT)
                videoControlSocket?.reuseAddress = true
                startControlListener(videoControlSocket!!, "VideoControl")
            }
            
            // Audio Data (UDP)
            if (audioDataSocket == null || audioDataSocket?.isClosed == true) {
                audioDataSocket = DatagramSocket(AUDIO_DATA_PORT)
                audioDataSocket?.reuseAddress = true
                startSocketListener(audioDataSocket!!, "AudioData")
            }
            
             // Audio Control (UDP) - Handle TimeSync
            if (audioControlSocket == null || audioControlSocket?.isClosed == true) {
                audioControlSocket = DatagramSocket(AUDIO_CONTROL_PORT)
                audioControlSocket?.reuseAddress = true
                startControlListener(audioControlSocket!!, "AudioControl")
            }
            
            Log.d(TAG, "Listening on data ports: $VIDEO_DATA_PORT, $VIDEO_CONTROL_PORT, $AUDIO_DATA_PORT")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting data listeners", e)
        }
    }
    
    private fun startVideoListener(socket: DatagramSocket) {
        executor.execute {
            val buffer = ByteArray(65535)
            val packet = DatagramPacket(buffer, buffer.size)
            Log.d(TAG, "VideoData listener started on port ${socket.localPort}")
            
            // Start Decoder
            decoder?.start()
            
            while (isRunning && !socket.isClosed) {
                try {
                    socket.receive(packet)
                    val len = packet.length
                    if (len > 12) {
                        // 1. Decrypt payload (RTP header is usually clear, payload is encrypted)
                        // Note: decryptor will pass through data if no key set
                        // AirPlay payload usually starts at offset 12
                        
                        val encryptedData = buffer.copyOfRange(12, len)
                        val decryptedData = decryptor.decrypt(encryptedData, 0, encryptedData.size)
                        
                        // 2. Feed to decoder
                        if (decryptedData != null) {
                             decoder?.feedData(decryptedData, decryptedData.size)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        // Log.e(TAG, "Error in VideoData listener", e)
                    }
                }
            }
        }
    }
    
    private fun startControlListener(socket: DatagramSocket, name: String) {
        executor.execute {
            val buffer = ByteArray(65535)
            val packet = DatagramPacket(buffer, buffer.size)
            Log.d(TAG, "$name listener started on port ${socket.localPort}")
            
            while (isRunning && !socket.isClosed) {
                try {
                    socket.receive(packet)
                    // Handle Time Sync packets
                    timeSync.handleSyncPacket(socket, packet)
                } catch (e: Exception) {
                    if (isRunning) {
                         // Log.e(TAG, "Error in $name listener", e)
                    }
                }
            }
        }
    }
    
    private fun startSocketListener(socket: DatagramSocket, name: String) {
        executor.execute {
            val buffer = ByteArray(65535)
            val packet = DatagramPacket(buffer, buffer.size)
            Log.d(TAG, "$name listener started on port ${socket.localPort}")
            
            while (isRunning && !socket.isClosed) {
                try {
                    socket.receive(packet)
                    // Drain buffer
                } catch (e: Exception) {
                    if (isRunning) {
                        // Log.e(TAG, "Error in $name listener", e)
                    }
                }
            }
        }
    }
    
    private fun handleAirPlayConnection(socket: Socket) {
        executor.execute {
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val buffer = ByteArray(8192)
                
                while (isRunning && !socket.isClosed) {
                    // Read request
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break // End of stream
                    
                    val request = String(buffer, 0, bytesRead)
                    Log.d(TAG, "Received request: ${request.lines().firstOrNull()}")
                    
                    // Handle AirPlay RTSP handshake & negotiation
                    if (request.contains("OPTIONS", ignoreCase = true)) {
                        val cseq = getHeader(request, "CSeq")
                        val appleChallenge = getHeader(request, "Apple-Challenge")
                        
                        val response = buildString {
                           append("RTSP/1.0 200 OK\r\n")
                           append("CSeq: $cseq\r\n")
                           append("Public: ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER, POST, GET\r\n")
                           append("Server: AirTunes/220.68\r\n")
                           if (appleChallenge.isNotEmpty()) {
                               append("Apple-Response: \r\n")
                           }
                           append("\r\n")
                        }
                        output.write(response.toByteArray())
                        output.flush()
                    }
                    else if (request.contains("ANNOUNCE", ignoreCase = true)) {
                         val cseq = getHeader(request, "CSeq")
                         val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            append("CSeq: $cseq\r\n")
                            append("Server: AirTunes/220.68\r\n")
                            append("\r\n")
                         }
                         output.write(response.toByteArray())
                         output.flush()
                    }
                    else if (request.contains("SETUP", ignoreCase = true)) {
                         val cseq = getHeader(request, "CSeq")
                         val transport = getHeader(request, "Transport")
                         
                         // Determine which ports to assign based on the request type if possible
                         // For now we return our fixed ports
                         
                         val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            append("CSeq: $cseq\r\n")
                            append("Server: AirTunes/220.68\r\n")
                            // Respond with server ports that we are actually listening on
                            append("Transport: $transport;server_port=$VIDEO_DATA_PORT-$VIDEO_CONTROL_PORT\r\n") 
                            append("Session: 1\r\n")
                            append("\r\n")
                         }
                         output.write(response.toByteArray())
                         output.flush()
                         
                         // Signal connection received
                         onConnectionReceived(socket)
                    }
                    else if (request.contains("RECORD", ignoreCase = true)) {
                         val cseq = getHeader(request, "CSeq")
                         val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            append("CSeq: $cseq\r\n")
                            append("Server: AirTunes/220.68\r\n")
                            append("Audio-Latency: 0\r\n")
                            append("\r\n")
                         }
                         output.write(response.toByteArray())
                         output.flush()
                    }
                    else if (request.contains("FLUSH", ignoreCase = true)) {
                         val cseq = getHeader(request, "CSeq")
                         val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            append("CSeq: $cseq\r\n")
                            append("Server: AirTunes/220.68\r\n")
                            append("\r\n")
                         }
                         output.write(response.toByteArray())
                         output.flush()
                    }
                    else if (request.contains("TEARDOWN", ignoreCase = true)) {
                         val cseq = getHeader(request, "CSeq")
                         val response = buildString {
                            append("RTSP/1.0 200 OK\r\n")
                            append("CSeq: $cseq\r\n")
                            append("Server: AirTunes/220.68\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                         }
                         output.write(response.toByteArray())
                         output.flush()
                         socket.close()
                         break
                    }
                    else if (request.startsWith("GET") || request.startsWith("POST")) {
                        handleHTTPRequest(socket, request, output)
                        // HTTP usually closes after response unless Keep-Alive
                         if (!request.contains("Keep-Alive", ignoreCase = true)) {
                             // Keep socket open for RTSP/AirPlay, but typically HTTP is single shot here
                             // Do nothing, let loop continue
                         }
                    }
                    else {
                         // Unknown, just ok it to keep alive
                         val response = "RTSP/1.0 200 OK\r\n\r\n"
                         output.write(response.toByteArray())
                         output.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling AirPlay connection", e)
                try {
                    socket.close()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    private fun getHeader(request: String, headerName: String): String {
        val lines = request.split("\r\n")
        for (line in lines) {
            if (line.startsWith("$headerName:", ignoreCase = true)) {
                return line.substring(headerName.length + 1).trim()
            }
        }
        return ""
    }
    
    private fun handleHTTPRequest(socket: Socket, request: String, output: java.io.OutputStream) {
        try {
            if (request.contains("/server-info", ignoreCase = true)) {
                val deviceId = getDeviceIdAsMac()
                val responseBody = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>deviceid</key>
                        <string>$deviceId</string>
                        <key>features</key>
                        <integer>1518354423</integer>
                        <key>model</key>
                        <string>AppleTV3,2</string>
                        <key>protovers</key>
                        <string>1.0</string>
                        <key>srcvers</key>
                        <string>220.68</string>
                    </dict>
                    </plist>
                """.trimIndent()
                
                val response = """
                    HTTP/1.1 200 OK
                    Content-Type: text/x-apple-plist+xml
                    Content-Length: ${responseBody.length}
                    Server: AirTunes/220.68
                    
                    $responseBody
                """.trimIndent()
                
                output.write(response.toByteArray())
                output.flush()
            } else {
                val response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
                output.write(response.toByteArray())
                output.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling HTTP request", e)
        }
    }
    
    private fun getDeviceIdAsMac(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
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
            decoder?.stop()
            serverSocket?.close()
            videoDataSocket?.close()
            videoControlSocket?.close()
            audioDataSocket?.close()
            audioControlSocket?.close()
            mdnsService?.stop()
            Log.d(TAG, "AirPlay receiver stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AirPlay receiver", e)
        }
    }
}
