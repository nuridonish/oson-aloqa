package com.osonaloqa.app

import android.media.AudioManager
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.osonaloqa.app.databinding.ActivityCallScreenBinding

class CallScreenActivity : AppCompatActivity() {

    private lateinit var b: ActivityCallScreenBinding
    private var muted = false
    private var speaker = false

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateUi(call)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        b = ActivityCallScreenBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        currentCall?.registerCallback(callback)
        b.btnAnswer.setOnClickListener {
            try { currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (e: Exception) {}
        }
        b.btnEnd.setOnClickListener {
            try { currentCall?.disconnect() } catch (e: Exception) {}
            finish()
        }
        b.btnDecline.setOnClickListener {
            try { currentCall?.disconnect() } catch (e: Exception) {}
            finish()
        }
        b.btnMute.setOnClickListener {
            muted = !muted
            try {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.setMicrophoneMute(muted)
            } catch (e: Exception) {}
            b.btnMute.alpha = if (muted) 0.5f else 1f
        }
        b.btnSpeaker.setOnClickListener {
            speaker = !speaker
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = speaker
            b.btnSpeaker.alpha = if (speaker) 1f else 0.5f
        }
        updateUi(currentCall)
    }

    private fun updateUi(call: Call?) {
        if (call == null) { finish(); return }
        val number = call.details.handle?.schemeSpecificPart ?: ""
        b.number.text = number
        when (call.state) {
            Call.STATE_RINGING -> {
                b.status.text = getString(R.string.call_incoming)
                b.btnAnswer.visibility = View.VISIBLE
                b.btnDecline.visibility = View.VISIBLE
                b.btnEnd.visibility = View.GONE
                b.rowExtra.visibility = View.GONE
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE, Call.STATE_HOLDING -> {
                b.status.text = getString(R.string.call_ongoing)
                b.btnAnswer.visibility = View.GONE
                b.btnDecline.visibility = View.GONE
                b.btnEnd.visibility = View.VISIBLE
                b.rowExtra.visibility = View.VISIBLE
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                b.status.text = getString(R.string.call_ended)
                b.btnAnswer.visibility = View.GONE
                b.btnDecline.visibility = View.GONE
                b.btnEnd.visibility = View.GONE
                b.rowExtra.visibility = View.GONE
            }
            else -> {
                b.status.text = getString(R.string.call_ongoing)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCall?.unregisterCallback(callback)
        instance = null
    }

    companion object {
        var currentCall: Call? = null
        var instance: CallScreenActivity? = null
    }
}
