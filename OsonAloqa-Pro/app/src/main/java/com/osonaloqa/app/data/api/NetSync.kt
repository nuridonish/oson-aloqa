package com.osonaloqa.app.data.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.osonaloqa.app.ChatActivity
import com.osonaloqa.app.R
import com.osonaloqa.app.data.crypto.CryptoManager
import com.osonaloqa.app.data.db.AppDatabase
import com.osonaloqa.app.data.db.NetMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NetSync {

    private const val TAG = "NetSync"

    fun poll(ctx: Context) {
        val base = ServerApi.baseUrl(ctx)
        val token = ServerApi.token(ctx)
        if (base.isEmpty() || token.isEmpty()) return
        try {
            val resp = blocking("GET", base, "/messages/pull", token, null) ?: return
            if (!resp.optBoolean("ok")) return
            val arr = resp.optJSONArray("messages") ?: return
            if (arr.length() == 0) return
            runBlocking {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.get(ctx).netDao()
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val from = m.optString("from")
                        var body = m.optString("body")
                        val enc = m.optBoolean("enc")
                        val kind = m.optString("kind", "text")
                        val url = m.optString("url", "")
                        val gid = m.optString("gid", "")
                        val gname = m.optString("gname", "")
                        val ts = m.optLong("ts", System.currentTimeMillis())
                        if (enc) {
                            try {
                                val keyResp = blocking(
                                    "GET", base,
                                    "/keys?phone=" + URLEncoder.encode(from, "UTF-8"), token, null
                                )
                                val pub = keyResp?.optString("pub") ?: ""
                                if (pub.isNotEmpty()) body = CryptoManager.decryptFor(pub, body)
                            } catch (e: Exception) {
                                Log.w(TAG, "decrypt fail")
                            }
                        }
                        val shown = when (kind) {
                            "image" -> "🖼 $url"
                            "video" -> "🎬 $url"
                            "file" -> "📄 $url"
                            else -> body
                        }
                        val dup = if (gid.isNotEmpty()) dao.existsGroup(gid, shown, ts) != null
                        else dao.lastDirect(from)?.let { it.ts == ts && it.body == shown } == true
                        if (!dup) {
                            dao.add(NetMessage(address = if (gid.isNotEmpty()) gid else from, body = shown, ts = ts, out = false, kind = kind, enc = enc, gid = gid, gname = m.optString("gname", ""), seq = m.optLong("seq", 0)))
                            notifyNew(ctx, if (gid.isNotEmpty()) gid else from, shown)
                            ctx.sendBroadcast(Intent(ChatActivity.ACTION_NEW_MSG).setPackage(ctx.packageName))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "poll fail")
        }
    }

    fun sendGroup(ctx: Context, gid: String, text: String, kind: String = "text", url: String = ""): Boolean {
        val base = ServerApi.baseUrl(ctx)
        val token = ServerApi.token(ctx)
        if (base.isEmpty() || token.isEmpty()) return false
        return try {
            val out = JSONObject()
            out.put("id", gid)
            out.put("body", text)
            out.put("kind", kind)
            out.put("url", url)
            ServerApi.blockingPost("$base/groups/send", token, out)?.optBoolean("ok") == true
        } catch (e: Exception) {
            false
        }
    }

    /** null = муваффақ; otherwise error code: unreachable | no_user | failed */
    fun send(ctx: Context, to: String, text: String, kind: String = "text", url: String = ""): String? {
        val base = ServerApi.baseUrl(ctx)
        val token = ServerApi.token(ctx)
        if (base.isEmpty() || token.isEmpty()) return "unreachable"
        return try {
            var body = text
            var enc = false
            runBlocking {
                withContext(Dispatchers.IO) {
                    val keyResp = blocking(
                        "GET", base,
                        "/keys?phone=" + URLEncoder.encode(to, "UTF-8"), token, null
                    )
                    val pub = keyResp?.optString("pub") ?: ""
                    if (pub.isNotEmpty() && kind == "text") {
                        val encBody = CryptoManager.encryptFor(pub, text)
                        if (encBody.isNotEmpty()) {
                            body = encBody
                            enc = true
                        }
                    }
                }
            }
            val out = JSONObject()
            out.put("to", to)
            out.put("body", body)
            out.put("enc", enc)
            out.put("kind", kind)
            out.put("url", url)
            var resp: JSONObject? = null
            for (attempt in 1..3) {
                resp = blocking("POST", base, "/messages/send", token, out)
                if (resp != null) break
                if (attempt < 3) Thread.sleep(1000L * attempt)
            }
            if (resp == null) return "unreachable"
            if (resp.optBoolean("ok")) return null
            return if (resp.optString("error") == "no_user") "no_user" else "failed"
        } catch (e: Exception) {
            "unreachable"
        }
    }

    private fun notifyNew(ctx: Context, address: String, body: String) {
        if (ChatActivity.currentAddress == address) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("osonaloqa_msg", ctx.getString(R.string.channel_msg), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pi = PendingIntent.getActivity(
            ctx, address.hashCode(),
            ChatActivity.intent(ctx, address),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, "osonaloqa_msg")
            .setSmallIcon(R.drawable.ic_msg_small)
            .setContentTitle(address)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(address.hashCode(), n)
    }

    fun blocking(method: String, base: String, path: String, token: String?, body: JSONObject?): JSONObject? {
        var result: JSONObject? = null
        try {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 7000
            conn.readTimeout = 15000
            if (!token.isNullOrEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null && method != "GET") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            if (text.isNotEmpty()) result = JSONObject(text)
        } catch (e: Exception) {
        }
        return result
    }
}




