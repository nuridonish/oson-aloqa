package com.osonaloqa.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.databinding.ActivityChatBinding
import com.osonaloqa.app.databinding.ItemMsgInBinding
import com.osonaloqa.app.databinding.ItemMsgOutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var b: ActivityChatBinding
    private lateinit var address: String
    private lateinit var adapter: MsgAdapter
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var voiceFile: java.io.File? = null
    private var recording = false
    private var voicePlayer: android.media.MediaPlayer? = null
    private var playingUrl = ""
    private var voiceSpeed = 1.0f
    private var gid = ""
    private var gname = ""

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) handleMedia(uri)
        }

    private val smsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val typingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val from = intent.getStringExtra("from") ?: return
            if (from != address) return
            supportActionBar?.subtitle = getString(R.string.typing_now)
            handler.postDelayed({
                supportActionBar?.subtitle = getString(R.string.sms_subtitle)
            }, 2500)
        }
    }

    private val micPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceRecording()
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show()
        }

    private fun toggleRecord() {
        if (recording) {
            stopVoiceAndSend()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoiceRecording()
    }

    private fun startVoiceRecording() {
        try {
            val file = java.io.File(cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            voiceFile = file
            @Suppress("DEPRECATION")
            mediaRecorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recording = true
            b.btnMic.alpha = 0.4f
            b.btnMic.text = "\uD83D\uDF4A"
            Toast.makeText(this, "\uD83C\uDF99 \u0421\u0430\u0431\u0442...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Rec ERR: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVoiceAndSend() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {}
        recording = false
        b.btnMic.alpha = 1f
        b.btnMic.text = "\uD83C\uDF99"
        val file = voiceFile ?: return
        val base = com.osonaloqa.app.data.api.ServerApi.baseUrl(this)
        val token = com.osonaloqa.app.data.api.ServerApi.token(this)
        if (base.isEmpty() || token.isEmpty() || !file.exists()) {
            Toast.makeText(this, R.string.server_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val bytes = file.readBytes()
        Toast.makeText(this, R.string.uploading, Toast.LENGTH_SHORT).show()
        com.osonaloqa.app.data.api.ServerApi.uploadMedia(base, token, bytes) { resp ->
            if (resp != null && resp.optBoolean("ok")) {
                val link = com.osonaloqa.app.data.api.ServerApi.mediaUrl(base, resp.optString("id"), file.name)
                val target = if (gid.isNotEmpty()) gid else address
                val sent = com.osonaloqa.app.data.repo.MessageRepo.sendMedia(this, target, gid, link, "voice")
                com.osonaloqa.app.data.repo.MessageRepo.storeLocal(this, target, "\uD83C\uDF99 \u041E\u0432\u043E\u0437\u043B\u0438 \u043F\u0430\u0451\u043C", gid, gname, out = true, kind = "voice", enc = false, uuid = java.util.UUID.randomUUID().toString(), ackState = if (sent) "sent" else "queued")
                refresh()
            } else {
                Toast.makeText(this, R.string.media_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openMedia(m: com.osonaloqa.app.Msg) {
        when (m.kind) {
            "voice" -> toggleVoice(m.body)
            "video" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.body)))
            "image" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.body)))
            "file" -> {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val req = android.app.DownloadManager.Request(Uri.parse(m.body))
                    .setTitle(m.body.substringAfterLast('/'))
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                dm.enqueue(req)
                Toast.makeText(this, "\u0414\u0430\u0440\u0438 \u0434\u0430\u0440\u0438 \u0431\u0430\u0440\u0433\u0438\u0440\u0438...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleVoice(url: String) {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.isSpeakerphoneOn = true
        if (playingUrl == url) {
            val pl = voicePlayer
            if (pl != null) {
                if (pl.isPlaying) pl.pause() else pl.start()
            }
            return
        }
        try {
            voicePlayer?.release()
            voicePlayer = android.media.MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release(); voicePlayer = null; playingUrl = ""
                }
                prepare()
            }
            playingUrl = url
        } catch (e: Exception) {
            Toast.makeText(this, "Play ERR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleVoiceSpeed() {
        voiceSpeed = when (voiceSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        try { val pl = voicePlayer; if (pl != null) { val pp = pl.playbackParams; pp.setSpeed(voiceSpeed); pl.playbackParams = pp } } catch (e: Exception) {}
        Toast.makeText(this, "Speed: " + voiceSpeed + "x", Toast.LENGTH_SHORT).show()
    }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        address = intent.getStringExtra("address") ?: ""
        gid = intent.getStringExtra("gid") ?: ""
        gname = intent.getStringExtra("gname") ?: ""
        if (gname.isNotEmpty()) supportActionBar?.title = gname
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = address
        supportActionBar?.subtitle = getString(R.string.sms_subtitle)
        b.toolbar.setNavigationOnClickListener { finish() }
        adapter = MsgAdapter()
        b.list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.list.adapter = adapter
        adapter.onMediaClick = { m -> openMedia(m) }
        adapter.onVoiceSpeed = { m -> cycleVoiceSpeed() }
        b.btnSend.setOnClickListener { send() }
        b.btnAttach.setOnClickListener { showAttachDialog() }
        b.btnMic.text = "\uD83C\uDF99"
        b.btnMic.setOnClickListener { toggleRecord() }
        b.switchNet.isChecked = Pref.netCall(this)
        b.switchNet.setOnCheckedChangeListener { _, checked ->
            Pref.setNetCall(this, checked)
            Toast.makeText(
                this,
                if (checked) getString(R.string.net_switch_on) else getString(R.string.net_switch_off),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        currentAddress = address
        refresh()
        val filter = IntentFilter().apply {
            addAction(ACTION_NEW_MSG)
            addAction(ACTION_MSG_UPDATED)
        }
        ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, typingReceiver, IntentFilter(ACTION_TYPING), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        currentAddress = null
        unregisterReceiver(updateReceiver)
    }

    private fun refresh() {
        val sms = MessageStore.messages(address)
        val net = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.osonaloqa.app.data.db.AppDatabase.get(this@ChatActivity).netDao().forAddress(address, gid)
            }
        }
        val all = ArrayList(sms)
        net.forEach { n ->
            val shown = when (n.kind) {
                "image" -> "🖼 " + n.body
                "video" -> "🎬 " + n.body
                "file" -> "📄 " + n.body
                else -> n.body
            }
            all.add(com.osonaloqa.app.Msg(-n.id - 1, n.address, shown, n.ts, n.out, if (n.enc) 4 else 1))
        }
        all.sortBy { it.ts }
        adapter.submit(all)
        if (all.isNotEmpty()) b.list.scrollToPosition(all.size - 1)
    }

    private fun blockThisUser() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(getString(R.string.block_confirm) + "\n" + address)
            .setPositiveButton(R.string.block) { _, _ ->
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.osonaloqa.app.data.db.AppDatabase.get(this@ChatActivity)
                            .blockedDao()
                            .add(com.osonaloqa.app.data.db.BlockedUser(address, "", System.currentTimeMillis()))
                    }
                }
                Toast.makeText(this, R.string.blocked_added, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reportThisUser() {
        val edit = android.widget.EditText(this)
        edit.hint = getString(R.string.report_hint)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.report)
            .setView(edit)
            .setPositiveButton(R.string.otp_verify) { _, _ ->
                val base = com.osonaloqa.app.data.api.ServerApi.baseUrl(this)
                if (base.isEmpty()) {
                    Toast.makeText(this, R.string.server_missing, Toast.LENGTH_SHORT).show()
                } else {
                    com.osonaloqa.app.data.api.ServerApi.report(
                        base, com.osonaloqa.app.data.api.ServerApi.token(this), address, edit.text.toString()
                    ) {
                        Toast.makeText(this, R.string.report_sent, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isBlocked(): Boolean = try {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.osonaloqa.app.data.db.AppDatabase.get(this@ChatActivity).blockedDao().isBlocked(address)
            }
        }
    } catch (e: Exception) {
        false
    }

    private fun send() {
        val text = b.input.text.toString().trim()
        if (text.isEmpty()) return
        if (isBlocked()) {
            Toast.makeText(this, R.string.blocked_added, Toast.LENGTH_SHORT).show()
            return
        }
        val useNet = gid.isNotEmpty() || Pref.netCall(this)
        if (useNet) {
            val err = com.osonaloqa.app.data.repo.MessageRepo.sendText(this, if (gid.isEmpty()) address else gid, text, gid, gname)
            b.input.setText("")
            refresh()
            if (err != null) Toast.makeText(this, when (err) {
                "unreachable" -> getString(R.string.net_err_unreachable)
                "no_user" -> getString(R.string.net_err_no_user)
                else -> getString(R.string.net_send_failed)
            }, Toast.LENGTH_LONG).show()
            return
        }
        if (!SmsGateway.canSend(this)) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.perm_denied_sms)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                smsPermLauncher.launch(Manifest.permission.SEND_SMS)
            }
            return
        }
        val id = MessageStore.insert(address, text, System.currentTimeMillis(), true, 0)
        b.input.setText("")
        refresh()
        SmsGateway.send(this, address, text, id)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_call -> { call(); true }
        R.id.action_video -> { showVideoOption(); true }
        R.id.action_block -> { blockThisUser(); true }
        R.id.action_report -> { reportThisUser(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private var pendingKind = "file"

    private fun showAttachDialog() {
        val items = arrayOf(getString(R.string.media_photo), getString(R.string.media_video), getString(R.string.media_file))
        AlertDialog.Builder(this)
            .setTitle(R.string.attach)
            .setItems(items) { _, i ->
                pendingKind = when (i) {
                    0 -> "image"
                    1 -> "video"
                    else -> "file"
                }
                val mime = when (i) {
                    0 -> "image/*"
                    1 -> "video/*"
                    else -> "*/*"
                }
                pickLauncher.launch(mime)
            }
            .show()
    }

    private fun handleMedia(uri: android.net.Uri) {
        val base = com.osonaloqa.app.data.api.ServerApi.baseUrl(this)
        val token = com.osonaloqa.app.data.api.ServerApi.token(this)
        if (base.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, R.string.server_missing, Toast.LENGTH_LONG).show()
            return
        }
        try {
            var name = "file"
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    val size = c.getLong(1)
                    if (size > 20L * 1024 * 1024) {
                        Toast.makeText(this, R.string.media_too_big, Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            Toast.makeText(this, R.string.uploading, Toast.LENGTH_SHORT).show()
            com.osonaloqa.app.data.api.ServerApi.uploadMedia(base, token, bytes) { resp ->
                if (resp != null && resp.optBoolean("ok")) {
                    val link = com.osonaloqa.app.data.api.ServerApi.mediaUrl(base, resp.optString("id"), name)
                    val target = if (gid.isNotEmpty()) gid else address
                    val viaNetErr = if (Pref.netCall(this) || gid.isNotEmpty()) {
                        if (com.osonaloqa.app.data.repo.MessageRepo.sendMedia(this, target, gid, link, pendingKind)) null else "failed"
                    } else "sms"
                    val viaNet = viaNetErr == null
                    if (!viaNet && viaNetErr != "sms") {
                        Toast.makeText(this, when (viaNetErr) {
                            "unreachable" -> getString(R.string.net_err_unreachable)
                            "no_user" -> getString(R.string.net_err_no_user)
                            else -> getString(R.string.net_send_failed)
                        }, Toast.LENGTH_LONG).show()
                    }
                    if (viaNet) {
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.osonaloqa.app.data.db.AppDatabase.get(this@ChatActivity).netDao().add(
                                    com.osonaloqa.app.data.db.NetMessage(address = address, body = link, ts = System.currentTimeMillis(), out = true, kind = pendingKind, enc = false)
                                )
                            }
                        }
                        refresh()
                    } else {
                        val id = com.osonaloqa.app.MessageStore.insert(address, link, System.currentTimeMillis(), true, 0)
                        refresh()
                        com.osonaloqa.app.SmsGateway.send(this, address, link, id)
                    }
                } else {
                    Toast.makeText(this, R.string.media_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.media_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideoOption() {
        val online = try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }
        if (!online) {
            Toast.makeText(this, R.string.video_no_internet, Toast.LENGTH_LONG).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.video_action)
                .setItems(arrayOf(getString(R.string.video_call_int), getString(R.string.audio_call_int))) { _, i ->
                    startActivity(InternetCallActivity.intent(this, address, i == 0))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun call() {
        if (Pref.netCall(this)) {
            val online = try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.activeNetworkInfo?.isConnected == true
            } catch (e: Exception) {
                false
            }
            if (online) {
                startActivity(InternetCallActivity.intent(this, address, false))
                return
            }
            Toast.makeText(this, R.string.video_no_internet, Toast.LENGTH_LONG).show()
        }
        if (Pref.sipConfigured(this)) {
            val options = arrayOf(getString(R.string.sim_call), getString(R.string.sip_call))
            AlertDialog.Builder(this)
                .setTitle(R.string.call)
                .setItems(options) { _, i ->
                    if (i == 0) dialGsm() else startActivity(Intent(this, SipCallActivity::class.java).putExtra("number", address))
                }
                .show()
            return
        }
        dialGsm()
    }

    private fun dialGsm() {
        val canCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:" + address)))
    }

    class MsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Msg> = emptyList()
        private val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        var onMediaClick: ((Msg) -> Unit)? = null
        var onVoiceSpeed: ((Msg) -> Unit)? = null

        fun submit(list: List<Msg>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = if (items[position].out) 1 else 0

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = android.view.LayoutInflater.from(parent.context)
            return if (viewType == 1) OutVH(ItemMsgOutBinding.inflate(inf, parent, false))
            else InVH(ItemMsgInBinding.inflate(inf, parent, false))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val m = items[position]
            val time = fmt.format(Date(m.ts))
            when (holder) {
                is OutVH -> bind(holder.b, m, time, true)
                is InVH -> bind(holder.b, m, time, false)
            }
        }

        private fun bind(b: Any, m: Msg, time: String, out: Boolean) {
            val media: android.widget.ImageView?
            var bubble: android.widget.TextView
            var timeView: android.widget.TextView
            if (b is ItemMsgOutBinding) {
                media = b.media; bubble = b.bubble; timeView = b.time
            } else {
                b as ItemMsgInBinding
                media = b.media; bubble = b.bubble; timeView = b.time
            }
            bubble.text = m.body
            timeView.text = if (out) time + statusMark(m.status) else time
            if (m.kind == "image" || m.kind == "video") {
                media.visibility = android.view.View.VISIBLE
                bubble.visibility = android.view.View.GONE
                com.bumptech.glide.Glide.with(media.context).load(m.body).into(media)
                media.setOnClickListener { onMediaClick?.invoke(m) }
            } else {
                media.visibility = android.view.View.GONE
                bubble.visibility = android.view.View.VISIBLE
                bubble.text = when (m.kind) {
                    "voice" -> "\uD83C\uDF99 " + m.body.substringAfterLast('/')
                    "file" -> "\uD83D\uDCC4 " + m.body.substringAfterLast('/')
                    else -> m.body
                }
            }
            (bubble.parent as? android.view.ViewGroup)?.setOnClickListener {
                when (m.kind) {
                    "image", "video" -> onMediaClick?.invoke(m)
                    "voice" -> onVoiceSpeed?.invoke(m)
                }
            }
        }

        private fun statusMark(status: Int): String = when (status) {
            0 -> " \u23F3"
            1 -> " \u2713"
            2 -> " \u2713\u2713"
            3 -> " \u2717"
            4 -> " \uD83D\uDD10"
            else -> " \u2713"
        }

        class OutVH(val b: ItemMsgOutBinding) : RecyclerView.ViewHolder(b.root)
        class InVH(val b: ItemMsgInBinding) : RecyclerView.ViewHolder(b.root)
    }
    companion object {
        const val ACTION_TYPING = "com.osonaloqa.app.TYPING"
        const val ACTION_NEW_MSG = "com.osonaloqa.app.NEW_MSG"
        const val ACTION_MSG_UPDATED = "com.osonaloqa.app.MSG_UPDATED"
        var currentAddress: String? = null

        fun intent(context: Context, address: String): Intent =
            Intent(context, ChatActivity::class.java).putExtra("address", address)

        fun groupIntent(context: Context, gid: String, gname: String): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra("address", gid)
                .putExtra("gid", gid)
                .putExtra("gname", gname)
    }
}



















