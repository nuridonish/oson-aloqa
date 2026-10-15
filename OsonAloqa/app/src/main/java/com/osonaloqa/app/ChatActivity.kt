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

    private val smsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
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
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = address
        supportActionBar?.subtitle = getString(R.string.sms_subtitle)
        b.toolbar.setNavigationOnClickListener { finish() }
        adapter = MsgAdapter()
        b.list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.list.adapter = adapter
        b.btnSend.setOnClickListener { send() }
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
    }

    override fun onPause() {
        super.onPause()
        currentAddress = null
        unregisterReceiver(updateReceiver)
    }

    private fun refresh() {
        val msgs = MessageStore.messages(address)
        adapter.submit(msgs)
        if (msgs.isNotEmpty()) b.list.scrollToPosition(msgs.size - 1)
    }

    private fun send() {
        val text = b.input.text.toString().trim()
        if (text.isEmpty()) return
        if (!SmsGateway.canSend(this)) {
            smsPermLauncher.launch(Manifest.permission.SEND_SMS)
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
        else -> super.onOptionsItemSelected(item)
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
                .setMessage(R.string.video_need_server)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun call() {
        val canCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:" + address)))
    }

    class MsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Msg> = emptyList()
        private val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

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
                is OutVH -> {
                    holder.b.bubble.text = m.body
                    holder.b.time.text = time + statusMark(m.status)
                }
                is InVH -> {
                    holder.b.bubble.text = m.body
                    holder.b.time.text = time
                }
            }
        }

        private fun statusMark(status: Int): String = when (status) {
            0 -> " ⏳"
            2 -> " ✓✓"
            3 -> " ✗"
            else -> " ✓"
        }

        class OutVH(val b: ItemMsgOutBinding) : RecyclerView.ViewHolder(b.root)
        class InVH(val b: ItemMsgInBinding) : RecyclerView.ViewHolder(b.root)
    }

    companion object {
        const val ACTION_NEW_MSG = "com.osonaloqa.app.NEW_MSG"
        const val ACTION_MSG_UPDATED = "com.osonaloqa.app.MSG_UPDATED"
        var currentAddress: String? = null

        fun intent(context: Context, address: String): Intent =
            Intent(context, ChatActivity::class.java).putExtra("address", address)
    }
}
