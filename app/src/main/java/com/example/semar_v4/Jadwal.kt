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

        adapter = JadwalAdapter(
            listJadwal,
            showSwitch = false, // switch tidak ditampilkan di Activity ini
            onDeleteClick = { jadwal, isChecked ->
                val position = listJadwal.indexOf(jadwal)
                if (position != -1) {
                    listJadwal.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    saveJadwal()
                }
            },
            onSwitchChange = { jadwal, isChecked ->
                jadwal.enabled = isChecked
                saveJadwal()
            }
        )




        recyclerJadwal.layoutManager = LinearLayoutManager(this)
        recyclerJadwal.adapter = adapter

        // load jadwal tersimpan
        loadJadwal()
        adapter.notifyDataSetChanged()

        btnTambah.setOnClickListener { showBottomSheetTambah() }
        btnBack.setOnClickListener { startActivity(Intent(this, BerandaActivity::class.java)) }
    }

    private fun showBottomSheetTambah() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.tambah_jadwal, null)
        bottomSheet.setContentView(view)

        // Spinner pilih relay/device
        val spinnerRelay = view.findViewById<Spinner>(R.id.spinnerDevice)
        val relayOptions = arrayOf("Relay 1") // bisa nanti ambil dari SharedPreferences/Database
        spinnerRelay.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relayOptions)

        // TimePicker mulai & mati
        val timePickerMulai = view.findViewById<TimePicker>(R.id.timePickerMulai)
        val timePickerMati = view.findViewById<TimePicker>(R.id.timePickerMati)
        timePickerMulai.setIs24HourView(true)
        timePickerMati.setIs24HourView(true)

        // Checkbox hari
        val checkBoxes = listOf(
            view.findViewById<CheckBox>(R.id.cbSen) to Calendar.MONDAY,
            view.findViewById<CheckBox>(R.id.cbSel) to Calendar.TUESDAY,
            view.findViewById<CheckBox>(R.id.cbRab) to Calendar.WEDNESDAY,
            view.findViewById<CheckBox>(R.id.cbKam) to Calendar.THURSDAY,
            view.findViewById<CheckBox>(R.id.cbJum) to Calendar.FRIDAY,
            view.findViewById<CheckBox>(R.id.cbSab) to Calendar.SATURDAY,
            view.findViewById<CheckBox>(R.id.cbMin) to Calendar.SUNDAY
        )

        val btnSimpan = view.findViewById<Button>(R.id.btnSimpan)
        btnSimpan.setOnClickListener {
            val relay = spinnerRelay.selectedItem.toString()

            // Ambil jam & menit dari kedua TimePicker
            val (hourMulai, minuteMulai, hourMati, minuteMati) =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    arrayOf(
                        timePickerMulai.hour, timePickerMulai.minute,
                        timePickerMati.hour, timePickerMati.minute
                    )
                } else {
                    arrayOf(
                        timePickerMulai.currentHour, timePickerMulai.currentMinute,
                        timePickerMati.currentHour, timePickerMati.currentMinute
                    )
                }

            // ✅ BENAR (kumpulin semua hari yang dipilih ke dalam list)
            val selectedDays = mutableListOf<Int>()
            checkBoxes.forEach { (cb, dayOfWeek) ->
                if (cb.isChecked) {
                    selectedDays.add(dayOfWeek)
                }
            }

            if (selectedDays.isNotEmpty()) {
                listJadwal.add(
                    JadwalModel(
                        relay = relay,
                        days = selectedDays,   // pakai list
                        startHour = hourMulai,
                        startMinute = minuteMulai,
                        endHour = hourMati,
                        endMinute = minuteMati,
                        enabled = true
                    )
                )
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
