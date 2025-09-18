package com.example.semar_v4.header

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.semar_v4.R
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class ManualFragment : Fragment() {

    private lateinit var mqttClient: MqttClient
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
        Thread {
            try {
                mqttClient = MqttClient(brokerUri, MqttClient.generateClientId(), null)
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    setCleanSession(false)
                    connectionTimeout = 10
                    keepAliveInterval = 60
                }

                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Koneksi MQTT hilang", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val msg = message.toString()
                        requireActivity().runOnUiThread {
                            when (topic) {
                                topicRunhour -> tvRunhourRelay2.text = "• Runhour Relay 2: $msg Jam"
                                topicRelay1Status -> updateStatus(tvStatusRelay1, msg)
                                topicRelay2Status -> updateStatus(tvStatusRelay2, msg)
                            }
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient.connect(options)
                subscribeTopics()

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
            mqttClient.subscribe(topicRunhour, 1)
            mqttClient.subscribe(topicRelay1Status, 1)
            mqttClient.subscribe(topicRelay2Status, 1)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun updateStatus(tv: TextView, msg: String) {
        tv.text = if (msg == "ON") "• Status ${tv.id}: ON" else "• Status ${tv.id}: OFF"
        tv.setTextColor(
            resources.getColor(if (msg == "ON") android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    private fun publishMessage(topic: String, msg: String) {
        Thread {
            try {
                if (mqttClient.isConnected) {
                    val mqttMessage = MqttMessage(msg.toByteArray()).apply {
                        qos = 1
                        isRetained = true
                    }
                    mqttClient.publish(topic, mqttMessage)
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

    override fun onDestroyView() {
        super.onDestroyView()
        try { if (mqttClient.isConnected) mqttClient.disconnect() } catch (_: Exception) {}
    }
}
