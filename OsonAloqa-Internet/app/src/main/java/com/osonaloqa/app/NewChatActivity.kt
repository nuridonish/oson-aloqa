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
