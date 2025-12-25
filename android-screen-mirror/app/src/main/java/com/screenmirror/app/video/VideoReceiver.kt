package com.screenmirror.app.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Socket

class VideoReceiver(
    private val surface: Surface,
    private val onConnectionStateChanged: (Boolean) -> Unit,
    private val onFpsUpdate: ((Double) -> Unit)? = null
) {
    private var tcpSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null
    private var inputStream: DataInputStream? = null
    private var isReceiving = false
    private var frameCount = 0L
    private var lastFpsTime = System.currentTimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Support higher resolutions
    private val MAX_FRAME_SIZE = 20 * 1024 * 1024 // 20MB buffer for 4K frames
    
    fun connect(ipAddress: String, port: Int) {
        // Legacy TCP connection method
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to $ipAddress:$port")
                tcpSocket = Socket(ipAddress, port)
                inputStream = DataInputStream(tcpSocket?.getInputStream())
                isReceiving = true
                
                onConnectionStateChanged(true)
                Log.d(TAG, "Connected successfully")
                
                startReceivingTCP()
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting", e)
                onConnectionStateChanged(false)
            }
        }
    }
    
    fun connectFromSocket(existingSocket: Socket) {
        // AirPlay handshake completed, now handling the stream
        // Note: Real AirPlay mirroring sends encrypted H.264 stream over TCP or UDP
        // This implementation expects a simplified stream for demonstration
        // If the client (Mac/iPhone) is sending real AirPlay Mirroring data,
        // we would need a decryption layer (using FairPlay keys) and an H.264 decoder.
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Using existing socket connection")
                tcpSocket = existingSocket
                inputStream = DataInputStream(tcpSocket?.getInputStream())
                isReceiving = true
                
                onConnectionStateChanged(true)
                Log.d(TAG, "Connected via existing socket")
                
                // For simplified protocols, we might read frames directly
                // For real AirPlay, we'd negotiate decryption and feed MediaCodec
                startReceivingTCP() 
            } catch (e: Exception) {
                Log.e(TAG, "Error using existing socket", e)
                onConnectionStateChanged(false)
            }
        }
    }
    
    private fun startReceivingTCP() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isReceiving && tcpSocket?.isConnected == true) {
                try {
                    // This logic assumes a custom protocol: [4 bytes size][Image Data]
                    // Real AirPlay stream handling is much more complex involving 
                    // 128-byte AES-CTR decryption and H.264 parsing.
                    
                    val frameSize = inputStream?.readInt() ?: break
                    
                    if (frameSize <= 0 || frameSize > MAX_FRAME_SIZE) {
                        // Might not be our custom protocol. 
                        // If connecting from real AirPlay, this will fail here.
                        // We would need to implement the AirPlay decryptor here.
                        Log.w(TAG, "Invalid frame size or protocol mismatch: $frameSize")
                        // Attempt to resync or break
                        break
                    }
                    
                    // Read frame data
                    val frameData = ByteArray(frameSize)
                    var bytesRead = 0
                    while (bytesRead < frameSize) {
                        val read = inputStream?.read(
                            frameData,
                            bytesRead,
                            frameSize - bytesRead
                        ) ?: break
                        if (read < 0) break
                        bytesRead += read
                    }
                    
                    if (bytesRead == frameSize) {
                        renderFrame(frameData)
                        updateFps()
                    }
                } catch (e: Exception) {
                    if (isReceiving) {
                        Log.e(TAG, "Error receiving frame", e)
                    }
                    break
                }
            }
            disconnect()
        }
    }
    
    private fun renderFrame(frameData: ByteArray) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
            if (bitmap != null) {
                val canvas = surface.lockCanvas(null)
                if (canvas != null) {
                    try {
                        // Scale bitmap to fit canvas while maintaining aspect ratio
                        val canvasWidth = canvas.width.toFloat()
                        val canvasHeight = canvas.height.toFloat()
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()
                        
                        // Use black background
                        canvas.drawColor(Color.BLACK)
                        
                        if (bitmapWidth > 0 && bitmapHeight > 0) {
                            val scale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
                            val scaledWidth = bitmapWidth * scale
                            val scaledHeight = bitmapHeight * scale
                            val x = (canvasWidth - scaledWidth) / 2
                            val y = (canvasHeight - scaledHeight) / 2
                            
                            // High quality filtering
                            paint.isFilterBitmap = true
                            
                            canvas.drawBitmap(
                                bitmap,
                                Rect(0, 0, bitmap.width, bitmap.height),
                                RectF(x, y, x + scaledWidth, y + scaledHeight),
                                paint
                            )
                        }
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering frame", e)
        }
    }
    
    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFpsTime >= 1000) {
            val fps = frameCount * 1000.0 / (currentTime - lastFpsTime)
            Log.d(TAG, "FPS: ${String.format("%.2f", fps)}")
            onFpsUpdate?.invoke(fps)
            frameCount = 0
            lastFpsTime = currentTime
        }
    }
    
    fun disconnect() {
        isReceiving = false
        try {
            inputStream?.close()
            tcpSocket?.close()
            udpSocket?.close()
            onConnectionStateChanged(false)
            Log.d(TAG, "Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }
    
    companion object {
        private const val TAG = "VideoReceiver"
    }
}
