package com.osonaloqa.app

import android.content.Intent
import android.os.IBinder
import android.app.Service

class CallSignalService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
}
