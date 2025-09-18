package com.example.semar_v4.header

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.semar_v4.JadwalAdapter
import com.example.semar_v4.JadwalModel
import com.example.semar_v4.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*

class OtomatisFragment : Fragment() {

    private lateinit var recyclerJadwal: RecyclerView
    private lateinit var adapter: JadwalAdapter
    private val listJadwal = mutableListOf<JadwalModel>()
    private lateinit var btnBack: ImageView


    private lateinit var switchSchedule: Switch
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var mqttClient: MqttClient
    private val brokerUrl = "tcp://test.mosquitto.org:1883"

    private var lastCheckedMinute = -1
    private var isRunning = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_otomatis, container, false)

        switchSchedule = view.findViewById(R.id.switchSchedule)
        recyclerJadwal = view.findViewById(R.id.recyclerJadwal)
        btnBack = view.findViewById(R.id.btnBack)


        // setup RecyclerView
        adapter = JadwalAdapter(listJadwal) { position ->
            val removed = listJadwal.removeAt(position)
            adapter.notifyItemRemoved(position)

            val gson = Gson()
            val json = gson.toJson(listJadwal)
            sharedPref.edit().putString("list_jadwal", json).apply()

            Toast.makeText(requireContext(), "Jadwal dihapus", Toast.LENGTH_SHORT).show()
        }
        recyclerJadwal.layoutManager = LinearLayoutManager(requireContext())
        recyclerJadwal.adapter = adapter
        recyclerJadwal.visibility = View.GONE

        // Tombol Back
        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // inisialisasi MQTT
        initMqtt()

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Konfirmasi Jadwal")
                    .setMessage("Apakah yakin sudah mengatur jadwal?")
                    .setPositiveButton("Ya") { _, _ ->
                        Toast.makeText(requireContext(), "Jadwal diaktifkan", Toast.LENGTH_SHORT).show()
                        loadJadwal()
                        recyclerJadwal.visibility = View.VISIBLE
                        startJadwalChecker()
                    }
                    .setNegativeButton("Tidak") { _, _ ->
                        switchSchedule.isChecked = false
                    }
                    .show()
            } else {
                Toast.makeText(requireContext(), "Jadwal dimatikan", Toast.LENGTH_SHORT).show()
                recyclerJadwal.visibility = View.GONE
                stopJadwalChecker()
            }
        }

        return view
    }

    // -------- SharedPreferences --------
    private val sharedPref by lazy {
        requireContext().getSharedPreferences("jadwal_prefs", AppCompatActivity.MODE_PRIVATE)
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

    // -------- MQTT --------
    private fun initMqtt() {
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mqttClient.connect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // -------- Jadwal Checker --------
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

                if (minute != lastCheckedMinute) {
                    lastCheckedMinute = minute
                    listJadwal.forEach { jadwal ->
                        if (!jadwal.executedToday &&
                            jadwal.dayOfWeek == day &&
                            jadwal.hour == hour &&
                            jadwal.minute == minute
                        ) {
                            publishMqtt(jadwal.relay, jadwal.status)
                            jadwal.executedToday = true
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

    private fun publishMqtt(relay: String, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (mqttClient.isConnected) {
                    val topic = "device/$relay/status"
                    val message = MqttMessage(status.toByteArray()).apply { qos = 1 }
                    mqttClient.publish(topic, message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        stopJadwalChecker()
        super.onDestroy()
    }
}
