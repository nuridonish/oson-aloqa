package com.osonaloqa.app

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class OsonCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallScreenActivity.currentCall = call
        val intent = Intent(this, CallScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallScreenActivity.currentCall === call) {
            CallScreenActivity.currentCall = null
            CallScreenActivity.instance?.finish()
        }
    }
}
