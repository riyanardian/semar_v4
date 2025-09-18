package com.example.semar_v4

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TimePicker
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*

class Jadwal : AppCompatActivity() {

    private lateinit var recyclerJadwal: RecyclerView
    private lateinit var btnBack: ImageView
    private lateinit var btnTambah: Button
    private lateinit var adapter: JadwalAdapter
    private val listJadwal = mutableListOf<JadwalModel>()


    private val handler = Handler(Looper.getMainLooper())

    // SharedPreferences
    private val sharedPref by lazy {
        getSharedPreferences("jadwal_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_jadwal)

        // handle padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerJadwal = findViewById(R.id.recyclerJadwal)
        btnTambah = findViewById(R.id.btnTambahJadwal)
        btnBack = findViewById(R.id.btnBack)

        adapter = JadwalAdapter(listJadwal) { position ->
            // hapus item dari list
            listJadwal.removeAt(position)
            adapter.notifyItemRemoved(position)
            saveJadwal() // simpan setelah hapus
        }
        recyclerJadwal.layoutManager = LinearLayoutManager(this)
        recyclerJadwal.adapter = adapter

        // load jadwal tersimpan
        loadJadwal()
        adapter.notifyDataSetChanged()

        btnTambah.setOnClickListener { showBottomSheetTambah() }
        // ke Jadwal
        btnBack.setOnClickListener { startActivity(Intent(this, BerandaActivity::class.java)) }
    }

    private fun showBottomSheetTambah() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.tambah_jadwal, null)
        bottomSheet.setContentView(view)

        // Spinner relay
        val spinnerRelay = view.findViewById<Spinner>(R.id.spinnerRelay)
        val relayOptions = arrayOf("Relay 1", "Relay 2", "Relay 3")
        spinnerRelay.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relayOptions)

        // RadioGroup ON/OFF
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupAction)

        // TimePicker
        val timePicker = view.findViewById<TimePicker>(R.id.timePicker)
        timePicker.setIs24HourView(true) // format 24 jam

        // Checkbox hari
        val checkBoxes = listOf(
            view.findViewById<CheckBox>(R.id.cbSenin) to Calendar.MONDAY,
            view.findViewById<CheckBox>(R.id.cbSelasa) to Calendar.TUESDAY,
            view.findViewById<CheckBox>(R.id.cbRabu) to Calendar.WEDNESDAY,
            view.findViewById<CheckBox>(R.id.cbKamis) to Calendar.THURSDAY,
            view.findViewById<CheckBox>(R.id.cbJumat) to Calendar.FRIDAY,
            view.findViewById<CheckBox>(R.id.cbSabtu) to Calendar.SATURDAY,
            view.findViewById<CheckBox>(R.id.cbMinggu) to Calendar.SUNDAY
        )

        val btnSimpan = view.findViewById<Button>(R.id.btnSimpan)
        btnSimpan.setOnClickListener {
            val relay = spinnerRelay.selectedItem.toString()
            val status = when (radioGroup.checkedRadioButtonId) {
                R.id.radioOn -> "ON"
                else -> "OFF"
            }

            // Ambil jam & menit dari TimePicker
            val hour: Int
            val minute: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = timePicker.hour
                minute = timePicker.minute
            } else {
                hour = timePicker.currentHour
                minute = timePicker.currentMinute
            }

            // Simpan untuk tiap hari yang dicentang
            checkBoxes.forEach { (cb, dayOfWeek) ->
                if (cb.isChecked) {
                    listJadwal.add(JadwalModel(relay, dayOfWeek, hour, minute, status))
                }
            }

            adapter.notifyDataSetChanged()
            saveJadwal() // simpan setelah menambah jadwal
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }


    // ----------- penyimpanan -----------
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
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
