package com.osonaloqa.app.data.repo

import android.content.Context
import com.osonaloqa.app.data.api.NetSync
import com.osonaloqa.app.data.api.ServerApi
import com.osonaloqa.app.data.api.WsClient
import com.osonaloqa.app.data.db.AppDatabase
import com.osonaloqa.app.data.db.NetMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Repository — ягона нуқтаи фиристодани паёмҳои интернетӣ.
 * offline-first: паём фавран дар база (⏳), сипас WS → REST.
 */
object MessageRepo {

    fun newUuid(): String = UUID.randomUUID().toString()

    fun storeLocal(
        ctx: Context, address: String, body: String, gid: String, gname: String,
        out: Boolean, kind: String, enc: Boolean, uuid: String, ackState: String, seq: Long = 0
    ) {
        runBlocking {
            withContext(Dispatchers.IO) {
                AppDatabase.get(ctx).netDao().add(
                    NetMessage(
                        address = if (gid.isEmpty()) address else gid,
                        body = body, ts = System.currentTimeMillis(), out = out,
                        kind = kind, enc = enc, gid = gid, gname = gname,
                        uuid = uuid, ackState = ackState, seq = seq
                    )
                )
            }
        }
    }

    /**
     * Баргаштан: null = қабул шуд; 'unreachable' | 'no_user' | 'failed' — паём дар база бо ⏳ мемонад
     */
    fun sendText(ctx: Context, to: String, text: String, gid: String = "", gname: String = ""): String? {
        val uuid = newUuid()
        storeLocal(ctx, if (gid.isEmpty()) to else gid, text, gid, gname, out = true, kind = "text", enc = true, uuid = uuid, ackState = "queued")

        // 1) WS-аввал (танҳо чатҳои шахсӣ; гурӯҳҳо — REST fan-out)
        if (gid.isEmpty()) {
            val frame = JSONObject()
                .put("type", "message.send")
                .put("to", to).put("body", text).put("enc", true)
                .put("kind", "text").put("uuid", uuid)
            if (WsClient.send(frame)) return null // 'sent'-ack тавассути FrameBus
        }

        // 2) REST: гурӯҳ → /groups/send; шахсӣ → /messages/send
        val base = ServerApi.baseUrl(ctx)
        val token = ServerApi.token(ctx)
        if (base.isEmpty() || token.isEmpty()) return "unreachable"
        val resp: JSONObject? = try {
            if (gid.isNotEmpty()) {
                NetSync.blocking("POST", base, "/groups/send", token,
                    JSONObject().put("id", gid).put("body", text).put("enc", true).put("kind", "text"))
            } else {
                NetSync.blocking("POST", base, "/messages/send", token,
                    JSONObject().put("to", to).put("body", text).put("enc", true).put("kind", "text").put("uuid", uuid))
            }
        } catch (e: Exception) {
            null
        }
        if (resp == null) return "unreachable"
        if (resp.optBoolean("ok")) {
            val seq = resp.optLong("seq", 0)
            runBlocking {
                withContext(Dispatchers.IO) {
                    if (seq > 0) AppDatabase.get(ctx).netDao().markSent(uuid, seq, "sent")
                    else AppDatabase.get(ctx).netDao().setAck(uuid, "sent")
                }
            }
            return null
        }
        return when (resp.optString("error")) {
            "no_user" -> "no_user"
            else -> "failed"
        }
    }

    fun sendMedia(ctx: Context, to: String, gid: String, link: String, kind: String): Boolean {
        if (gid.isNotEmpty()) {
            val base = ServerApi.baseUrl(ctx); val token = ServerApi.token(ctx)
            if (base.isNotEmpty() && token.isNotEmpty()) {
                return try {
                    NetSync.blocking("POST", base, "/groups/send", token,
                        JSONObject().put("id", gid).put("body", link).put("kind", kind).put("url", link)
                    )?.optBoolean("ok") == true
                } catch (e: Exception) { false }
            }
            return false
        }
        val frame = JSONObject()
            .put("type", "message.send")
            .put("to", to).put("body", link)
            .put("kind", kind).put("url", link)
        if (WsClient.send(frame)) return true
        val base = ServerApi.baseUrl(ctx)
        val token = ServerApi.token(ctx)
        if (base.isEmpty() || token.isEmpty()) return false
        val body = JSONObject().put("to", to).put("body", link).put("kind", kind).put("url", link)
        return try {
            NetSync.blocking("POST", base, "/messages/send", token, body)?.optBoolean("ok") == true
        } catch (e: Exception) {
            false
        }
    }
}
