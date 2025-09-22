package com.example.semar_v4

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class Histori : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var spinnerBulan: Spinner
    private lateinit var spinnerRelay: Spinner
    private lateinit var adapter: HistoriAdapter
    private val listHistory = mutableListOf<HistoriModel>()
    private lateinit var btnBack: ImageView

    private val client = OkHttpClient()
    private val baseUrl = "http://103.197.190.79/api_mysql/get_histori.php"

    // 🔹 Simpan mapping agar bisa kirim ke server
    private val bulanMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_histori)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerHistory = findViewById(R.id.recyclerHistory)
        spinnerBulan = findViewById(R.id.spinnerTanggal)
        spinnerRelay = findViewById(R.id.spinnerRelay)
        btnBack = findViewById(R.id.btnBack)

        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoriAdapter(listHistory)
        recyclerHistory.adapter = adapter

        setupSpinners()
        loadBulan()
        loadHistory("ALL", "") // Hanya relay + bulan

        btnBack.setOnClickListener {
            startActivity(Intent(this, BerandaActivity::class.java))
        }

        // tombol refresh
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            loadBulan()
            val relay = spinnerRelay.selectedItem?.toString() ?: "ALL"
            val bulanDisplay = spinnerBulan.selectedItem?.toString()
            val bulan = bulanMap[bulanDisplay] ?: ""
            loadHistory(relay, bulan)
        }
    }

    private fun setupSpinners() {
        val relayOptions = arrayOf("ALL", "Relay1", "Sensor")

        // Adapter untuk spinnerRelay
        spinnerRelay.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            relayOptions
        )

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val relay = spinnerRelay.selectedItem?.toString() ?: "ALL"
                val bulanDisplay = spinnerBulan.selectedItem?.toString()
                val bulan = bulanMap[bulanDisplay] ?: ""
                loadHistory(relay, bulan)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerRelay.onItemSelectedListener = listener
        spinnerBulan.onItemSelectedListener = listener
    }

    private fun loadBulan() {
        val bulanList = mutableListOf<String>()
        bulanMap.clear()

        // Tambah ALL
        bulanList.add("ALL")
        bulanMap["ALL"] = ""

        // Tahun saat ini
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val outputFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        val inputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        for (i in 1..12) {
            val bulanServer = String.format("%04d-%02d", year, i)
            val date = inputFormat.parse(bulanServer)
            val bulanDisplay = outputFormat.format(date!!)
            bulanList.add(bulanDisplay)
            bulanMap[bulanDisplay] = bulanServer
        }

        runOnUiThread {
            spinnerBulan.adapter = ArrayAdapter(
                this@Histori,
                android.R.layout.simple_spinner_dropdown_item,
                bulanList
            )
        }
    }

    private fun loadHistory(relay: String, bulan: String) {
        val url = "$baseUrl?relay=$relay&bulan=$bulan"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty() && body.startsWith("{")) {
                    runOnUiThread {
                        try {
                            listHistory.clear()
                            val jsonObject = JSONObject(body)

                            if (jsonObject.getBoolean("success")) {
                                val dataArray = jsonObject.getJSONArray("data")
                                for (i in 0 until dataArray.length()) {
                                    val obj = dataArray.getJSONObject(i)
                                    listHistory.add(
                                        HistoriModel(
                                            obj.getString("relay"),
                                            obj.getString("status"),
                                            obj.getString("chipid"),
                                            obj.getString("waktu")
                                        )
                                    )
                                }
                                adapter.notifyDataSetChanged()
                                if (dataArray.length() == 0) {
                                    Toast.makeText(
                                        this@Histori,
                                        "Data histori kosong",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val msg = jsonObject.optString("msg", "Gagal ambil data")
                                Toast.makeText(this@Histori, msg, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(
                                this@Histori,
                                "Error parsing data",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }
}
