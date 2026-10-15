package com.osonaloqa.app.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object ServerApi {

    private val pool = Executors.newCachedThreadPool()
    private val ui = Handler(Looper.getMainLooper())

    fun baseUrl(ctx: Context): String {
        val saved = ctx.getSharedPreferences("osonaloqa", Context.MODE_PRIVATE)
            .getString("server_url", "") ?: ""
        if (saved.isNotEmpty()) return saved
        return com.osonaloqa.app.BuildConfig.DEFAULT_SERVER_URL
    }

    /** Номи нишонододашуда дар Профил ё default-и BuildConfig */
    fun effectiveBaseUrl(ctx: Context): String = baseUrl(ctx)

    fun setBaseUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences("osonaloqa", Context.MODE_PRIVATE)
            .edit().putString("server_url", url.trim().trimEnd('/')).apply()
    }

    fun token(ctx: Context): String = try {
        val enc = ctx.getSharedPreferences("osonaloqa", Context.MODE_PRIVATE)
            .getString("server_token_enc", "") ?: ""
        if (enc.isEmpty()) "" else com.osonaloqa.app.data.crypto.CryptoManager.decrypt(enc)
    } catch (e: Exception) {
        ""
    }

    fun saveToken(ctx: Context, token: String) {
        ctx.getSharedPreferences("osonaloqa", Context.MODE_PRIVATE)
            .edit()
            .putString("server_token_enc", com.osonaloqa.app.data.crypto.CryptoManager.encrypt(token))
            .apply()
    }

    fun requestOtp(base: String, phone: String, cb: (JSONObject?) -> Unit) =
        run("POST", base, "/auth/request-otp", null, JSONObject().put("phone", phone), cb)

    fun verify(base: String, phone: String, code: String, name: String, cb: (JSONObject?) -> Unit) =
        run("POST", base, "/auth/verify", null,
            JSONObject().put("phone", phone).put("code", code).put("name", name), cb)

    fun search(base: String, token: String, q: String, cb: (JSONObject?) -> Unit) =
        run("GET", base, "/users/search?q=" + URLEncoder.encode(q, "UTF-8"), token, null, cb)

    fun report(base: String, token: String, address: String, reason: String, cb: (JSONObject?) -> Unit = {}) =
        run("POST", base, "/reports", token,
            JSONObject().put("address", address).put("reason", reason), cb)

    fun addBlock(base: String, token: String, address: String) =
        run("POST", base, "/blocks", token, JSONObject().put("address", address)) {}

    fun removeBlock(base: String, token: String, address: String) =
        run("DELETE", base, "/blocks", token, JSONObject().put("address", address)) {}

    fun putKey(base: String, token: String, pub: String, cb: (JSONObject?) -> Unit = {}) =
        run("POST", base, "/keys", token, JSONObject().put("pub", pub), cb)

    fun getKey(base: String, token: String, phone: String, cb: (JSONObject?) -> Unit) =
        run("GET", base, "/keys?phone=" + URLEncoder.encode(phone, "UTF-8"), token, null, cb)

    fun uploadMedia(base: String, token: String, bytes: ByteArray, cb: (JSONObject?) -> Unit) {
        if (base.isBlank()) {
            ui.post { cb(null) }
            return
        }
        pool.execute {
            var result: JSONObject? = null
            try {
                val conn = URL("$base/media/upload").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
                val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                if (text.isNotEmpty()) result = JSONObject(text)
            } catch (e: Exception) {
            }
            ui.post { cb(result) }
        }
    }

    fun mediaUrl(base: String, id: String, name: String): String =
        "$base/m/$id?name=" + URLEncoder.encode(name, "UTF-8")

    // ===== Гурӯҳҳо/Статус (blocking — дар executor) =====
    fun myGroupsBlocking(base: String, token: String): org.json.JSONArray? = try {
        blockingGet("$base/groups/my", token)?.optJSONArray("groups")
    } catch (e: Exception) { null }

    fun createGroupBlocking(base: String, token: String, name: String, type: String): JSONObject? = try {
        blockingPost("$base/groups/create", token, JSONObject().put("name", name).put("type", type))
    } catch (e: Exception) { null }

    fun joinGroupBlocking(base: String, token: String, id: String): JSONObject? = try {
        blockingPost("$base/groups/join", token, JSONObject().put("id", id))
    } catch (e: Exception) { null }

    fun sendGroupBlocking(base: String, token: String, id: String, body: String, kind: String, url: String): Boolean = try {
        val out = JSONObject().put("id", id).put("body", body).put("kind", kind).put("url", url)
        blockingPost("$base/groups/send", token, out)?.optBoolean("ok") == true
    } catch (e: Exception) { false }

    fun postStatusBlocking(base: String, token: String, text: String): Boolean = try {
        blockingPost("$base/status", token, JSONObject().put("text", text))?.optBoolean("ok") == true
    } catch (e: Exception) { false }

    fun feedBlocking(base: String, token: String): org.json.JSONArray? = try {
        blockingGet("$base/status/feed", token)?.optJSONArray("statuses")
    } catch (e: Exception) { null }

    fun blockingGet(url: String, token: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 7000
        conn.readTimeout = 15000
        if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        return if (text.isNotEmpty()) JSONObject(text) else null
    }

    fun blockingPost(url: String, token: String, body: JSONObject): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 7000
        conn.readTimeout = 15000
        if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        return if (text.isNotEmpty()) JSONObject(text) else null
    }

    private fun run(method: String, base: String, path: String, token: String?, body: JSONObject?, cb: (JSONObject?) -> Unit) {
        if (base.isBlank()) {
            ui.post { cb(null) }
            return
        }
        pool.execute {
            var result: JSONObject? = null
            try {
                val conn = URL(base + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
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
            ui.post { cb(result) }
        }
    }
}


