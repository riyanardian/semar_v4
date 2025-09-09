package com.example.semar_v4

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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

class BerandaActivity : AppCompatActivity() {

    private lateinit var btnManual: Button
    private lateinit var btnOtomatis: Button
    private lateinit var layoutDefault: LinearLayout
    private lateinit var device: ImageView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_beranda)

        btnManual = findViewById(R.id.btnManual)
        btnOtomatis = findViewById(R.id.btnOtomatis)
        layoutDefault = findViewById(R.id.layoutDefault)
        device = findViewById(R.id.btnTambahDevice)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 🔹 ambil data dari SharedPreferences
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val deviceCount = sharedPref.getInt("deviceCount", 0)

        if (deviceCount > 0) {
            // ada device → tampilkan recycler
            layoutDefault.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            val devices = mutableListOf<DeviceModel>()
            for (i in 1..deviceCount) {
                val name = sharedPref.getString("device_${i}_name", null)
                val type = sharedPref.getString("device_${i}_type", null)

                if (name != null && type != null) {
                    devices.add(DeviceModel(name, type))
                }
            }

            recyclerView.adapter = DeviceAdapter(devices) { device ->
                showDeviceDetail(device)
            }
        } else {
            // belum ada device → tampilkan folder kosong
            layoutDefault.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        // klik tombol manual
        btnManual.setOnClickListener {
            replaceFragment(ManualFragment())
        }

        // klik tombol otomatis
        btnOtomatis.setOnClickListener {
            replaceFragment(OtomatisFragment())
        }

        // tambah device
        device.setOnClickListener {
            val intent = Intent(this, Device::class.java)
            startActivity(intent)
        }

        // tambah device
        device.setOnClickListener {
            val intent = Intent(this, Jadwal::class.java)
            startActivity(intent)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        layoutDefault.visibility = View.GONE   // sembunyikan folder kosong
        recyclerView.visibility = View.GONE   // sembunyikan daftar device
        supportFragmentManager.beginTransaction()
            .replace(R.id.bodyContainer, fragment)
            .commit()
    }

    private fun showDeviceDetail(device: DeviceModel) {
        AlertDialog.Builder(this)
            .setTitle(device.name)
            .setMessage("Spesifikasi: ${device.type}\n\nKlik OK untuk masuk kontrol.")
            .setPositiveButton("OK") { _, _ ->
                val fragment = ManualFragment().apply {
                    arguments = Bundle().apply {
                        putString("deviceName", device.name)
                        putString("deviceType", device.type)
                    }
                }
                replaceFragment(fragment)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
