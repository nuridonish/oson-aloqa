package com.osonaloqa.app.ui.auth

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.osonaloqa.app.Country
import com.osonaloqa.app.CountryPicker
import com.osonaloqa.app.Countries
import com.osonaloqa.app.HomeActivity
import com.osonaloqa.app.Pref
import com.osonaloqa.app.R
import com.osonaloqa.app.data.model.OperatorDetector
import com.osonaloqa.app.databinding.ActivityPhoneBinding

class PhoneActivity : AppCompatActivity() {

    private lateinit var b: ActivityPhoneBinding
    private var country: Country = Countries.all.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPhoneBinding.inflate(layoutInflater)
        setContentView(b.root)
        update()
        b.btnCountry.setOnClickListener {
            CountryPicker(this) { c -> country = c; update() }.show()
        }
        b.phone.addTextChangedListener(simple { update() })
        b.btnNext.setOnClickListener { next() }
        b.btnOldReg.setOnClickListener {
            startActivity(Intent(this, com.osonaloqa.app.RegisterActivity::class.java))
            finish()
        }
    }

    private fun update() {
        b.btnCountry.text = country.display()
        val e164 = Countries.normalize(b.phone.text.toString(), country.code)
        b.preview.text = e164 ?: getString(R.string.phone_hint_preview)
        if (e164 != null) {
            val op = OperatorDetector.detect(e164)
            b.operator.text = getString(R.string.operator_label) + ": " + op.name + " (" + op.country + ")"
        } else {
            b.operator.text = ""
        }
    }

    private fun next() {
        val e164 = Countries.normalize(b.phone.text.toString(), country.code)
        if (e164 == null) {
            b.phone.error = getString(R.string.invalid_phone)
            return
        }
        startActivity(Intent(this, OtpActivity::class.java).putExtra("e164", e164))
    }

    private fun simple(onChange: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = onChange()
    }
}
