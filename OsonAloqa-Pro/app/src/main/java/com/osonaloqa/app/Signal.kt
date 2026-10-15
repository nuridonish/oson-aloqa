package com.osonaloqa.app

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object Signal {

    @Volatile
    private var client: MqttClient? = null

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(JSONObject) -> Unit>>()

    val connected: Boolean
        get() = client?.isConnected == true

    @Synchronized
    fun ensure(onReady: (Boolean) -> Unit) {
        if (connected) {
            onReady(true)
            return
        }
        Thread {
            var ok = false
            try {
                val c = MqttClient("tcp://broker.emqx.io:1883", "osonaloqa" + System.currentTimeMillis(), MemoryPersistence())
                c.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {}
                    override fun messageArrived(topic: String, message: MqttMessage) {
                        try {
                            val json = JSONObject(String(message.payload))
                            listeners[topic]?.forEach { it(json) }
                        } catch (e: Exception) {
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                c.connect()
                client = c
                ok = true
            } catch (e: Exception) {
            }
            onReady(ok)
        }.start()
    }

    fun listen(topic: String, cb: (JSONObject) -> Unit) {
        listeners.getOrPut(topic) { CopyOnWriteArrayList() }.add(cb)
        try {
            client?.subscribe(topic, 1)
        } catch (e: Exception) {
        }
    }

    fun send(topic: String, obj: JSONObject) {
        try {
            client?.publish(topic, MqttMessage(obj.toString().toByteArray()))
        } catch (e: Exception) {
        }
    }

    fun topicOf(e164: String): String = "osonaloqa/v1/call/" + e164.filter { it.isDigit() }
}
