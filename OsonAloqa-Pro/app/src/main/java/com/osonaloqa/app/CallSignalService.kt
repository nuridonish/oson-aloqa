package com.osonaloqa.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.app.Service
import androidx.core.app.NotificationCompat

class CallSignalService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundOnline()
        val me = Pref.e164(this)
        if (me.isNotEmpty()) {
            Signal.ensure { ok ->
                if (ok) {
                    Signal.listen(Signal.topicOf(me)) { json ->
                        val type = json.optString("type")
                        if (type == "offer") {
                            if (InternetCallActivity.instance == null) {
                                val i = Intent(this, InternetCallActivity::class.java)
                                i.putExtra(InternetCallActivity.EXTRA_JSON, json.toString())
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(i)
                            }
                        } else {
                            InternetCallActivity.instance?.onExternalSignal(json)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundOnline() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("online", getString(R.string.channel_online), NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, "online")
            .setSmallIcon(R.drawable.ic_msg_small)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.online_now))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1001, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1001, n)
        }
    }
}

