package com.osonaloqa.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val address = msgs[0].originatingAddress ?: return
        val body = StringBuilder()
        for (m in msgs) body.append(m.messageBody ?: "")
        if (body.isBlank()) return
        val blocked = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.osonaloqa.app.data.db.AppDatabase.get(context).blockedDao().isBlocked(address)
            }
        }
        if (blocked) return
        val ts = msgs[0].timestampMillis
        MessageStore.insert(address, body.toString(), ts, false, 1)
        context.sendBroadcast(Intent(ChatActivity.ACTION_NEW_MSG).setPackage(context.packageName))
        notifyIfNeeded(context, address, body.toString())
    }

    private fun notifyIfNeeded(ctx: Context, address: String, body: String) {
        if (ChatActivity.currentAddress == address) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.channel_msg), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pFlags = if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        val pi = android.app.PendingIntent.getActivity(
            ctx, address.hashCode(),
            ChatActivity.intent(ctx, address),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or pFlags
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_msg_small)
            .setContentTitle(address)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(address.hashCode(), notification)
    }

    companion object {
        const val CHANNEL = "osonaloqa_msg"
    }
}

