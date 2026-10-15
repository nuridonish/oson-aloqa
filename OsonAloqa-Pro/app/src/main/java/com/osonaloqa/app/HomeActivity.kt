package com.osonaloqa.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.data.api.NetSync
import com.osonaloqa.app.data.api.ServerApi
import com.osonaloqa.app.data.api.WsClient
import com.osonaloqa.app.data.db.AppDatabase
import com.osonaloqa.app.data.db.BlockedUser
import com.osonaloqa.app.data.db.NetMessage
import com.osonaloqa.app.data.model.OperatorDetector
import com.osonaloqa.app.databinding.ActivityHomeBinding
import com.osonaloqa.app.databinding.ItemCallBinding
import com.osonaloqa.app.databinding.ItemContactBinding
import com.osonaloqa.app.databinding.ItemStatusBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

data class CallEntry(val number: String, val type: Int, val date: Long, val duration: String)
data class ContactEntry(val name: String, val number: String, val operator: String)
data class GroupRow(val gid: String, val name: String, val type: String)
data class StatusRow(val phone: String, val name: String, val text: String, val media: String, val ts: Long)

data class ChatRow(
    val address: String,
    val display: String,
    val snippet: String,
    val ts: Long,
    val isGroup: Boolean,
    val gid: String,
    val unread: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false
)

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private lateinit var convAdapter: ConvAdapter
    private lateinit var callAdapter: CallAdapter
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var statusAdapter: StatusAdapter
    private var allContacts: List<ContactEntry> = emptyList()
    private var groups: List<GroupRow> = emptyList()
    private var chatFilter = "all"

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val callLogLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshCalls() }
    private val contactsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshContacts() else showTab(b.tabContacts.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Pref.registered(this)) {
            startActivity(Intent(this, com.osonaloqa.app.ui.auth.PhoneActivity::class.java))
            finish()
            return
        }
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        convAdapter = ConvAdapter({ row -> openChat(row) }, { row -> showChatOptions(row) })
        b.tabChats.list.layoutManager = LinearLayoutManager(this)
        b.tabChats.list.adapter = convAdapter
        b.tabChats.fab.setOnClickListener { startActivity(Intent(this, NewChatActivity::class.java)) }
        b.tabChats.chipAll.setOnClickListener { setFilter("all") }
        b.tabChats.chipGroups.setOnClickListener { setFilter("groups") }
        b.tabChats.chipChannels.setOnClickListener { setFilter("channels") }
        b.tabChats.chipArchive.setOnClickListener { setFilter("archive") }

        callAdapter = CallAdapter { entry -> confirmCall(entry.number) }
        b.tabCalls.list.layoutManager = LinearLayoutManager(this)
        b.tabCalls.list.adapter = callAdapter
        b.tabCalls.btnGrant.setOnClickListener { callLogLauncher.launch(Manifest.permission.READ_CALL_LOG) }

        contactAdapter = ContactAdapter { entry -> showContactActions(entry) }
        b.tabContacts.list.layoutManager = LinearLayoutManager(this)
        b.tabContacts.list.adapter = contactAdapter
        b.tabContacts.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filterContacts(s?.toString() ?: "") }
        })

        statusAdapter = StatusAdapter()
        b.tabStatus.list.layoutManager = LinearLayoutManager(this)
        b.tabStatus.list.adapter = statusAdapter
        b.tabStatus.fab.setOnClickListener { showAddStatus() }

        b.tabBalance.btnTcell.setOnClickListener { ussd("*111#") }
        b.tabBalance.btnBabilon.setOnClickListener { ussd("*105#") }
        b.tabBalance.btnBeelineTj.setOnClickListener { ussd("*100#") }
        b.tabBalance.btnMtsRu.setOnClickListener { ussd("*100#") }
        b.tabBalance.btnBeelineRu.setOnClickListener { ussd("*102#") }
        b.tabBalance.btnMegafonRu.setOnClickListener { ussd("*100#") }
        b.tabBalance.btnTele2Ru.setOnClickListener { ussd("*105#") }
        b.tabBalance.btnCustom.setOnClickListener {
            val code = b.tabBalance.customCode.text.toString().trim()
            if (code.isNotEmpty()) ussd(code) else Toast.makeText(this, R.string.ussd_hint, Toast.LENGTH_SHORT).show()
        }

        b.tabProfile.tvName.text = Pref.name(this)
        b.tabProfile.tvNumber.text = Pref.e164(this)
        b.tabProfile.tvOperator.text = getString(R.string.operator_label) + ": " + OperatorDetector.detect(Pref.e164(this)).name
        b.tabProfile.btnPerms.setOnClickListener { showPermsDialog() }
        b.tabProfile.btnSip.setOnClickListener { showSipDialog() }
        b.tabProfile.btnServer.setOnClickListener { showServerDialog() }
        b.tabProfile.btnBlocked.setOnClickListener { showBlocked() }
        b.tabProfile.btnAbout.setOnClickListener { showAbout() }
        b.tabProfile.btnLogout.setOnClickListener { confirmLogout() }

        b.bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> { showTab(b.tabChats.root); refreshChats(); true }
                R.id.nav_calls -> { showTab(b.tabCalls.root); refreshCalls(); true }
                R.id.nav_contacts -> { showTab(b.tabContacts.root); refreshContacts(); true }
                R.id.nav_status -> { showTab(b.tabStatus.root); refreshStatuses(); true }
                else -> { showTab(b.tabProfile.root); true }
            }
        }
        showTab(b.tabChats.root)
        setFilter("all")

        try { startService(Intent(this, CallSignalService::class.java)) } catch (e: Exception) {}

        WsClient.addListener { frame -> runOnUiThread { handleFrame(frame) } }
        WsClient.connect(applicationContext)

        lifecycleScope.launch {
            while (true) {
                try { NetSync.poll(applicationContext) } catch (e: Exception) {}
                kotlinx.coroutines.delay(15000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshChats()
        b.tabProfile.tvName.text = Pref.name(this)
        b.tabProfile.tvNumber.text = Pref.e164(this)
        b.tabProfile.tvOperator.text = getString(R.string.operator_label) + ": " + OperatorDetector.detect(Pref.e164(this)).name
        supportActionBar?.subtitle = Pref.e164(this)
    }

    private fun handleFrame(frame: JSONObject) {
        val type = frame.optString("type")
        when (type) {
            "message.new" -> {
                val m = frame.optJSONObject("message") ?: return
                val from = m.optString("from")
                val gid = m.optString("gid", "")
                val body = m.optString("body", "")
                val ts = m.optLong("ts", System.currentTimeMillis())
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.get(applicationContext).netDao()
                    if (gid.isNotEmpty()) {
                        if (dao.existsGroup(gid, body, ts) == null) {
                            dao.add(NetMessage(address = gid, body = body, ts = ts, out = false, kind = m.optString("kind", "text"), enc = m.optBoolean("enc"), gid = gid, gname = m.optString("gname")))
                        }
                    } else {
                        val last = dao.lastDirect(from)
                        if (last == null || last.ts != ts || last.body != body) {
                            dao.add(NetMessage(address = from, body = body, ts = ts, out = false, kind = m.optString("kind", "text"), enc = m.optBoolean("enc"), seq = m.optLong("seq", 0)))
                        }
                    }
                }
                val chatKey = if (gid.isNotEmpty()) gid else from
                if (ChatActivity.currentAddress != chatKey) {
                    showMsgNotification(chatKey, body)
                    incrUnread(chatKey)
                }
                sendBroadcast(Intent(ChatActivity.ACTION_NEW_MSG).setPackage(packageName))
                refreshChats()
            }
            "message.ack" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.get(applicationContext).netDao()
                    val uuid = frame.optString("uuid", "")
                    val seq = frame.optLong("seq", 0)
                    val state = frame.optString("state", "delivered")
                    if (uuid.isNotEmpty()) dao.setAck(uuid, state) else dao.setAckBySeq(seq, state)
                }
                sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(packageName))
            }
            "edit" -> {
                val seq = frame.optLong("seq", 0)
                val body = frame.optString("body", "")
                lifecycleScope.launch(Dispatchers.IO) { AppDatabase.get(applicationContext).netDao().applyEditBySeq(seq, body) }
                sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(packageName))
            }
            "delete" -> {
                val seq = frame.optLong("seq", 0)
                lifecycleScope.launch(Dispatchers.IO) { AppDatabase.get(applicationContext).netDao().applyDeleteBySeq(seq) }
                sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(packageName))
            }
            "typing" -> {
                val from = frame.optString("from", "")
                if (from.isNotEmpty()) sendBroadcast(Intent(ChatActivity.ACTION_TYPING).setPackage(packageName).putExtra("from", from))
            }
        }
    }

    private fun showMsgNotification(from: String, body: String) {
        if (Pref.muted(this).contains(from)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel("osonaloqa_msg", getString(R.string.channel_msg), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = android.app.PendingIntent.getActivity(this, from.hashCode(), ChatActivity.intent(this, from), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = androidx.core.app.NotificationCompat.Builder(this, "osonaloqa_msg")
            .setSmallIcon(R.drawable.ic_msg_small)
            .setContentTitle(from)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(from.hashCode(), n)
    }

    private fun incrUnread(key: String) {
        val sp = getSharedPreferences("osonaloqa", Context.MODE_PRIVATE)
        sp.edit().putInt("unread_" + key, sp.getInt("unread_" + key, 0) + 1).apply()
    }

    private fun clearUnread(key: String) {
        getSharedPreferences("osonaloqa", Context.MODE_PRIVATE).edit().putInt("unread_" + key, 0).apply()
    }

    private fun unreadFor(key: String): Int =
        getSharedPreferences("osonaloqa", Context.MODE_PRIVATE).getInt("unread_" + key, 0)

    private fun setFilter(f: String) {
        chatFilter = f
        val active = android.graphics.Typeface.BOLD
        b.tabChats.chipAll.setTypeface(null, if (f == "all") active else android.graphics.Typeface.NORMAL)
        b.tabChats.chipGroups.setTypeface(null, if (f == "groups") active else android.graphics.Typeface.NORMAL)
        b.tabChats.chipChannels.setTypeface(null, if (f == "channels") active else android.graphics.Typeface.NORMAL)
        b.tabChats.chipArchive.setTypeface(null, if (f == "archive") active else android.graphics.Typeface.NORMAL)
        refreshChats()
    }

    private fun openChat(row: ChatRow) {
        clearUnread(row.address + "|" + row.gid)
        val i = ChatActivity.intent(this, row.address)
        if (row.isGroup) i.putExtra("gid", row.gid).putExtra("gname", row.display)
        startActivity(i)
    }

    private fun showChatOptions(row: ChatRow) {
        val key = row.address + "|" + row.gid
        val isArchived = Pref.archived(this).contains(key)
        val isMuted = Pref.muted(this).contains(key)
        val isPinned = Pref.pinned(this).contains(key)
        val options = mutableListOf(
            if (isPinned) getString(R.string.unpin) else getString(R.string.pin),
            if (isMuted) getString(R.string.unmute) else getString(R.string.mute),
            if (isArchived) getString(R.string.unarchive) else getString(R.string.archive)
        )
        AlertDialog.Builder(this)
            .setTitle(row.display)
            .setItems(options.toTypedArray()) { _, i ->
                when (i) {
                    0 -> { val s = Pref.pinned(this); if (isPinned) s.remove(key) else s.add(key); Pref.setPinned(this, s) }
                    1 -> { val s = Pref.muted(this); if (isMuted) s.remove(key) else s.add(key); Pref.setMuted(this, s) }
                    2 -> { val s = Pref.archived(this); if (isArchived) s.remove(key) else s.add(key); Pref.setArchived(this, s) }
                }
                refreshChats()
            }
            .show()
    }

    private fun showTab(view: View) {
        listOf(b.tabChats.root, b.tabCalls.root, b.tabContacts.root, b.tabStatus.root, b.tabBalance.root, b.tabProfile.root).forEach {
            it.visibility = if (it === view) View.VISIBLE else View.GONE
        }
    }

    private fun refreshChats() {
        val archivedSet = Pref.archived(this)
        lifecycleScope.launch {
            val rows = ArrayList<ChatRow>()
            withContext(Dispatchers.IO) {
                groups = try {
                    val base = ServerApi.baseUrl(this@HomeActivity)
                    val token = ServerApi.token(this@HomeActivity)
                    if (base.isEmpty() || token.isEmpty()) emptyList()
                    else ServerApi.myGroupsBlocking(base, token)?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val o = arr.optJSONObject(i) ?: return@map null
                            GroupRow(o.optString("id"), o.optString("name"), o.optString("type", "group"))
                        }.filterNotNull()
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            MessageStore.conversations().forEach { c ->
                rows.add(ChatRow(c.address, c.address, if (c.out) "→ ${c.lastBody}" else c.lastBody, c.ts, false, ""))
            }
            val groupMsgs = withContext(Dispatchers.IO) {
                val out = LinkedHashMap<String, NetMessage>()
                AppDatabase.get(this@HomeActivity).netDao().allDesc().forEach { m ->
                    val key = if (m.gid.isNotEmpty()) "g:" + m.gid else "d:" + m.address
                    if (!out.containsKey(key)) out[key] = m
                }
                out.values.toList()
            }
            groupMsgs.forEach { m ->
                val isG = m.gid.isNotEmpty()
                val g = groups.firstOrNull { it.gid == m.gid }
                val disp = if (isG) (m.gname.ifEmpty { g?.name ?: "Group" }) else m.address
                val existing = rows.firstOrNull { it.address == m.address && it.gid == m.gid }
                if (existing == null || existing.ts < m.ts) {
                    rows.removeAll { it.address == m.address && it.gid == m.gid }
                    rows.add(ChatRow(m.address, disp, if (m.out) "→ ${m.body}" else m.body, m.ts, isG, m.gid))
                }
            }
            groups.forEach { g ->
                if (rows.none { it.gid == g.gid }) {
                    rows.add(ChatRow(g.gid, g.name, getString(R.string.group_hint), 0, true, g.gid))
                }
            }
            rows.forEachIndexed { idx, r ->
                val key = r.address + "|" + r.gid
                val u = unreadFor(key)
                val pinned = Pref.pinned(this@HomeActivity).contains(key)
                val muted = Pref.muted(this@HomeActivity).contains(key)
                rows[idx] = ChatRow(r.address, r.display, r.snippet, r.ts, r.isGroup, r.gid, u, pinned, muted)
            }
            rows.sortWith(compareByDescending<ChatRow> { it.pinned }.thenByDescending { it.ts })
            val filtered = rows.filter { r ->
                val key = r.address + "|" + r.gid
                val isArch = archivedSet.contains(key)
                when (chatFilter) {
                    "all" -> !isArch
                    "archive" -> isArch
                    "groups" -> r.isGroup && !isArch && groups.firstOrNull { it.gid == r.gid }?.type != "channel"
                    "channels" -> r.isGroup && !isArch && groups.firstOrNull { it.gid == r.gid }?.type == "channel"
                    else -> !isArch
                }
            }
            withContext(Dispatchers.Main) {
                convAdapter.submitList(filtered)
                b.tabChats.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshCalls() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            b.tabCalls.btnGrant.visibility = View.VISIBLE
            b.tabCalls.list.visibility = View.GONE
            b.tabCalls.empty.visibility = View.GONE
            return
        }
        b.tabCalls.btnGrant.visibility = View.GONE
        b.tabCalls.list.visibility = View.VISIBLE
        val entries = ArrayList<CallEntry>()
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && entries.size < 100) {
                    val num = c.getString(0) ?: "—"
                    entries.add(CallEntry(num, c.getInt(1), c.getLong(2), c.getLong(3).toString() + " " + getString(R.string.seconds)))
                }
            }
        } catch (e: Exception) {}
        callAdapter.submit(entries)
        b.tabCalls.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshContacts() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        val list = ArrayList<ContactEntry>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && list.size < 300) {
                    val name = c.getString(0) ?: continue
                    val raw = c.getString(1) ?: continue
                    val num = raw.replace(Regex("[\\s\\-()]"), "")
                    if (num.isEmpty()) continue
                    if (!seen.add(num)) continue
                    list.add(ContactEntry(name, num, OperatorDetector.detect(num).name))
                }
            }
        } catch (e: Exception) {}
        allContacts = list
        filterContacts(b.tabContacts.search.text?.toString() ?: "")
    }

    private fun filterContacts(q: String) {
        val query = q.trim().lowercase(Locale.getDefault())
        val filtered = if (query.isEmpty()) allContacts else allContacts.filter {
            it.name.lowercase(Locale.getDefault()).contains(query) || it.number.contains(query.removePrefix("+"))
        }
        contactAdapter.submit(filtered)
        b.tabContacts.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshStatuses() {
        lifecycleScope.launch {
            val feed = withContext(Dispatchers.IO) {
                val base = ServerApi.baseUrl(this@HomeActivity)
                val token = ServerApi.token(this@HomeActivity)
                if (base.isEmpty() || token.isEmpty()) emptyList()
                else ServerApi.feedBlocking(base, token)?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val o = arr.optJSONObject(i) ?: return@map null
                        StatusRow(o.optString("phone"), o.optString("name", o.optString("phone")), o.optString("text"), o.optString("media"), o.optLong("ts"))
                    }.filterNotNull()
                } ?: emptyList()
            }
            statusAdapter.submit(feed)
            b.tabStatus.empty.visibility = if (feed.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddStatus() {
        val base = ServerApi.baseUrl(this)
        val token = ServerApi.token(this)
        if (base.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, R.string.server_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val edit = android.widget.EditText(this)
        edit.hint = getString(R.string.status_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_status)
            .setView(edit)
            .setPositiveButton(R.string.otp_verify) { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { ServerApi.postStatusBlocking(base, token, text) }
                        Toast.makeText(this@HomeActivity, if (ok) R.string.saved else R.string.media_failed, Toast.LENGTH_SHORT).show()
                        refreshStatuses()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showContactActions(entry: ContactEntry) {
        val actions = arrayOf(getString(R.string.open_chat), getString(R.string.call), getString(R.string.block))
        AlertDialog.Builder(this)
            .setTitle(entry.name + "\n" + entry.number)
            .setItems(actions) { _, i ->
                when (i) {
                    0 -> startActivity(ChatActivity.intent(this, entry.number))
                    1 -> confirmCall(entry.number)
                    else -> blockUser(entry.number, entry.name)
                }
            }
            .show()
    }

    private fun blockUser(address: String, name: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.get(this@HomeActivity).blockedDao().add(BlockedUser(address, name, System.currentTimeMillis()))
            }
            Toast.makeText(this@HomeActivity, R.string.blocked_added, Toast.LENGTH_SHORT).show()
        }
        val base = ServerApi.baseUrl(this)
        if (base.isNotEmpty()) ServerApi.addBlock(base, ServerApi.token(this), address)
    }

    private fun showBlocked() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { AppDatabase.get(this@HomeActivity).blockedDao().list() }
            if (list.isEmpty()) {
                Toast.makeText(this@HomeActivity, R.string.no_blocked, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = list.map { (it.name.ifEmpty { "?" }) + "\n" + it.address }.toTypedArray()
            AlertDialog.Builder(this@HomeActivity)
                .setTitle(R.string.blocked_list)
                .setItems(names) { _, i ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { AppDatabase.get(this@HomeActivity).blockedDao().remove(list[i].address) }
                        Toast.makeText(this@HomeActivity, R.string.unblocked, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun confirmCall(number: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.call_confirm) + "\n" + number)
            .setPositiveButton(R.string.call) { _, _ -> dial(number) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dial(number: String) {
        val canCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(number))))
    }

    private fun ussd(code: String) {
        val canCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(code))))
    }

    private fun requestDialerRole() {
        val rm = getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
            if (rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                Toast.makeText(this, R.string.already_default, Toast.LENGTH_SHORT).show()
                return
            }
            roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER))
            return
        }
        Toast.makeText(this, R.string.default_unsupported, Toast.LENGTH_SHORT).show()
    }

    private fun requestSmsRole() {
        val rm = getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
            if (rm.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                Toast.makeText(this, R.string.already_default, Toast.LENGTH_SHORT).show()
                return
            }
            roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
            return
        }
        Toast.makeText(this, R.string.default_unsupported, Toast.LENGTH_SHORT).show()
    }

    private fun showLanguageDialog() {
        val names = arrayOf("Тоҷикӣ", "Русский", "English", "Oʻzbekcha")
        val tags = arrayOf("tg", "ru", "en", "uz")
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setItems(names) { _, i -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[i])) }
            .show()
    }

    private fun showPermsDialog() {
        fun mark(p: String): String =
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) "✅" else "❌"
        val sms = mark(Manifest.permission.SEND_SMS) + " " + getString(R.string.perm_sms)
        val phone = mark(Manifest.permission.CALL_PHONE) + " " + getString(R.string.perm_phone)
        val contacts = mark(Manifest.permission.READ_CONTACTS) + " " + getString(R.string.perm_contacts)
        val notif = if (Build.VERSION.SDK_INT >= 33) mark(Manifest.permission.POST_NOTIFICATIONS) + " " + getString(R.string.perm_notif) else "✅ " + getString(R.string.perm_notif)
        val logLine = mark(Manifest.permission.READ_CALL_LOG) + " " + getString(R.string.tab_calls)
        AlertDialog.Builder(this)
            .setTitle(R.string.perms_title)
            .setMessage("$sms\n$phone\n$contacts\n$logLine\n$notif\n\n" + getString(R.string.perm_hint))
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSipDialog() {
        val lin = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val host = android.widget.EditText(this).apply { hint = getString(R.string.sip_server_hint); setText(Pref.sipHost(this@HomeActivity)) }
        val user = android.widget.EditText(this).apply { hint = getString(R.string.sip_user_hint); setText(Pref.sipUser(this@HomeActivity)) }
        val pass = android.widget.EditText(this).apply {
            hint = getString(R.string.sip_pass_hint)
            setText(Pref.sipPass(this@HomeActivity))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        lin.addView(host); lin.addView(user); lin.addView(pass)
        AlertDialog.Builder(this)
            .setTitle(R.string.sip_settings)
            .setView(lin)
            .setPositiveButton(R.string.otp_verify) { _, _ ->
                Pref.setSip(this, host.text.toString(), user.text.toString(), pass.text.toString())
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showServerDialog() {
        val edit = android.widget.EditText(this)
        edit.hint = getString(R.string.server_url_hint)
        edit.setText(ServerApi.baseUrl(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.server_settings)
            .setView(edit)
            .setPositiveButton(R.string.otp_verify) { _, _ ->
                ServerApi.setBaseUrl(this, edit.text.toString())
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.logout) { _, _ ->
                Pref.logout(this)
                startActivity(Intent(this, com.osonaloqa.app.ui.auth.PhoneActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_profile -> { showTab(b.tabProfile.root); true }
        R.id.menu_perms -> { showPermsDialog(); true }
        R.id.menu_balance -> { showTab(b.tabBalance.root); true }
        R.id.menu_language -> { showLanguageDialog(); true }
        R.id.menu_server -> { showServerDialog(); true }
        R.id.menu_sip -> { showSipDialog(); true }
        R.id.menu_about -> { showAbout(); true }
        R.id.menu_logout -> { confirmLogout(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private class CallAdapter(var onClick: (CallEntry) -> Unit) : RecyclerView.Adapter<CallAdapter.VH>() {
        private var items: List<CallEntry> = emptyList()
        fun submit(list: List<CallEntry>) { items = list; notifyDataSetChanged() }
        class VH(val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(ItemCallBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val ctx = holder.b.root.context
            holder.b.number.text = e.number
            holder.b.time.text = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(e.date) + " • " + e.duration
            when (e.type) {
                CallLog.Calls.OUTGOING_TYPE -> { holder.b.icon.text = "↗"; holder.b.icon.setTextColor(ContextCompat.getColor(ctx, R.color.primary)) }
                CallLog.Calls.MISSED_TYPE -> { holder.b.icon.text = "✗"; holder.b.icon.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark)) }
                else -> { holder.b.icon.text = "↙"; holder.b.icon.setTextColor(ContextCompat.getColor(ctx, R.color.text_main)) }
            }
            holder.b.root.setOnClickListener { onClick(e) }
        }
    }

    private class ContactAdapter(var onClick: (ContactEntry) -> Unit) : RecyclerView.Adapter<ContactAdapter.VH>() {
        private var items: List<ContactEntry> = emptyList()
        fun submit(list: List<ContactEntry>) { items = list; notifyDataSetChanged() }
        class VH(val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(ItemContactBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.b.avatar.text = e.name.take(1).uppercase(Locale.getDefault())
            holder.b.name.text = e.name
            holder.b.number.text = e.number
            holder.b.operator.text = e.operator
            holder.b.root.setOnClickListener { onClick(e) }
        }
    }

    private class StatusAdapter : RecyclerView.Adapter<StatusAdapter.VH>() {
        private var items: List<StatusRow> = emptyList()
        fun submit(list: List<StatusRow>) { items = list; notifyDataSetChanged() }
        class VH(val b: ItemStatusBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(ItemStatusBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.b.avatar.text = s.name.take(1).uppercase(Locale.getDefault())
            holder.b.name.text = s.name
            holder.b.text.text = s.text
            holder.b.time.text = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(s.ts)
        }
    }
}

