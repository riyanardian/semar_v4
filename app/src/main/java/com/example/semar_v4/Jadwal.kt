package com.example.semar_v4

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class Jadwal : AppCompatActivity() {

    private lateinit var recyclerJadwal: RecyclerView
    private lateinit var btnTambah: Button
    private lateinit var adapter: JadwalAdapter
    private val listJadwal = mutableListOf<String>() // contoh data sementara

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_jadwal)

        // handle padding untuk status bar & navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerJadwal = findViewById(R.id.recyclerJadwal)
        btnTambah = findViewById(R.id.btnTambahJadwal)

        // setup recycler
        adapter = JadwalAdapter(listJadwal)
        recyclerJadwal.layoutManager = LinearLayoutManager(this)
        recyclerJadwal.adapter = adapter

        // klik tombol tambah
        btnTambah.setOnClickListener {
            showBottomSheetTambah()
        }
    }

    private fun showBottomSheetTambah() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.tambah_jadwal, null)
        bottomSheet.setContentView(view)

        val btnSimpan = view.findViewById<Button>(R.id.btnSimpan)

        btnSimpan.setOnClickListener {
            // contoh simpan jadwal (dummy)
            listJadwal.add("Relay 1 - Senin 07:00 ON")
            adapter.notifyDataSetChanged()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }
}
