package com.example.semar_v4

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.semar_v4.header.ManualFragment
import com.example.semar_v4.header.OtomatisFragment
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.MqttMessage


class BerandaActivity : AppCompatActivity() {

    private lateinit var btnManual: Button
    private lateinit var btnOtomatis: Button
    private lateinit var layoutDefault: LinearLayout
    private lateinit var device: ImageView
    private lateinit var jadwal: ImageView
    private lateinit var histori: ImageView
    private lateinit var recyclerView: RecyclerView

    private val devices = mutableListOf<DeviceModel>()
    private lateinit var adapter: DeviceAdapter
    private lateinit var mqttClient: MqttClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_beranda)

        // inisialisasi view
        btnManual = findViewById(R.id.btnManual)
        btnOtomatis = findViewById(R.id.btnOtomatis)
        layoutDefault = findViewById(R.id.layoutDefault)
        device = findViewById(R.id.btnTambahDevice)
        jadwal = findViewById(R.id.btnJadwal)
        histori = findViewById(R.id.histori)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // padding sesuai sistem bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // SharedPreferences
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val deviceCount = sharedPref.getInt("deviceCount", 0)

        // MQTT
        val brokerUrl = "tcp://test.mosquitto.org:1883"
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

        Thread {
            try {
                mqttClient.connect()

                // subscribe status tiap device setelah connect
                for (device in devices) {
                    val topicStatus = "device/${device.chipId}/status"
                    mqttClient.subscribe(topicStatus) { _, message ->
                        val isOn = message.toString() == "ON"
                        runOnUiThread {
                            adapter.updateDeviceStatus(device.chipId, isOn)
                        }
                    }
                }

                // subscribe runhour relay 2
                for (device in devices) {
                    val topicRunhour = "device/${device.chipId}/runhour2"
                    mqttClient.subscribe(topicRunhour) { _, message ->
                        val runhour = message.toString() + " jam"
                        runOnUiThread {
                            adapter.updateDeviceRunhour(device.chipId, runhour)
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        // tampilkan device atau folder kosong
        if (deviceCount > 0) {
            layoutDefault.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            for (i in 1..deviceCount) {
                val name = sharedPref.getString("device_${i}_name", null)
                val type = sharedPref.getString("device_${i}_type", null)
                val chip = sharedPref.getString("device_${i}_chip", null)

                if (!name.isNullOrEmpty() && !type.isNullOrEmpty() && !chip.isNullOrEmpty()) {
                    devices.add(DeviceModel(name, type, chip))
                }
            }

            // inisialisasi adapter
            adapter = DeviceAdapter(devices,
                onClick = { device -> showDeviceDetail(device) },
                onSwitchChanged = { device, isChecked ->
                    // Kosongkan, tidak publish apa-apa
                    // Hanya untuk deteksi klik switch jika perlu
                }
            )
            recyclerView.adapter = adapter

// --- subscribe status tiap device untuk update switch otomatis ---
            if (mqttClient.isConnected) {
                for (device in devices) {
                    val topicStatus = "device/${device.chipId}/status"
                    mqttClient.subscribe(topicStatus) { _, message ->
                        val isOn = message.toString() == "ON"
                        runOnUiThread {
                            adapter.updateDeviceStatus(device.chipId, isOn)
                        }
                    }
                }
            }

            recyclerView.adapter = adapter
        } else {
            layoutDefault.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        // tombol manual & otomatis
        btnManual.setOnClickListener { replaceFragment(ManualFragment()) }
        btnOtomatis.setOnClickListener { replaceFragment(OtomatisFragment()) }

        // tambah device
        device.setOnClickListener { startActivity(Intent(this, Device::class.java)) }


        // 🔹 Arahkan ke ProfileActivity saat icon account diklik
        val btnAccount = findViewById<ImageView>(R.id.btnAccount)
        btnAccount.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
        // ke jadwal
        jadwal.setOnClickListener { startActivity(Intent(this, Jadwal::class.java)) }

        // ke histori
        histori.setOnClickListener { startActivity(Intent(this, Histori::class.java)) }

    }

    private fun replaceFragment(fragment: Fragment) {
        layoutDefault.visibility = View.GONE
        recyclerView.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bodyContainer, fragment)
            .commit()
    }

    private fun showDeviceDetail(device: DeviceModel) {
        AlertDialog.Builder(this)
            .setTitle(device.name)
            .setMessage(
                "📌 Spesifikasi:\nTipe: ${device.type}\nChip ID: ${device.chipId}\n\nKlik OK untuk masuk kontrol."
            )
            .setPositiveButton("OK") { _, _ ->
                val fragment = ManualFragment().apply {
                    arguments = Bundle().apply {
                        putString("deviceName", device.name)
                        putString("deviceType", device.type)
                        putString("chipId", device.chipId)
                    }
                }
                replaceFragment(fragment)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // -------------------- HAPUS DEVICE --------------------
    // di BerandaActivity
    fun deleteDeviceAt(position: Int) {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val count = sharedPref.getInt("deviceCount", 0)

        // buat list sementara untuk semua device kecuali yang dihapus
        val newList = mutableListOf<Triple<String, String, String>>()
        for (i in 1..count) {
            val name = sharedPref.getString("device_${i}_name", "") ?: ""
            val type = sharedPref.getString("device_${i}_type", "") ?: ""
            val chip = sharedPref.getString("device_${i}_chip", "") ?: ""

            if (i - 1 != position) { // skip yang mau dihapus
                newList.add(Triple(name, type, chip))
            }
        }

        // clear SharedPreferences
        editor.clear()

        // simpan ulang device yang tersisa
        newList.forEachIndexed { index, triple ->
            val (name, type, chip) = triple
            val newIndex = index + 1
            editor.putString("device_${newIndex}_name", name)
            editor.putString("device_${newIndex}_type", type)
            editor.putString("device_${newIndex}_chip", chip)
        }
        editor.putInt("deviceCount", newList.size)
        editor.apply()

        // update RecyclerView
        devices.removeAt(position)
        adapter.notifyItemRemoved(position)
    }

}
