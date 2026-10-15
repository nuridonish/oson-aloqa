package com.osonaloqa.app.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WsClient — пайвасти WebSocket бо сервер.
 * - reconnect-и худкор бо exponential backoff (1с → 2с → 4с → ... 60с)
 * - heartbeat ping ҳар 25 сония
 * - қабули кадрҳои JSON → listener
 */
object WsClient {

    private const val TAG = "WsClient"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connecting = AtomicBoolean(false)
    private var backoffSec = 1L

    @Volatile var onFrame: ((JSONObject) -> Unit)? = null
    @Volatile var onStateChange: ((Boolean) -> Unit)? = null

    // FrameBus: ҳамаи гӯшкунандаҳо
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<(JSONObject) -> Unit>()
    fun addListener(l: (JSONObject) -> Unit) { listeners.add(l) }
    fun removeListener(l: (JSONObject) -> Unit) { listeners.remove(l) }

    fun isConnected(): Boolean = ws != null

    fun connect(context: Context) {
        if (connecting.get()) return
        connecting.set(true)
        val base = ServerApi.effectiveBaseUrl(context)
        if (base.isEmpty()) {
            connecting.set(false)
            return
        }
        val token = ServerApi.token(context)
        val wsUrl = base.replace("http://", "ws://").replace("https://", "wss://") + "/ws?token=" +
            java.net.URLEncoder.encode(token, "UTF-8")
        scope.launch {
            try {
                val request = Request.Builder().url(wsUrl).build()
                ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        log(true)
                        backoffSec = 1
                        onStateChange?.invoke(true)
                        // hello-ро интизор мешавем; фавран ping-и мантиқӣ
                        webSocket.send(JSONObject().put("type", "ping").toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val obj = JSONObject(text)
                            if (obj.optString("type") == "pong") return
                            listeners.forEach { l ->
                                try { l(obj) } catch (e: Exception) { Log.w(TAG, "listener fail") }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "frame parse fail")
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        log(false)
                        onStateChange?.invoke(false)
                        scheduleReconnect(context)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        log(false)
                        onStateChange?.invoke(false)
                        scheduleReconnect(context)
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "connect fail: ${e.message}")
                connecting.set(false)
                scheduleReconnect(context)
            }
        }
    }

    private fun scheduleReconnect(context: Context) {
        val wait = backoffSec
        backoffSec = (backoffSec * 2).coerceAtMost(60)
        scope.launch {
            delay(wait * 1000)
            connect(context)
        }
    }

    private fun log(up: Boolean) {
        connecting.set(false)
        Log.i(TAG, if (up) "WS UP" else "WS DOWN")
    }

    fun send(obj: JSONObject): Boolean {
        val w = ws ?: return false
        return try {
            w.send(obj.toString())
        } catch (e: Exception) {
            false
        }
    }
}
