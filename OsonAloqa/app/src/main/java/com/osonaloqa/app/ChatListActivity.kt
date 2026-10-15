package com.osonaloqa.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.osonaloqa.app.databinding.ActivityChatListBinding
import com.osonaloqa.app.databinding.ItemChatBinding

class ChatListActivity : AppCompatActivity() {

    private lateinit var b: ActivityChatListBinding
    private lateinit var adapter: ConvAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        adapter = ConvAdapter { conv ->
            startActivity(ChatActivity.intent(this, conv.address))
        }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.fab.setOnClickListener { startActivity(Intent(this, NewChatActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val convs = MessageStore.conversations()
        adapter.submit(convs)
        b.empty.visibility = if (convs.isEmpty()) View.VISIBLE else View.GONE
        val me = Pref.e164(this)
        supportActionBar?.subtitle = if (me.isNotEmpty()) me else null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_language -> { showLanguageDialog(); true }
        R.id.action_profile -> { showProfileDialog(); true }
        else -> super.onOptionsItemSelected(item)
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

    private fun showProfileDialog() {
        val me = "${Pref.name(this)}\n${Pref.e164(this)}"
        AlertDialog.Builder(this)
            .setTitle(R.string.profile)
            .setMessage(me)
            .setPositiveButton(R.string.logout) { _, _ ->
                Pref.logout(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class ConvAdapter(var onClick: (Conv) -> Unit) :
        androidx.recyclerview.widget.RecyclerView.Adapter<ConvAdapter.VH>() {

        private var items: List<Conv> = emptyList()

        fun submit(list: List<Conv>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(val b: ItemChatBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(ItemChatBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val name = c.address
            holder.b.avatar.text = name.takeLast(1)
            holder.b.title.text = name
            holder.b.snippet.text = if (c.out) "→ ${c.lastBody}" else c.lastBody
            holder.b.time.text = android.text.format.DateFormat.format("dd.MM HH:mm", c.ts)
            holder.b.root.setOnClickListener { onClick(c) }
        }
    }
}
