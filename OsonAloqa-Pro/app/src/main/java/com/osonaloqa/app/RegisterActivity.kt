package com.osonaloqa.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.osonaloqa.app.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private var country: Country = Countries.default

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.SEND_SMS] == true || granted[Manifest.permission.RECEIVE_SMS] == true) {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        updateCountry()
        b.btnCountry.setOnClickListener {
            CountryPicker(this) { c -> country = c; updateCountry() }.show()
        }
        b.phone.addTextChangedListener(simpleWatcher { updatePreview() })
        b.btnRegister.setOnClickListener { register() }
    }

    private fun updateCountry() {
        b.btnCountry.text = country.display()
        updatePreview()
    }

    private fun updatePreview() {
        val e164 = Countries.normalize(b.phone.text.toString(), country.code)
        b.preview.text = e164 ?: getString(R.string.phone_hint_preview)
    }

    private fun register() {
        val name = b.name.text.toString().trim()
        if (name.isEmpty()) {
            b.name.error = getString(R.string.invalid_name)
            return
        }
        val e164 = Countries.normalize(b.phone.text.toString(), country.code)
        if (e164 == null) {
            b.phone.error = getString(R.string.invalid_phone)
            return
        }
        Pref.save(this, name, e164)
        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        val needed = list.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    private fun simpleWatcher(onChange: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = onChange()
    }
}
