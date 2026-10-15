package com.osonaloqa.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.osonaloqa.app.databinding.ActivityNewChatBinding

class NewChatActivity : AppCompatActivity() {

    private lateinit var b: ActivityNewChatBinding
    private var country: Country = Countries.default

    private val contactLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showContacts() else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNewChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        updateCountry()
        b.btnCountry.setOnClickListener {
            CountryPicker(this) { c -> country = c; updateCountry() }.show()
        }
        b.btnContacts.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                showContacts()
            } else {
                contactLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        b.btnOpen.setOnClickListener { openChat() }
        b.btnGroup.setOnClickListener { createGroupOrChannel("group") }
        b.btnChannel.setOnClickListener { createGroupOrChannel("channel") }
    }

    private fun updateCountry() {
        b.btnCountry.text = country.display()
    }

    private fun openChat() {
        val raw = b.number.text.toString().trim()
        if (raw.isEmpty()) {
            b.number.error = getString(R.string.invalid_phone)
            return
        }
        val e164 = if (raw.startsWith("+")) {
            val digits = raw.filter { it.isDigit() || it == '+' }
            if (digits.length in 8..16) digits else null
        } else {
            Countries.normalize(raw, country.code)
        }
        if (e164 == null) {
            b.number.error = getString(R.string.invalid_phone)
            return
        }
        startActivity(ChatActivity.intent(this, e164))
        finish()
    }

    private fun createGroupOrChannel(type: String) {
        val base = com.osonaloqa.app.data.api.ServerApi.baseUrl(this)
        val token = com.osonaloqa.app.data.api.ServerApi.token(this)
        if (base.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, R.string.server_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val edit = android.widget.EditText(this)
        edit.hint = getString(R.string.group_name_hint)
        AlertDialog.Builder(this)
            .setTitle(if (type == "channel") R.string.create_channel else R.string.create_group)
            .setView(edit)
            .setPositiveButton(R.string.otp_verify) { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.osonaloqa.app.data.api.ServerApi.createGroupBlocking(base, token, name, type)
                    }
                    if (resp != null && resp.optBoolean("ok")) {
                        Toast.makeText(this@NewChatActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@NewChatActivity, R.string.media_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showContacts() {
        val names = ArrayList<String>()
        val numbers = ArrayList<String>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1)?.replace(Regex("[\\s\\-()]"), "") ?: continue
                    if (n.isEmpty()) continue
                    names.add(c.getString(0) ?: n)
                    numbers.add(if (n.startsWith("+") || n.startsWith("0")) n else "+$n")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_contacts, Toast.LENGTH_SHORT).show()
            return
        }
        if (numbers.isEmpty()) {
            Toast.makeText(this, R.string.error_contacts, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_contact)
            .setItems(names.toTypedArray()) { _, i ->
                b.number.setText(numbers[i])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}



