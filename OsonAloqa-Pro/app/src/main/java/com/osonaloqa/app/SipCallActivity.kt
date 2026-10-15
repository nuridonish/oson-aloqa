package com.osonaloqa.app

import android.content.Context
import android.media.AudioManager
import android.net.sip.SipAudioCall
import android.net.sip.SipManager
import android.net.sip.SipProfile
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.osonaloqa.app.databinding.ActivitySipCallBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SipCallActivity : AppCompatActivity() {

    private lateinit var b: ActivitySipCallBinding
    private var manager: SipManager? = null
    private var profile: SipProfile? = null
    private var call: SipAudioCall? = null
    private var number = ""
    private var closed = false
    private var muted = false
    private var speaker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySipCallBinding.inflate(layoutInflater)
        setContentView(b.root)
        number = intent.getStringExtra("number") ?: ""
        b.number.text = number
        b.status.text = getString(R.string.ic_connecting)
        b.btnEnd.setOnClickListener {
            endCall()
        }
        b.btnMute.setOnClickListener {
            muted = !muted
            try { call?.toggleMute() } catch (e: Exception) {}
            b.btnMute.alpha = if (muted) 0.5f else 1f
        }
        b.btnSpeaker.setOnClickListener {
            speaker = !speaker
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = speaker
            b.btnSpeaker.alpha = if (speaker) 1f else 0.5f
        }

        if (!SipManager.isApiSupported(this) || !SipManager.isVoipSupported(this)) {
            Toast.makeText(this, R.string.sip_unsupported, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        connectAndCall()
    }

    private fun connectAndCall() {
        manager = SipManager.newInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val host = Pref.sipHost(this@SipCallActivity)
                val user = Pref.sipUser(this@SipCallActivity)
                val pass = Pref.sipPass(this@SipCallActivity)
                val builder = SipProfile.Builder(user, host).setPassword(pass)
                val p: SipProfile = builder.build()
                profile = p
                manager?.open(p)
                val digits = number.filter { it.isDigit() }
                val target: SipProfile = SipProfile.Builder("sip:$digits@$host").build()
                val listener = object : SipAudioCall.Listener() {
                    override fun onCallEstablished(c: SipAudioCall) {
                        runOnUiThread {
                            b.status.text = getString(R.string.ic_connected)
                            try { c.startAudio(); c.setSpeakerMode(speaker) } catch (e: Exception) {}
                        }
                    }

                    override fun onCallEnded(c: SipAudioCall) {
                        runOnUiThread { finish() }
                    }
                }
                withContext(Dispatchers.Main) {
                    call = manager?.makeAudioCall(p, target, listener, 30)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    b.status.text = getString(R.string.sip_error, e.message ?: "")
                    Toast.makeText(this@SipCallActivity, R.string.sip_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun endCall() {
        closed = true
        try { call?.endCall() } catch (e: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { call?.endCall() } catch (e: Exception) {}
        profile?.let { try { manager?.close(it.uriString) } catch (e: Exception) {} }
    }
}


