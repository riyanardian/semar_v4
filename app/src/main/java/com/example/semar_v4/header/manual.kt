package com.example.semar_v4.header

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.semar_v4.R
import org.eclipse.paho.client.mqttv3.*

class ManualFragment : Fragment() {

    companion object {
        private var mqttClient: MqttClient? = null
        private var isConnected = false
    }

    private lateinit var tvRunhourRelay2: TextView
    private lateinit var tvStatusRelay1: TextView
    private lateinit var tvStatusRelay2: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnRelayOn: Button
    private lateinit var btnRelayOff: Button

    private var chipId: String? = null
    private val brokerUri = "tcp://test.mosquitto.org:1883"

    private lateinit var topicRelay: String
    private lateinit var topicRelay1Status: String
    private lateinit var topicRelay2Status: String
    private lateinit var topicRunhour: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_manual, container, false)

        btnRelayOn = view.findViewById(R.id.btnRelayOn)
        btnRelayOff = view.findViewById(R.id.btnRelayOff)
        btnBack = view.findViewById(R.id.btnBack)
        tvRunhourRelay2 = view.findViewById(R.id.tvRunhourRelay2)
        tvStatusRelay1 = view.findViewById(R.id.tvStatusRelay1)
        tvStatusRelay2 = view.findViewById(R.id.tvStatusRelay2)

        chipId = arguments?.getString("chipId") ?: ""

        topicRelay = "device/$chipId/relay1/set"
        topicRelay1Status = "device/$chipId/relay1/status"
        topicRelay2Status = "device/$chipId/relay2/status"
        topicRunhour = "device/$chipId/relay2/runhour"

        initMqtt()

        btnRelayOn.setOnClickListener { publishMessage(topicRelay, "ON") }
        btnRelayOff.setOnClickListener { publishMessage(topicRelay, "OFF") }
        btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        return view
    }

    private fun initMqtt() {
        if (isConnected && mqttClient != null && mqttClient!!.isConnected) {
            subscribeTopics()
            return
        }

        Thread {
            try {
                mqttClient = MqttClient(brokerUri, MqttClient.generateClientId(), null)
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    setCleanSession(false)
                    connectionTimeout = 10
                    keepAliveInterval = 60
                }

                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Koneksi MQTT hilang", Toast.LENGTH_SHORT).show()
                        }
                        isConnected = false
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val msg = message.toString()
                        requireActivity().runOnUiThread {
                            when (topic) {
                                topicRunhour -> {
                                    tvRunhourRelay2.text = "• Runhour Relay 2: $msg Jam"
                                    broadcastToAllDevices("runhour", msg)
                                }
                                topicRelay1Status -> {
                                    updateStatus(tvStatusRelay1, msg, "Relay 1")
                                    broadcastToAllDevices("relay1", msg)
                                }
                                topicRelay2Status -> {
                                    updateStatus(tvStatusRelay2, msg, "Relay 2")
                                    broadcastToAllDevices("relay2", msg)
                                }
                            }
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient?.connect(options)
                subscribeTopics()
                isConnected = true

                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "MQTT connected!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Gagal konek MQTT: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun subscribeTopics() {
        try {
            mqttClient?.subscribe(topicRunhour, 1)
            mqttClient?.subscribe(topicRelay1Status, 1)
            mqttClient?.subscribe(topicRelay2Status, 1)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun updateStatus(tv: TextView, msg: String, label: String) {
        tv.text = "• Status $label: $msg"
        tv.setTextColor(
            resources.getColor(
                if (msg == "ON") android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            )
        )
    }

    private fun publishMessage(topic: String, msg: String) {
        Thread {
            try {
                if (mqttClient != null && mqttClient!!.isConnected) {
                    val mqttMessage = MqttMessage(msg.toByteArray()).apply {
                        qos = 1
                        isRetained = true
                    }
                    mqttClient!!.publish(topic, mqttMessage)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Publish: $msg", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "MQTT belum terhubung", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // 🔹 Broadcast ke BerandaActivity untuk semua device
    private fun broadcastToAllDevices(type: String, value: String) {
        val intent = Intent("DEVICE_UPDATE") // samakan dengan BerandaActivity
        intent.putExtra("chipId", chipId)
        intent.putExtra("type", type)
        intent.putExtra("value", value)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // jangan disconnect MQTT biar tetap hidup
    }
}
