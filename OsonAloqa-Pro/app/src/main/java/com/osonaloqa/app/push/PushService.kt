package com.osonaloqa.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.content.Intent
import com.osonaloqa.app.ChatActivity

class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        getSharedPreferences("osonaloqa", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val from = message.data["from"] ?: return
        val body = message.data["body"] ?: return
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel("osonaloqa_msg", "Осон Алоқа", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = android.app.PendingIntent.getActivity(
            this, from.hashCode(),
            Intent(this, ChatActivity::class.java).putExtra("address", from),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = androidx.core.app.NotificationCompat.Builder(this, "osonaloqa_msg")
            .setSmallIcon(com.osonaloqa.app.R.drawable.ic_msg_small)
            .setContentTitle(from)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(from.hashCode(), n)
    }
}
