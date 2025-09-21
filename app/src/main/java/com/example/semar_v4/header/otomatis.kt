package com.example.semar_v4.header

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
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
import java.util.*

class OtomatisFragment : Fragment() {

    private lateinit var recyclerJadwal: RecyclerView
    private lateinit var adapter: JadwalAdapter
    private val listJadwal = mutableListOf<JadwalModel>()
    private lateinit var btnBack: ImageView
    private lateinit var switchSchedule: Switch
    private val handler = Handler(Looper.getMainLooper())

    private var chipId: String? = null
    private var lastCheckedMinute = -1
    private var lastCheckedDay = -1
    private var isRunning = false

    private var mqttService: MqttService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService = null
            isBound = false
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

        adapter = JadwalAdapter(
            list = listJadwal,
            showSwitch = true,  // switch muncul di fragment otomatis
            onDeleteClick = { position ->
                val jadwal = listJadwal[position]
                listJadwal.removeAt(position)
                saveJadwal()
                adapter.notifyDataSetChanged()

                // Kirim ke MQTT untuk "hapus" jadwal
                chipId?.let { mqttService?.publishMessage("device/$it/jadwal/remove", Gson().toJson(jadwal)) }
            },
            onSwitchChange = { jadwal, isChecked ->
                jadwal.enabled = isChecked
                saveJadwal()

                // Kirim ke MQTT sesuai status switch item
                val topic = "device/$chipId/jadwal"
                val payload = Gson().toJson(
                    mapOf(
                        "relay" to jadwal.relay,
                        "enabled" to isChecked,
                        "startHour" to jadwal.startHour,
                        "startMinute" to jadwal.startMinute,
                        "endHour" to jadwal.endHour,
                        "endMinute" to jadwal.endMinute,
                        "days" to jadwal.days
                    )
                )
                mqttService?.publishMessage(topic, payload)
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

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                recyclerJadwal.visibility = View.VISIBLE
            } else {
                recyclerJadwal.visibility = View.GONE
                // Optional: matikan semua item switch tanpa kirim
                listJadwal.forEach { it.enabled = false }
                adapter.notifyDataSetChanged()
            }
        }


        return view
    }

    private fun restoreSwitchState() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(KEY_AUTOMATIS_ACTIVE, false)
        if (wasActive) {
            switchSchedule.isChecked = true
            loadJadwal()
            recyclerJadwal.visibility = View.VISIBLE
            startJadwalChecker()
        }
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
        val json = gson.toJson(listJadwal)
        sharedPref.edit().putString("list_jadwal", json).apply()
    }

    private fun loadJadwal() {
        val gson = Gson()
        val json = sharedPref.getString("list_jadwal", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<JadwalModel>>() {}.type
            val loadedList: MutableList<JadwalModel> = gson.fromJson(json, type)
            listJadwal.clear()
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
