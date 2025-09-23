package com.example.semar_v4.header

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.semar_v4.JadwalAdapter
import com.example.semar_v4.JadwalModel
import com.example.semar_v4.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.semar_v4.BerandaActivity
import com.example.semar_v4.service.MqttService
import org.json.JSONObject
import java.util.*

class OtomatisFragment : Fragment() {

    private lateinit var recyclerJadwal: RecyclerView
    private lateinit var adapter: JadwalAdapter
    private val listJadwal = mutableListOf<JadwalModel>()
    private lateinit var btnBack: ImageView
    private lateinit var switchSchedule: Switch
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvRunhourRelay2: TextView
    private lateinit var tvStatusRelay1: TextView
    private lateinit var tvStatusRelay2: TextView
    private lateinit var ivStatusRelay1: ImageView
    private lateinit var ivStatusRelay2: ImageView
    private lateinit var tvResetRunhour: TextView
    private lateinit var tvKondisiMesin: TextView


    private var chipId: String? = null
    private var lastCheckedMinute = -1
    private var lastCheckedDay = -1
    private var isRunning = false

    private var mqttService: MqttService? = null
    private var isBound = false
    private lateinit var topicRelay1Set: String
    private lateinit var topicRelay1Status: String
    private lateinit var topicRelay2Status: String
    private lateinit var topicRunhour: String
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
                        val hrReadable = obj.optString("hr_readable", "0 jam 0 menit")
                        tvRunhourRelay2.text = "• Runhour: $hrReadable"
                    } catch (e: Exception) {
                        // fallback kalau payload bukan JSON
                        tvRunhourRelay2.text = "• Runhour: $payload"
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

    private val PREFS_NAME = "MyRoomPrefs"
    private val KEY_AUTOMATIS_ACTIVE = "otomatis_active"

    private val sharedPref by lazy {
        requireContext().getSharedPreferences("jadwal_prefs", 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chipId = arguments?.getString("chipId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_otomatis, container, false)

        switchSchedule = view.findViewById(R.id.switchSchedule)
        recyclerJadwal = view.findViewById(R.id.recyclerJadwal)
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

        // begitu buka manual, langsung paksa set mode manual di ESP
        if (isBound) {
            mqttService?.publishMessage("device/$chipId/mode", "AUTO")
            Toast.makeText(requireContext(), "Mode manual diaktifkan", Toast.LENGTH_SHORT).show()
        } else {
            Log.w("ManualFragment", "MQTT belum siap untuk set mode manual")
        }

        adapter = JadwalAdapter(
            list = listJadwal,
            showSwitch = true,
            onDeleteClick = { position ->
                val jadwal = listJadwal[position]

                // Hapus dari list dan simpan
                listJadwal.removeAt(position)
                saveJadwal()
                adapter.notifyDataSetChanged()

                // Kirim MQTT dengan delete = true
                chipId?.let {
                    val payload = Gson().toJson(
                        mapOf(
                            "relay" to jadwal.relay,
                            "enabled" to jadwal.enabled,
                            "startHour" to jadwal.startHour,
                            "startMinute" to jadwal.startMinute,
                            "endHour" to jadwal.endHour,
                            "endMinute" to jadwal.endMinute,
                            "days" to jadwal.days,
                            "delete" to true
                        )
                    )
                    mqttService?.publishMessage("device/$it/jadwal", payload)
                }
            },
            onSwitchChange = { jadwal, isChecked ->
                jadwal.enabled = isChecked
                saveJadwal()

                // Kirim MQTT dengan delete = false
                chipId?.let {
                    val payload = Gson().toJson(
                        mapOf(
                            "relay" to jadwal.relay,
                            "enabled" to isChecked,
                            "startHour" to jadwal.startHour,
                            "startMinute" to jadwal.startMinute,
                            "endHour" to jadwal.endHour,
                            "endMinute" to jadwal.endMinute,
                            "days" to jadwal.days,
                            "delete" to false
                        )
                    )
                    mqttService?.publishMessage("device/$it/jadwal", payload)
                }
            }
        )


        recyclerJadwal.layoutManager = LinearLayoutManager(requireContext())
        recyclerJadwal.adapter = adapter
        recyclerJadwal.visibility = View.GONE

        restoreSwitchState()

        btnBack.setOnClickListener {
            if (activity is BerandaActivity) {
                (activity as BerandaActivity).showDeviceSelection()
            }
        }
        tvResetRunhour.setOnClickListener {
            // 1. Reset value runhour di SharedPreferences
            saveRelayState("runhour_value", "0 jam 0 menit ")

            // 2. Update UI
            tvRunhourRelay2.text = "• Runhour : 0 jam 0 menit"

            // 3. Publish reset ke MQTT lewat MqttService
            val resetTopic = "device/$chipId/sensor/runhour/reset"
            val resetPayload = "reset"
            if (isBound) {
                mqttService?.publishMessage(resetTopic, resetPayload)
                Toast.makeText(requireContext(), "Runhour di-reset & dikirim ke MQTT", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "MQTT belum siap", Toast.LENGTH_SHORT).show()
            }
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            recyclerJadwal.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                // 👉 aktifkan jadwal & load data
                loadJadwal()
                startJadwalChecker()
            } else {
                // matikan semua item jadwal
                listJadwal.forEach { it.enabled = false }
                adapter.notifyDataSetChanged()
                stopJadwalChecker()
            }

            // simpan state
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AUTOMATIS_ACTIVE, isChecked).apply()
        }





        return view
    }

    private fun restoreSwitchState() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(KEY_AUTOMATIS_ACTIVE, false)

        switchSchedule.isChecked = wasActive
        recyclerJadwal.visibility = if (wasActive) View.VISIBLE else View.GONE

        if (wasActive) {
            loadJadwal()
            startJadwalChecker()
        } else {
            stopJadwalChecker()
        }
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




    private fun handleSwitchChange(isChecked: Boolean) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (isChecked) {
            AlertDialog.Builder(requireContext())
                .setTitle("Konfirmasi Jadwal")
                .setMessage("Apakah yakin sudah mengatur jadwal?")
                .setPositiveButton("Ya") { _, _ ->
                    Toast.makeText(requireContext(), "Jadwal diaktifkan", Toast.LENGTH_SHORT).show()
                    loadJadwal()
                    recyclerJadwal.visibility = View.VISIBLE
                    startJadwalChecker()
                    chipId?.let { publishJadwalToMqtt(it) } // kirim semua jadwal ke MQTT
                    editor.putBoolean(KEY_AUTOMATIS_ACTIVE, true).apply()
                }
                .setNegativeButton("Tidak") { _, _ ->
                    switchSchedule.isChecked = false
                    editor.putBoolean(KEY_AUTOMATIS_ACTIVE, false).apply()
                }
                .show()
        } else {
            Toast.makeText(requireContext(), "Jadwal dimatikan", Toast.LENGTH_SHORT).show()
            recyclerJadwal.visibility = View.GONE
            stopJadwalChecker()
            editor.putBoolean(KEY_AUTOMATIS_ACTIVE, false).apply()
        }
    }


    private fun saveJadwal() {
        val gson = Gson()
        // clone tapi set enabled = false
        val tempList = listJadwal.map {
            it.copy(enabled = false)
        }
        val json = gson.toJson(tempList)
        sharedPref.edit().putString("list_jadwal", json).apply()
    }


    private fun loadJadwal() {
        val gson = Gson()
        val json = sharedPref.getString("list_jadwal", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<JadwalModel>>() {}.type
            val loadedList: MutableList<JadwalModel> = gson.fromJson(json, type)
            listJadwal.clear()
            // 👉 Paksa semua enabled = false
            loadedList.forEach { it.enabled = false }
            listJadwal.addAll(loadedList)
            adapter.notifyDataSetChanged()
        }
    }

    // ✅ Publish semua jadwal ke MQTT
    private fun publishJadwalToMqtt(chipId: String) {
        if (!isBound) return

        val topic = "device/$chipId/jadwal"
        val payload = Gson().toJson(listJadwal) // kirim seluruh list jadwal

        mqttService?.publishMessage(topic, payload)

        // Broadcast ke BerandaActivity
        val intent = Intent("DEVICE_UPDATE")
        intent.putExtra("chipId", chipId)
        intent.putExtra("key", "jadwal")
        intent.putExtra("value", payload)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    // Loop checker otomatis untuk relay ON/OFF
    private fun startJadwalChecker() {
        if (isRunning) return
        isRunning = true

        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                val calendar = Calendar.getInstance()
                val day = calendar.get(Calendar.DAY_OF_WEEK)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                if (day != lastCheckedDay) {
                    listJadwal.forEach { it.executedToday = false }
                    lastCheckedDay = day
                }

                if (minute != lastCheckedMinute) {
                    lastCheckedMinute = minute
                    listJadwal.forEach { jadwal ->
                        if (jadwal.enabled && switchSchedule.isChecked && jadwal.days.contains(day)) {
                            if (jadwal.startHour == hour && jadwal.startMinute == minute) {
                                chipId?.let { mqttService?.publishMessage("device/$it/relay1/set", "1") }
                            }
                            if (jadwal.endHour == hour && jadwal.endMinute == minute) {
                                chipId?.let { mqttService?.publishMessage("device/$it/relay1/set", "0") }
                            }
                        }
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateRelay1Status(payload: String) {
        val isOn = payload.equals("ON", true) || payload == "1"
        tvStatusRelay1.text = if (isOn) "Relay 1: ON" else "Relay 1: OFF"
        ivStatusRelay1.setImageResource(if (isOn) R.drawable.circle_green else R.drawable.circle_red)
        saveRelayState("relay1_state", isOn)
        updateKondisiMesin()
    }

    private fun updateRelay2Status(payload: String) {
        val isOn = payload.equals("ON", true) || payload == "1"
        tvStatusRelay2.text = if (isOn) "Status Mesin: ON" else "Status Mesin: OFF"
        ivStatusRelay2.setImageResource(if (isOn) R.drawable.circle_green else R.drawable.circle_red)
        saveRelayState("relay2_state", isOn)
        updateKondisiMesin()
    }


    // ===== Restore terakhir =====
    private fun restoreRelayStatus() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val relay1On = prefs.getBoolean("relay1_state", false)
        val relay2On = prefs.getBoolean("relay2_state", false)

        updateRelay1Status(if (relay1On) "ON" else "OFF")
        updateRelay2Status(if (relay2On) "ON" else "OFF")
    }


    // ===== Save state =====
    private fun saveRelayState(key: String, value: Any) {
        val pref = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = pref.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
        }
        editor.apply()
    }


    private fun stopJadwalChecker() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
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
}