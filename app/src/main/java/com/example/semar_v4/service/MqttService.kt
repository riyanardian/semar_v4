package com.example.semar_v4.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttService : Service() {

    private val binder = LocalBinder()
    private var mqttClient: MqttClient? = null
    private val brokerUrl = "tcp://103.197.190.79:1885"
    private var isConnected = false

    // Map untuk menyimpan last payload tiap topik
    private val lastPayloadMap = mutableMapOf<String, String>()

    inner class LocalBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d("MqttService", "Service created")
        initMqtt()
    }

    private var mqttCallback: ((topic: String, message: String) -> Unit)? = null

    // Fungsi untuk set callback dari activity/fragment
    fun setCallback(callback: (topic: String, message: String) -> Unit) {
        mqttCallback = callback
    }

    // Contoh saat menerima pesan MQTT
    private fun handleIncomingMessage(topic: String, payload: String) {
        // panggil callback kalau ada
        mqttCallback?.invoke(topic, payload)
    }


    private fun initMqtt() {
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = false // supaya retained message bisa tersimpan
                    userName = "Rumot2025"      // 👉 Username MQTT
                    password = "RumahOtomatis25".toCharArray() // 👉 Password MQTT
                }
                mqttClient?.connect(options)
                isConnected = true
                Log.d("MqttService", "Connected to MQTT broker")
            } catch (e: Exception) {
                isConnected = false
                Log.e("MqttService", "Failed to connect: ${e.message}")
            }
        }

        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                Log.e("MqttService", "Connection lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message.toString()
                if (topic != null) {
                    // Simpan payload terakhir
                    lastPayloadMap[topic] = payload

                    // Broadcast ke fragment
                    val intent = Intent("MQTT_MESSAGE")
                    intent.putExtra("topic", topic)
                    intent.putExtra("payload", payload)
                    sendBroadcast(intent)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
    }

    fun publishMessage(topic: String, payload: String) {
        if (isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val message = MqttMessage(payload.toByteArray()).apply {
                        qos = 1
                        isRetained = true // supaya status tersimpan di broker
                    }
                    mqttClient?.publish(topic, message)
                    // Update last payload
                    lastPayloadMap[topic] = payload
                } catch (e: Exception) {
                    Log.e("MqttService", "Publish error: ${e.message}")
                }
            }
        }
    }

    fun subscribeTopic(topic: String) {
        CoroutineScope(Dispatchers.IO).launch {
            while (!isConnected) {
                kotlinx.coroutines.delay(100)
            }
            try {
                mqttClient?.subscribe(topic, 1)
                Log.d("MqttService", "Subscribed to $topic")
            } catch (e: Exception) {
                Log.e("MqttService", "Subscribe error: ${e.message}")
            }
        }
    }

    fun publish(topic: String, message: String) {
        mqttClient?.let {
            if (it.isConnected) {
                it.publish(topic, message.toByteArray(), 0, false)
            }
        }
    }


    // Fungsi untuk fragment panggil last payload
    fun getLastPayload(topic: String): String {
        return lastPayloadMap[topic] ?: "OFF"
    }

    override fun onDestroy() {
        super.onDestroy()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mqttClient?.disconnect()
                mqttClient?.close()
                Log.d("MqttService", "MQTT disconnected")
            } catch (e: Exception) {
                Log.e("MqttService", "Disconnect error: ${e.message}")
            }
        }
    }
}
