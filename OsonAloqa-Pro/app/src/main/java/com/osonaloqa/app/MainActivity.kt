package com.osonaloqa.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        val next = if (Pref.registered(this)) HomeActivity::class.java else RegisterActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
