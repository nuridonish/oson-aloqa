package com.osonaloqa.app.ui.auth

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.osonaloqa.app.HomeActivity
import com.osonaloqa.app.Pref
import com.osonaloqa.app.R
import com.osonaloqa.app.data.api.ServerApi
import com.osonaloqa.app.data.crypto.CryptoManager
import com.osonaloqa.app.databinding.ActivityOtpBinding

class OtpActivity : AppCompatActivity() {

    private lateinit var b: ActivityOtpBinding
    private lateinit var e164: String
    private var timer: CountDownTimer? = null

    private val permLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.SEND_SMS] == true || granted[Manifest.permission.RECEIVE_SMS] == true) {
                goHome()
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(b.root)
        e164 = intent.getStringExtra("e164") ?: ""
        b.number.text = e164
        b.note.text = getString(R.string.otp_offline_note)
        startTimer()
        val base = ServerApi.baseUrl(this)
        if (base.isNotEmpty()) {
            ServerApi.requestOtp(base, e164) { resp ->
                if (resp != null && resp.optBoolean("ok")) {
                    serverMode = true
                    val dev = resp.optString("dev", "")
                    if (dev.isNotEmpty()) {
                        b.note.text = getString(R.string.otp_offline_note) + "\nSMS gateway OFF — demo code: " + dev
                    } else {
                        b.note.text = getString(R.string.otp_sent_real)
                    }
                }
            }
        }
        b.btnVerify.setOnClickListener { verify() }
        b.btnResend.isEnabled = false
        b.btnResend.setOnClickListener {
            Toast.makeText(this, R.string.otp_sent_demo, Toast.LENGTH_SHORT).show()
            startTimer()
        }
    }

    private fun startTimer() {
        timer?.cancel()
        b.btnResend.isEnabled = false
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(ms: Long) {
                b.btnResend.text = getString(R.string.otp_resend_in, (ms / 1000).toString())
            }
            override fun onFinish() {
                b.btnResend.isEnabled = true
                b.btnResend.text = getString(R.string.otp_resend)
            }
        }.start()
    }

    private var serverMode = false

    private fun verify() {
        val code = b.code.text.toString().trim()
        if (code.length != 6) {
            b.code.error = getString(R.string.otp_invalid)
            return
        }
        val base = ServerApi.baseUrl(this)
        if (base.isNotEmpty()) {
            val name = "User" + e164.takeLast(4)
            ServerApi.verify(base, e164, code, name) { resp ->
                if (resp != null && resp.optBoolean("ok")) {
                    ServerApi.saveToken(this, resp.optString("token"))
                    val pub = com.osonaloqa.app.data.crypto.CryptoManager.publicKeyBase64()
                    if (pub.isNotEmpty()) ServerApi.putKey(base, resp.optString("token"), pub)
                    val profile = resp.optJSONObject("profile")
                    val nm = profile?.optString("name") ?: name
                    Pref.save(this, nm, profile?.optString("phone") ?: e164)
                    requestPerms()
                } else {
                    b.code.error = getString(R.string.otp_invalid)
                }
            }
            return
        }
        if (code == "123456") {
            val name = "User" + e164.takeLast(4)
            Pref.save(this, name, e164)
            try {
                val enc = CryptoManager.encrypt("token_" + System.currentTimeMillis())
                getSharedPreferences("osonaloqa", MODE_PRIVATE).edit().putString("server_token_enc", enc).apply()
            } catch (e: Exception) {
            }
            requestPerms()
        } else {
            b.code.error = getString(R.string.otp_invalid)
        }
    }

    private fun requestPerms() {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val needed = list.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) goHome() else permLauncher.launch(needed.toTypedArray())
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}


