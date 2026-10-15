package com.osonaloqa.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.osonaloqa.app.databinding.ActivityInternetCallBinding
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.Executors

class InternetCallActivity : AppCompatActivity() {

    private lateinit var b: ActivityInternetCallBinding
    private var peer: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var withVideo = false
    private var remoteAddr = ""
    private var incoming = false
    private var connectedOnce = false
    private var closed = false
    private var player: MediaPlayer? = null
    private var currentOffer: JSONObject? = null
    private var muted = false
    private var speakerOn = false
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val fallback = Runnable {
        if (!connectedOnce && !closed) {
            runOnUiThread { offerGsmFallback() }
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
            if (m[Manifest.permission.RECORD_AUDIO] == true) {
                val offer = currentOffer
                currentOffer = null
                if (offer != null) acceptCall(offer) else startOutgoing()
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        b = ActivityInternetCallBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        b.remote.init(egl().eglBaseContext, null)
        b.local.init(egl().eglBaseContext, null)
        b.local.setZOrderMediaOverlay(true)

        incoming = intent.getStringExtra(EXTRA_JSON) != null
        if (incoming) {
            val json = JSONObject(intent.getStringExtra(EXTRA_JSON)!!)
            remoteAddr = json.optString("from")
            withVideo = json.optBoolean("video", false)
            currentOffer = json
            b.status.text = getString(R.string.ic_incoming)
            b.btnAnswer.visibility = View.VISIBLE
            b.btnDecline.visibility = View.VISIBLE
            b.btnEnd.visibility = View.GONE
            startRingtone()
        } else {
            remoteAddr = intent.getStringExtra(EXTRA_CALL) ?: ""
            withVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
            b.status.text = getString(R.string.ic_connecting)
            b.btnAnswer.visibility = View.GONE
            b.btnDecline.visibility = View.GONE
            b.btnEnd.visibility = View.VISIBLE
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (withVideo) perms.add(Manifest.permission.CAMERA)
            val need = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need.isEmpty()) startOutgoing() else permLauncher.launch(need.toTypedArray())
        }
        b.number.text = remoteAddr

        Signal.ensure { }
        Signal.listen(Signal.topicOf(Pref.e164(this))) { json ->
            runOnUiThread { onSignal(json) }
        }

        b.btnAnswer.setOnClickListener {
            stopRingtone()
            val offer = currentOffer
            if (offer == null) {
                finish()
                return@setOnClickListener
            }
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (offer.optBoolean("video", false)) perms.add(Manifest.permission.CAMERA)
            val need = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            b.btnAnswer.visibility = View.GONE
            b.btnDecline.visibility = View.GONE
            if (need.isEmpty()) acceptCall(offer) else {
                currentOffer = offer
                permLauncher.launch(need.toTypedArray())
            }
        }
        b.btnDecline.setOnClickListener {
            send("reject", JSONObject())
            closeAll()
        }
        b.btnEnd.setOnClickListener {
            send("end", JSONObject())
            closeAll()
        }
        b.btnMute.setOnClickListener {
            muted = !muted
            localAudio?.setEnabled(!muted)
            b.btnMute.alpha = if (muted) 0.5f else 1f
        }
        b.btnSpeaker.setOnClickListener {
            speakerOn = !speakerOn
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = speakerOn
            b.btnSpeaker.alpha = if (speakerOn) 1f else 0.5f
        }
    }

    private fun startRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer.create(this, uri)
            player?.isLooping = true
            player?.start()
        } catch (e: Exception) {
        }
    }

    private fun stopRingtone() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    private fun startOutgoing() {
        setupPeer()
        createAndSendOffer()
        handler.postDelayed(fallback, 15000)
    }

    private fun acceptCall(offer: JSONObject) {
        stopRingtone()
        withVideo = offer.optBoolean("video", false)
        setupPeer()
        val sdp = SessionDescription(SessionDescription.Type.OFFER, offer.optString("sdp"))
        peer?.setRemoteDescription(SdpObs(), sdp)
        peer?.createAnswer(object : SdpObs() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peer?.setLocalDescription(SdpObs(), desc)
                val out = JSONObject()
                out.put("type", "answer")
                out.put("from", Pref.e164(this@InternetCallActivity))
                out.put("sdp", desc.description)
                send("answer", out)
            }
        }, MediaConstraints())
        applyVideoUi()
    }

    private fun createAndSendOffer() {
        peer?.createOffer(object : SdpObs() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peer?.setLocalDescription(SdpObs(), desc)
                val out = JSONObject()
                out.put("type", "offer")
                out.put("from", Pref.e164(this@InternetCallActivity))
                out.put("video", withVideo)
                out.put("sdp", desc.description)
                send("offer", out)
            }
        }, MediaConstraints())
        applyVideoUi()
    }

    private fun applyVideoUi() {
        if (!withVideo) {
            b.local.visibility = View.GONE
            b.remote.visibility = View.GONE
        } else {
            localVideo?.addSink(b.local)
        }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = speakerOn || withVideo
    }

    private fun setupPeer() {
        val stun = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        val config = PeerConnection.RTCConfiguration(listOf(stun))
        peer = pcFactory(this).createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val out = JSONObject()
                out.put("type", "ice")
                out.put("from", Pref.e164(this@InternetCallActivity))
                out.put("mid", candidate.sdpMid)
                out.put("idx", candidate.sdpMLineIndex)
                out.put("cand", candidate.sdp)
                Signal.send(Signal.topicOf(remoteAddr), out)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                runOnUiThread {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            connectedOnce = true
                            handler.removeCallbacks(fallback)
                            b.status.text = getString(R.string.ic_connected)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            b.status.text = getString(R.string.ic_failed)
                            handler.postDelayed(fallback, 1500)
                        }
                        else -> {}
                    }
                }
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                if (track is VideoTrack) {
                    runOnUiThread {
                        b.remote.visibility = View.VISIBLE
                        track.addSink(b.remote)
                    }
                }
            }

            override fun onAddStream(stream: MediaStream) {
                val vt = stream.videoTracks.firstOrNull()
                if (vt != null) runOnUiThread {
                    b.remote.visibility = View.VISIBLE
                    vt.addSink(b.remote)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
        addLocalTracks()
    }

    private fun addLocalTracks() {
        val factory = pcFactory(this)
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("audio0", audioSource)
        peer?.addTrack(localAudio, listOf("oson"))
        if (withVideo) {
            val enumerator = Camera2Enumerator(this)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (name == null) {
                withVideo = false
                applyVideoUi()
                return
            }
            videoCapturer = enumerator.createCapturer(name, null)
            helper = SurfaceTextureHelper.create("OsonCap", egl().eglBaseContext)
            videoSource = factory.createVideoSource(false)
            videoCapturer?.initialize(helper, this, videoSource!!.capturerObserver)
            videoCapturer?.startCapture(640, 480, 24)
            localVideo = factory.createVideoTrack("video0", videoSource)
            peer?.addTrack(localVideo, listOf("oson"))
            localVideo?.addSink(b.local)
        }
    }

    private fun onSignal(json: JSONObject) {
        when (json.optString("type")) {
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.optString("sdp"))
                peer?.setRemoteDescription(SdpObs(), sdp)
            }
            "ice" -> {
                try {
                    peer?.addIceCandidate(
                        IceCandidate(json.optString("mid"), json.optInt("idx"), json.optString("cand"))
                    )
                } catch (e: Exception) {
                }
            }
            "end", "reject" -> {
                Toast.makeText(this, R.string.call_ended, Toast.LENGTH_SHORT).show()
                closeAll()
            }
        }
    }

    fun onExternalSignal(json: JSONObject) = onSignal(json)

    private fun offerGsmFallback() {
        if (closed || connectedOnce) return
        AlertDialog.Builder(this)
            .setMessage(R.string.ic_fallback)
            .setPositiveButton(R.string.ic_to_sim) { _, _ ->
                try {
                    val canCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
                    startActivity(Intent(action, Uri.parse("tel:$remoteAddr")))
                } catch (e: Exception) {
                }
                closeAll()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> closeAll() }
            .setOnCancelListener { closeAll() }
            .show()
    }

    private fun send(type: String, extra: JSONObject) {
        val out = JSONObject()
        out.put("type", type)
        out.put("from", Pref.e164(this))
        extra.keys().forEach { out.put(it, extra.get(it)) }
        Signal.send(Signal.topicOf(remoteAddr), out)
    }

    private fun closeAll() {
        if (closed) return
        closed = true
        handler.removeCallbacks(fallback)
        stopRingtone()
        executor.execute {
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
            }
            try {
                peer?.close()
            } catch (e: Exception) {
            }
            try {
                videoCapturer?.dispose()
            } catch (e: Exception) {
            }
            try {
                videoSource?.dispose()
            } catch (e: Exception) {
            }
            try {
                audioSource?.dispose()
            } catch (e: Exception) {
            }
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(fallback)
        stopRingtone()
        instance = null
        try {
            b.remote.release()
        } catch (e: Exception) {
        }
        try {
            b.local.release()
        } catch (e: Exception) {
        }
        executor.shutdown()
    }

    open class SdpObs : org.webrtc.SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    companion object {
        const val EXTRA_JSON = "json"
        const val EXTRA_CALL = "call"
        const val EXTRA_VIDEO = "video"

        @Volatile
        var instance: InternetCallActivity? = null

        @Volatile
        private var factory: PeerConnectionFactory? = null

        @Volatile
        private var eglBase: EglBase? = null

        @Synchronized
        fun egl(): EglBase {
            if (eglBase == null) eglBase = EglBase.create()
            return eglBase!!
        }

        @Synchronized
        fun pcFactory(ctx: Context): PeerConnectionFactory {
            if (factory == null) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                        .createInitializationOptions()
                )
                factory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl().eglBaseContext, true, true))
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl().eglBaseContext))
                    .createPeerConnectionFactory()
            }
            return factory!!
        }

        fun intent(ctx: Context, callee: String, video: Boolean): Intent =
            Intent(ctx, InternetCallActivity::class.java)
                .putExtra(EXTRA_CALL, callee)
                .putExtra(EXTRA_VIDEO, video)
    }
}
