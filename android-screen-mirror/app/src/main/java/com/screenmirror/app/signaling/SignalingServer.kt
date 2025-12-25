package com.screenmirror.app.signaling

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import android.util.Log
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.util.concurrent.CopyOnWriteArraySet
import com.google.gson.Gson

/**
 * Very small WebSocket signaling server. Handles a single client for now.
 * Messages are JSON objects of form: { "type": "offer|answer|candidate", "sdp": "...", "candidate": {...} }
 */
class SignalingServer(private val port: Int = 9000) {
    private val TAG = "SignalingServer"
    private var server: WebSocketServer? = null
    private val clients = CopyOnWriteArraySet<WebSocket>()
    private val gson = Gson()
    var onMessage: ((String, WebSocket) -> Unit)? = null
    // Simple HTTP health check server
    private var httpServer: ServerSocket? = null
    private var httpThread: Thread? = null
    private var isHttpRunning = false

    fun start() {
        if (server != null) return
        server = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
                conn?.let {
                    clients.add(it)
                    Log.d(TAG, "Client connected: ${it.remoteSocketAddress}")
                }
            }

            override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
                conn?.let {
                    clients.remove(it)
                    Log.d(TAG, "Client disconnected: ${it.remoteSocketAddress}")
                }
            }

            override fun onMessage(conn: WebSocket?, message: String?) {
                if (conn == null || message == null) return
                Log.d(TAG, "Signaling message: $message")
                onMessage?.invoke(message, conn)
            }

            override fun onError(conn: WebSocket?, ex: Exception?) {
                Log.e(TAG, "WebSocket error", ex)
            }

            override fun onStart() {
                Log.d(TAG, "Signaling server started on port $port")
            }
        }
        server?.start()

        // Start simple HTTP health-check on port 9001
        try {
            if (!isHttpRunning) {
                isHttpRunning = true
                httpServer = ServerSocket(9001)
                httpThread = Thread {
                    Log.d(TAG, "HTTP health-check server started on port 9001")
                    while (isHttpRunning) {
                        try {
                            val client: Socket = httpServer?.accept() ?: break
                            val out = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
                            val body = "OK"
                            out.write("HTTP/1.1 200 OK\r\n")
                            out.write("Content-Type: text/plain\r\n")
                            out.write("Content-Length: ${body.length}\r\n")
                            out.write("Connection: close\r\n")
                            out.write("\r\n")
                            out.write(body)
                            out.flush()
                            client.close()
                        } catch (e: Exception) {
                            if (isHttpRunning) Log.e(TAG, "HTTP server error", e)
                        }
                    }
                    Log.d(TAG, "HTTP health-check server stopped")
                }
                httpThread?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting HTTP health-check", e)
        }
    }

    fun stop() {
        try {
            server?.stop()
            // Stop HTTP server
            isHttpRunning = false
            try {
                httpServer?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                httpThread?.interrupt()
            } catch (e: Exception) {
                // ignore
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping signaling server", e)
        }
        server = null
    }

    fun broadcast(obj: Any) {
        val msg = gson.toJson(obj)
        for (c in clients) {
            try {
                c.send(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting", e)
            }
        }
    }

    fun send(conn: WebSocket, obj: Any) {
        try {
            conn.send(gson.toJson(obj))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
        }
    }
}
