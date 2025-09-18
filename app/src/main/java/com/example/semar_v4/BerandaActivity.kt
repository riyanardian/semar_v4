package com.example.semar_v4

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BerandaActivity : AppCompatActivity() {

    private lateinit var btnManual: Button
    private lateinit var btnOtomatis: Button
    private lateinit var layoutDefault: LinearLayout
    private lateinit var device: ImageView
    private lateinit var jadwal: ImageView
    private lateinit var histori: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvGreeting: TextView

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
        tvGreeting = findViewById(R.id.tvGreeting)

        // padding sesuai sistem bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setGreeting()

        // SharedPreferences
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val deviceCount = sharedPref.getInt("deviceCount", 0)

        // load device dari SharedPreferences
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

            adapter = DeviceAdapter(devices,
                onClick = { device -> showDeviceDetail(device) },
                onSwitchChanged = { _, _ -> /* Kosong */ }
            )
            recyclerView.adapter = adapter
        } else {
            layoutDefault.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        // pilih device pertama sebagai default jika belum ada yang dipilih
        val deviceDipilih = getSelectedDevice() ?: devices.firstOrNull()?.also { device ->
            sharedPref.edit().apply {
                putString("selected_name", device.name)
                putString("selected_type", device.type)
                putString("selected_chip", device.chipId)
                apply()
            }
        }

        // setup tombol Manual & Otomatis berdasarkan device terpilih
        setupControlButtons()

        // MQTT
        val brokerUrl = "tcp://test.mosquitto.org:1883"
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
        Thread {
            try {
                mqttClient.connect()
                devices.forEach { device ->
                    val topicStatus = "device/${device.chipId}/status"
                    mqttClient.subscribe(topicStatus) { _, message ->
                        val isOn = message.toString() == "ON"
                        runOnUiThread { adapter.updateDeviceStatus(device.chipId, isOn) }
                    }

                    val topicRunhour = "device/${device.chipId}/runhour2"
                    mqttClient.subscribe(topicRunhour) { _, message ->
                        val runhour = "${message} jam"
                        runOnUiThread { adapter.updateDeviceRunhour(device.chipId, runhour) }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()

        // tambah device
        device.setOnClickListener { startActivity(Intent(this, Device::class.java)) }

        // ke ProfileActivity
        findViewById<ImageView>(R.id.btnAccount).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        // ke Jadwal
        jadwal.setOnClickListener { startActivity(Intent(this, Jadwal::class.java)) }
        // ke Histori
        histori.setOnClickListener { startActivity(Intent(this, Histori::class.java)) }
    }

    private fun replaceFragment(fragment: Fragment) {
        layoutDefault.visibility = View.GONE
        recyclerView.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bodyContainer, fragment)
            .commit()
    }

    private fun setupControlButtons() {
        val deviceDipilih = getSelectedDevice()

        if (deviceDipilih == null) {
            // Belum ada device terpilih → tombol nonaktif
            btnManual.isEnabled = false
            btnOtomatis.isEnabled = false
            btnManual.backgroundTintList = getColorStateList(R.color.gray)
            btnOtomatis.backgroundTintList = getColorStateList(R.color.gray)
        } else {
            // Device terpilih → tombol Manual aktif, Otomatis nonaktif
            btnManual.isEnabled = true
            btnOtomatis.isEnabled = true
            btnManual.backgroundTintList = getColorStateList(R.color.purple_700)
            btnOtomatis.backgroundTintList = getColorStateList(R.color.gray)

            // Klik Manual
            btnManual.setOnClickListener {
                val fragment = ManualFragment().apply {
                    arguments = Bundle().apply {
                        putString("deviceName", deviceDipilih.name)
                        putString("deviceType", deviceDipilih.type)
                        putString("chipId", deviceDipilih.chipId)
                    }
                }
                replaceFragment(fragment)
                btnManual.backgroundTintList = getColorStateList(R.color.purple_700)
                btnOtomatis.backgroundTintList = getColorStateList(R.color.gray)
            }

            // Klik Otomatis
            btnOtomatis.setOnClickListener {
                val fragment = OtomatisFragment().apply {
                    arguments = Bundle().apply {
                        putString("deviceName", deviceDipilih.name)
                        putString("deviceType", deviceDipilih.type)
                        putString("chipId", deviceDipilih.chipId)
                    }
                }
                replaceFragment(fragment)
                btnOtomatis.backgroundTintList = getColorStateList(R.color.purple_700)
                btnManual.backgroundTintList = getColorStateList(R.color.gray)
            }
        }
    }

    private fun getSelectedDevice(): DeviceModel? {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val name = sharedPref.getString("selected_name", null)
        val type = sharedPref.getString("selected_type", null)
        val chip = sharedPref.getString("selected_chip", null)
        return if (!name.isNullOrEmpty() && !type.isNullOrEmpty() && !chip.isNullOrEmpty()) {
            DeviceModel(name, type, chip)
        } else null
    }

    private fun showDeviceDetail(device: DeviceModel) {
        AlertDialog.Builder(this)
            .setTitle(device.name)
            .setMessage("📌 Spesifikasi:\nTipe: ${device.type}\nChip ID: ${device.chipId}\n\nKlik OK untuk masuk kontrol.")
            .setPositiveButton("OK") { _, _ ->
                val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("selected_name", device.name)
                    putString("selected_type", device.type)
                    putString("selected_chip", device.chipId)
                    apply()
                }
                val fragment = ManualFragment().apply {
                    arguments = Bundle().apply {
                        putString("deviceName", device.name)
                        putString("deviceType", device.type)
                        putString("chipId", device.chipId)
                    }
                }
                replaceFragment(fragment)
                setupControlButtons()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun deleteDeviceAt(position: Int) {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val count = sharedPref.getInt("deviceCount", 0)
        val newList = mutableListOf<Triple<String, String, String>>()
        for (i in 1..count) {
            val name = sharedPref.getString("device_${i}_name", "") ?: ""
            val type = sharedPref.getString("device_${i}_type", "") ?: ""
            val chip = sharedPref.getString("device_${i}_chip", "") ?: ""
            if (i - 1 != position) newList.add(Triple(name, type, chip))
        }
        editor.clear()
        newList.forEachIndexed { index, triple ->
            val (name, type, chip) = triple
            val newIndex = index + 1
            editor.putString("device_${newIndex}_name", name)
            editor.putString("device_${newIndex}_type", type)
            editor.putString("device_${newIndex}_chip", chip)
        }
        editor.putInt("deviceCount", newList.size)
        editor.apply()

        devices.removeAt(position)
        adapter.notifyItemRemoved(position)

        // jika device yang dipilih dihapus, pilih device pertama yang tersisa
        if (getSelectedDevice() == null && devices.isNotEmpty()) {
            val first = devices.first()
            sharedPref.edit().apply {
                putString("selected_name", first.name)
                putString("selected_type", first.type)
                putString("selected_chip", first.chipId)
                apply()
            }
            setupControlButtons()
        }
    }

    private fun setGreeting() {
        val jam = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toInt()
        val greeting = when (jam) {
            in 0..5 -> "Good Night..."
            in 6..11 -> "Good Morning..."
            in 12..15 -> "Good Afternoon..."
            in 16..18 -> "Good Evening..."
            else -> "Good Night..."
        }
        tvGreeting.text = greeting
    }
}
