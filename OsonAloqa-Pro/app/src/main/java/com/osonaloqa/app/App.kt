package com.osonaloqa.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        MessageStore.init(this)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra("id", -1L)
                if (id >= 0) {
                    MessageStore.setStatus(id, if (resultCode == android.app.Activity.RESULT_OK) 2 else 3)
                    context.sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(packageName))
                }
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(SmsGateway.ACTION_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
