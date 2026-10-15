package com.osonaloqa.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.databinding.ActivityHomeBinding
import com.osonaloqa.app.databinding.ItemCallBinding

data class CallEntry(val number: String, val type: Int, val date: Long, val duration: String)

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private lateinit var convAdapter: ConvAdapter
    private lateinit var callAdapter: CallAdapter

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val callLogLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshCalls()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Pref.registered(this)) {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
            return
        }
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        convAdapter = ConvAdapter { conv ->
            startActivity(ChatActivity.intent(this, conv.address))
        }
        b.tabChats.list.layoutManager = LinearLayoutManager(this)
        b.tabChats.list.adapter = convAdapter
        b.tabChats.fab.setOnClickListener { startActivity(Intent(this, NewChatActivity::class.java)) }

        callAdapter = CallAdapter { entry ->
            confirmCall(entry.number)
        }
        b.tabCalls.list.layoutManager = LinearLayoutManager(this)
        b.tabCalls.list.adapter = callAdapter
        b.tabCalls.btnGrant.setOnClickListener {
            callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }

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

        b.tabProfile.btnLanguage.setOnClickListener { showLanguageDialog() }
        b.tabProfile.btnDefaultPhone.setOnClickListener { requestDialerRole() }
        b.tabProfile.btnDefaultSms.setOnClickListener { requestSmsRole() }
        b.tabProfile.btnAbout.setOnClickListener { showAbout() }
        b.tabProfile.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout) { _, _ ->
                    Pref.logout(this)
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        b.bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> { showTab(b.tabChats.root); refreshChats(); true }
                R.id.nav_calls -> { showTab(b.tabCalls.root); refreshCalls(); true }
                R.id.nav_balance -> { showTab(b.tabBalance.root); true }
                else -> { showTab(b.tabProfile.root); true }
            }
        }
        showTab(b.tabChats.root)
    }

    override fun onResume() {
        super.onResume()
        refreshChats()
        b.tabProfile.tvName.text = Pref.name(this)
        b.tabProfile.tvNumber.text = Pref.e164(this)
        supportActionBar?.subtitle = Pref.e164(this)
    }

    private fun showTab(view: View) {
        listOf(b.tabChats.root, b.tabCalls.root, b.tabBalance.root, b.tabProfile.root).forEach {
            it.visibility = if (it === view) View.VISIBLE else View.GONE
        }
    }

    private fun refreshChats() {
        val convs = MessageStore.conversations()
        convAdapter.submit(convs)
        b.tabChats.empty.visibility = if (convs.isEmpty()) View.VISIBLE else View.GONE
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
                null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && entries.size < 100) {
                    val num = c.getString(0) ?: "—"
                    val type = c.getInt(1)
                    val date = c.getLong(2)
                    val dur = c.getLong(3).toString() + " " + getString(R.string.seconds)
                    entries.add(CallEntry(num, type, date, dur))
                }
            }
        } catch (e: Exception) {
        }
        callAdapter.submit(entries)
        b.tabCalls.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
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
        if (Build.VERSION.SDK_INT >= 29) {
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
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                })
            } catch (e: Exception) {
                Toast.makeText(this, R.string.default_unsupported, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.default_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestSmsRole() {
        if (Build.VERSION.SDK_INT >= 29) {
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
        } else {
            val i = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            try {
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.default_unsupported, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLanguageDialog() {
        val names = arrayOf("Тоҷикӣ", "Русский", "English", "Oʻzbekcha")
        val tags = arrayOf("tg", "ru", "en", "uz")
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setItems(names) { _, i ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[i]))
            }
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private class CallAdapter(var onClick: (CallEntry) -> Unit) :
        RecyclerView.Adapter<CallAdapter.VH>() {

        private var items: List<CallEntry> = emptyList()

        fun submit(list: List<CallEntry>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(ItemCallBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val ctx = holder.b.root.context
            holder.b.number.text = e.number
            holder.b.time.text = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(e.date) + " • " + e.duration
            when (e.type) {
                CallLog.Calls.OUTGOING_TYPE -> {
                    holder.b.icon.text = "↗"
                    holder.b.icon.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                }
                CallLog.Calls.MISSED_TYPE -> {
                    holder.b.icon.text = "✗"
                    holder.b.icon.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
                }
                else -> {
                    holder.b.icon.text = "↙"
                    holder.b.icon.setTextColor(ContextCompat.getColor(ctx, R.color.text_main))
                }
            }
            holder.b.root.setOnClickListener { onClick(e) }
        }
    }
}
