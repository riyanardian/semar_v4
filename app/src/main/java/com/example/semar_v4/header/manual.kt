package com.example.semar_v4.header

import android.content.*
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
import com.example.semar_v4.service.MqttService
import android.os.IBinder
import android.util.Log
import com.example.semar_v4.BerandaActivity
import org.json.JSONObject

class ManualFragment : Fragment() {

    private lateinit var tvRunhourRelay2: TextView
    private lateinit var tvStatusRelay1: TextView
    private lateinit var tvStatusRelay2: TextView
    private lateinit var ivStatusRelay1: ImageView
    private lateinit var ivStatusRelay2: ImageView
    private lateinit var tvResetRunhour: TextView
    private lateinit var tvKondisiMesin: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnRelayOn: Button
    private lateinit var btnRelayOff: Button


    private var chipId: String? = null

    private lateinit var topicRelay1Set: String
    private lateinit var topicRelay1Status: String
    private lateinit var topicRelay2Status: String
    private lateinit var topicRunhour: String

    private var mqttService: MqttService? = null
    private var isBound = false

    private val PREFS_NAME = "MyRoomPrefs"
    private var relay1On: Boolean = false
    private var sensorOn: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isBound = true

            // subscribe topik penting
            mqttService?.subscribeTopic(topicRelay1Status)
            mqttService?.subscribeTopic(topicRelay2Status)
            mqttService?.subscribeTopic(topicRunhour)

            // restore status terakhir
            restoreRelayStatus()

            btnBack.setOnClickListener {
                if (activity is BerandaActivity) {
                    (activity as BerandaActivity).showDeviceSelection()
                }
            }

        }


        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService = null
            isBound = false
        }
    }

    private val mqttReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val topic = intent?.getStringExtra("topic")
            val payload = intent?.getStringExtra("payload") ?: "OFF"

            when (topic) {
                topicRunhour -> {
                    try {
                        val obj = JSONObject(payload)
                        val hrReadable = obj.optString("hr_readable", "0 jam 0 menit 0 detik")
                        tvRunhourRelay2.text = "• Runhour Relay 2: $hrReadable"
                    } catch (e: Exception) {
                        // fallback kalau payload bukan JSON
                        tvRunhourRelay2.text = "• Runhour Relay 2: $payload"
                    }
                }
                topicRelay1Status -> {
                    relay1On = payload == "ON"   // update boolean relay
                    updateRelay1Status(payload)
                    saveRelayState("relay1_state", payload == "ON")
                    updateKondisiMesin()         // update kondisi realtime

                }
                topicRelay2Status -> {
                    sensorOn = payload == "ON"   // update boolean sensor
                    updateRelay2Status(payload)
                    saveRelayState("relay2_state", payload == "ON")
                    updateKondisiMesin()         // update kondisi realtime

                }
            }
        }
    }

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
        ivStatusRelay1 = view.findViewById(R.id.ivStatusRelay1)
        ivStatusRelay2 = view.findViewById(R.id.ivStatusRelay2)
        tvResetRunhour = view.findViewById(R.id.resetrunhour)
        tvKondisiMesin = view.findViewById(R.id.kondisimesin)


        chipId = arguments?.getString("chipId") ?: ""

        topicRelay1Set = "device/$chipId/relay1/set"
        topicRelay1Status = "device/$chipId/relay1/status"
        topicRelay2Status = "device/$chipId/relay2/status"
        topicRunhour = "device/$chipId/relay2/runhour"

        btnRelayOn.setOnClickListener { sendRelay1("ON") }
        btnRelayOff.setOnClickListener { sendRelay1("OFF") }
        btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        tvResetRunhour.setOnClickListener {
            // 1. Reset value runhour di SharedPreferences
            saveRelayState("runhour_value", "0 jam 0 menit ")

            // 2. Update UI
            tvRunhourRelay2.text = "• Runhour Relay 2: 0 jam 0 menit"

            // 3. Publish reset ke MQTT lewat MqttService
            val resetTopic = "device/$chipId/sensor/runhour/reset"
            val resetPayload = "reset"
            if (isBound) {
                mqttService?.publishMessage(resetTopic, resetPayload)
                Toast.makeText(requireContext(), "Runhour di-reset & dikirim ke MQTT", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "MQTT belum siap", Toast.LENGTH_SHORT).show()
            }

            // begitu buka manual, langsung paksa set mode manual di ESP
            if (isBound) {
                mqttService?.publishMessage("device/$chipId/mode", "MANUAL")
                Toast.makeText(requireContext(), "Mode manual diaktifkan", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("ManualFragment", "MQTT belum siap untuk set mode manual")
            }
        }


        return view
    }

    private fun updateKondisiMesin() {
        val kondisi = if ((relay1On && sensorOn) || (!relay1On && !sensorOn)) {
            "Normal"
        } else {
            "Trouble"
        }

        Log.d("KONDISI_MESIN", "Relay1=$relay1On Sensor=$sensorOn => $kondisi")
        tvKondisiMesin.text = "Kondisi Mesin: $kondisi"
    }


    private fun updateRelay1Status(status: String) {
        tvStatusRelay1.text = "Status Relay 1: $status"
        tvStatusRelay1.setTextColor(
            resources.getColor(if (status == "ON") android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
        ivStatusRelay1.setBackgroundResource(if (status == "ON") R.drawable.circle_green else R.drawable.circle_red)
        updateKondisiMesin()
    }

    private fun updateRelay2Status(status: String) {
        tvStatusRelay2.text = "Status Mesin: $status"
        tvStatusRelay2.setTextColor(
            resources.getColor(if (status == "ON") android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
        ivStatusRelay2.setBackgroundResource(if (status == "ON") R.drawable.circle_green else R.drawable.circle_red)
        updateKondisiMesin()
    }

    private fun broadcastToBeranda(key: String, value: String) {
        val chip = chipId ?: return  // pastikan chipId tidak null
        val intent = Intent("DEVICE_UPDATE") // sesuai yang diterima BerandaActivity
        intent.putExtra("chipId", chip)
        intent.putExtra("key", key)
        intent.putExtra("value", value)
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(requireContext())
            .sendBroadcast(intent)
    }



    // Modifikasi sendRelay1, sendRelay2, sendRunhour
    private fun sendRelay1(msg: String) {
        if (isBound) {
            mqttService?.publishMessage(topicRelay1Set, msg)
            Toast.makeText(requireContext(), "Publish: $msg", Toast.LENGTH_SHORT).show()
            saveRelayState("relay1_state", msg == "ON")
            // kirim update ke BerandaActivity
            broadcastToBeranda("relay1", msg)
        } else {
            Toast.makeText(requireContext(), "MQTT belum siap", Toast.LENGTH_SHORT).show()
        }
    }


    private fun sendRelay2(msg: String) {
        if (isBound) {
            mqttService?.publishMessage(topicRelay2Status, msg)
            Toast.makeText(requireContext(), "Publish: $msg", Toast.LENGTH_SHORT).show()
            saveRelayState("relay2_state", msg == "ON")
            // kirim update ke BerandaActivity
            broadcastToBeranda("relay2", msg)
        } else {
            Toast.makeText(requireContext(), "MQTT belum siap", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRunhour(value: String) {
        if (isBound) {
            mqttService?.publishMessage(topicRunhour, value)
            Toast.makeText(requireContext(), "Publish Runhour: $value", Toast.LENGTH_SHORT).show()
            saveRelayState("runhour_value", value)
            // kirim update ke BerandaActivity
            broadcastToBeranda("runhour", value)
        } else {
            Toast.makeText(requireContext(), "MQTT belum siap", Toast.LENGTH_SHORT).show()
        }
    }


    // Fungsi untuk menyimpan status di SharedPreferences
    private fun saveRelayState(key: String, value: Any) {
        val pref = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = pref.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
        }
        editor.apply()
    }

    private fun loadRelayState(key: String, default: Boolean = false): Boolean {
        val pref = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return pref.getBoolean(key, default)
    }

    private fun restoreRelayStatus() {
        val relay1 = loadRelayState("relay1_state")
        val relay2 = loadRelayState("relay2_state")

        updateRelay1Status(if (relay1) "ON" else "OFF")
        updateRelay2Status(if (relay2) "ON" else "OFF")

        updateKondisiMesin()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), MqttService::class.java)
        requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireActivity().unbindService(connection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                mqttReceiver,
                IntentFilter("MQTT_MESSAGE"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(
                mqttReceiver,
                IntentFilter("MQTT_MESSAGE")
            )
        }
    }


    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(mqttReceiver)
    }
}
