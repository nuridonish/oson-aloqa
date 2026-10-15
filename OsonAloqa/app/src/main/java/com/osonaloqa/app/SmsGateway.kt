package com.osonaloqa.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SmsGateway {

    const val ACTION_SENT = "com.osonaloqa.app.SMS_SENT"
    private var seq = 1

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(context: Context, address: String, text: String, storeId: Long) {
        val ctx = context.applicationContext
        val sm = manager(ctx)
        if (sm == null || !canSend(ctx)) {
            MessageStore.setStatus(storeId, 3)
            ctx.sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(ctx.packageName))
            return
        }
        val code = ++seq
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val piSent = PendingIntent.getBroadcast(
            ctx, code,
            Intent(ACTION_SENT).setPackage(ctx.packageName).putExtra("id", storeId),
            PendingIntent.FLAG_UPDATE_CURRENT or flags
        )
        try {
            val parts = sm.divideMessage(text)
            if (parts.size <= 1) {
                sm.sendTextMessage(address, null, text, piSent, null)
            } else {
                sm.sendMultipartTextMessage(address, null, parts, arrayListOf(piSent), null)
            }
        } catch (e: Exception) {
            MessageStore.setStatus(storeId, 3)
            ctx.sendBroadcast(Intent(ChatActivity.ACTION_MSG_UPDATED).setPackage(ctx.packageName))
        }
    }

    private fun manager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
}
