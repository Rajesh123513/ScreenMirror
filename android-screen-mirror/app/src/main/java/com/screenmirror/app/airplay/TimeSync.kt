package com.screenmirror.app.airplay

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles AirPlay/NTP Time Synchronization.
 * Critical for keeping the connection alive.
 */
class TimeSync {
    companion object {
        private const val TAG = "TimeSync"
        private const val NTP_OFFSET = 2208988800L
    }

    fun handleSyncPacket(socket: DatagramSocket, packet: DatagramPacket) {
        try {
            val data = packet.data
            // Simplified NTP-like structure check
            // AirPlay sync packets are typically 32 bytes
            if (packet.length < 32) return

            // We need to reflect the timestamp back with our current time
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            
            // Read incoming timestamp (seconds + fraction)
            // This is just a basic echo mechanism required by the protocol
            val r0 = buffer.get(0) // Control byte?
            
            // Construct response
            // For AirPlay mirroring, often echoing the relevant parts 
            // and adding the current reference time is sufficient.
            
            // NTP timestamp format: 64-bit fixed point
            // High 32 bits: seconds since 1900
            // Low 32 bits: fraction
            
            val currentTime = System.currentTimeMillis()
            val ntpSeconds = (currentTime / 1000) + NTP_OFFSET
            val ntpFraction = ((currentTime % 1000) * 4294967296.0 / 1000.0).toLong()

            // Prepare response packet (32 bytes)
            val response = ByteArray(32)
            val resBuffer = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
            
            // Copy some headers (simplified)
            System.arraycopy(data, 0, response, 0, 4) // Copy first word
            
            // Write Reference Timestamp (Receive Time)
            resBuffer.putInt(16, ntpSeconds.toInt())
            resBuffer.putInt(20, ntpFraction.toInt())
            
            // Write Transmit Timestamp (Send Time)
            resBuffer.putInt(24, ntpSeconds.toInt())
            resBuffer.putInt(28, ntpFraction.toInt())

            val responsePacket = DatagramPacket(
                response,
                response.size,
                packet.address,
                packet.port
            )
            
            socket.send(responsePacket)
            // Log.v(TAG, "Sent TimeSync response")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling TimeSync", e)
        }
    }
}
