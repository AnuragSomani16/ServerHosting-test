package com.example.ChatApp

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject

class ChatWebSocketClient(
    private val url: String,
    private val onMessageReceived: (sender: String, content: String, latencyMs: Long) -> Unit,
    private val onConnectionChanged: (connected: Boolean) -> Unit
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect() {
        Log.d("WebSocket", "Attempting to connect to: $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post { onConnectionChanged(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                val sentAt = json.optLong("sentAt", 0L)
                val latencyMs = if (sentAt > 0) System.currentTimeMillis() - sentAt else -1L
                mainHandler.post {
                    onMessageReceived(
                        json.optString("from", "unknown"),
                        json.optString("content", ""),
                        latencyMs
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post { onConnectionChanged(false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection failed to $url: ${t.message}", t)
                mainHandler.post { onConnectionChanged(false) }
            }
        })
    }

    fun sendMessage(content: String) {
        val json = JSONObject()
            .put("content", content)
            .put("sentAt", System.currentTimeMillis())
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Goodbye")
    }
}
