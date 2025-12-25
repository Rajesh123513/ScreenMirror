package com.screenmirror.app.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class H264Decoder(private val surface: Surface) {
    private var codec: MediaCodec? = null
    private var isConfigured = false
    
    companion object {
        private const val TAG = "H264Decoder"
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    fun start(width: Int = 1920, height: Int = 1080) {
        if (isConfigured) return
        
        try {
            codec = MediaCodec.createDecoderByType(VIDEO_MIME_TYPE)
            val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height)
            
            // Critical: Set these flags for low latency
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1)
            
            // Assuming the stream will provide SPS/PPS in-band
            
            codec?.configure(format, surface, null, 0)
            codec?.start()
            isConfigured = true
            Log.d(TAG, "H.264 Decoder started: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start H.264 decoder", e)
        }
    }

    fun feedData(data: ByteArray, length: Int) {
        if (!isConfigured || codec == null) return

        try {
            val index = codec?.dequeueInputBuffer(10000) // 10ms timeout
            if (index != null && index >= 0) {
                val buffer: ByteBuffer? = codec?.getInputBuffer(index)
                buffer?.clear()
                buffer?.put(data, 0, length)
                
                codec?.queueInputBuffer(
                    index,
                    0,
                    length,
                    System.nanoTime() / 1000,
                    0
                )
            }

            processOutput()
        } catch (e: Exception) {
            // Log.e(TAG, "Error feeding data", e)
        }
    }

    private fun processOutput() {
        val info = MediaCodec.BufferInfo()
        try {
            var outIndex = codec?.dequeueOutputBuffer(info, 0) ?: -1
            while (outIndex >= 0) {
                codec?.releaseOutputBuffer(outIndex, true)
                outIndex = codec?.dequeueOutputBuffer(info, 0) ?: -1
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stop() {
        try {
            isConfigured = false
            codec?.stop()
            codec?.release()
            codec = null
            Log.d(TAG, "H.264 Decoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping decoder", e)
        }
    }
}
