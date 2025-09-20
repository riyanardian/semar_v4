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
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.semar_v4.BerandaActivity
import com.example.semar_v4.service.MqttService

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
            listJadwal,
            onDeleteClick = { position ->
                listJadwal.removeAt(position)
                adapter.notifyItemRemoved(position)
                saveJadwal()
            },
            onSwitchChange = { position, enabled ->
                listJadwal[position].enabled = enabled
                saveJadwal()
                chipId?.let { publishRelay1(it, listJadwal[position]) }
            },
            showSwitch = true
        )

        recyclerJadwal.layoutManager = LinearLayoutManager(requireContext())
        recyclerJadwal.adapter = adapter
        recyclerJadwal.visibility = View.GONE

        // 🔥 Restore state otomatis terakhir
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(KEY_AUTOMATIS_ACTIVE, false)
        if (wasActive) {
            switchSchedule.isChecked = true
            loadJadwal()
            recyclerJadwal.visibility = View.VISIBLE
            startJadwalChecker()
        }

        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        btnBack.setOnClickListener {
            if (activity is BerandaActivity) {
                (activity as BerandaActivity).showDeviceSelection()
            }
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
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

        return view
    }

    private val sharedPref by lazy {
        requireContext().getSharedPreferences("jadwal_prefs", 0)
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

    private fun publishRelay1(chipId: String, jadwal: JadwalModel) {
        if (!isBound) return
        val topic = "device/$chipId/relay1/set"
        val payload = if (jadwal.enabled) "1" else "0"
        mqttService?.publishMessage(topic, payload)
        broadcastToBeranda("relay1", payload)
    }

    private fun broadcastToBeranda(key: String, value: String) {
        val chip = chipId ?: return
        val intent = Intent("DEVICE_UPDATE")
        intent.putExtra("chipId", chip)
        intent.putExtra("key", key)
        intent.putExtra("value", value)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

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
                        if (!jadwal.executedToday &&
                            jadwal.enabled &&
                            jadwal.dayOfWeek == day &&
                            jadwal.hour == hour &&
                            jadwal.minute == minute
                        ) {
                            chipId?.let { publishRelay1(it, jadwal) }
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
