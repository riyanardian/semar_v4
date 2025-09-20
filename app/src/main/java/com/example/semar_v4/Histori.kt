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
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class Histori : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var spinnerMode: Spinner
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
        spinnerMode = findViewById(R.id.spinnerMode)
        spinnerBulan = findViewById(R.id.spinnerTanggal) // 🔹 masih pakai id lama
        spinnerRelay = findViewById(R.id.spinnerRelay)
        btnBack = findViewById(R.id.btnBack)

        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoriAdapter(listHistory)
        recyclerHistory.adapter = adapter

        setupSpinners()
        loadBulan()
        loadHistory("ALL", "ALL", "")

        btnBack.setOnClickListener { startActivity(Intent(this, BerandaActivity::class.java)) }


        // tombol refresh
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            loadBulan()
            val mode = spinnerMode.selectedItem?.toString() ?: "ALL"
            val relay = spinnerRelay.selectedItem?.toString() ?: "ALL"
            val bulanDisplay = spinnerBulan.selectedItem?.toString()
            val bulan = bulanMap[bulanDisplay] ?: ""
            loadHistory(relay, mode, bulan)
        }
    }

    private fun setupSpinners() {
        val modeOptions = arrayOf("ALL", "Manual", "Otomatis")
        val relayOptions = arrayOf("ALL", "Relay1", "Relay2")

        spinnerMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeOptions)
        spinnerRelay.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relayOptions)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val mode = spinnerMode.selectedItem?.toString() ?: "ALL"
                val relay = spinnerRelay.selectedItem?.toString() ?: "ALL"
                val bulanDisplay = spinnerBulan.selectedItem?.toString()
                val bulan = bulanMap[bulanDisplay] ?: ""
                loadHistory(relay, mode, bulan)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerMode.onItemSelectedListener = listener
        spinnerRelay.onItemSelectedListener = listener
        spinnerBulan.onItemSelectedListener = listener
    }

    private fun loadBulan() {
        val url = "$baseUrl?bulan_only=1"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty() && body.startsWith("[")) {
                    val jsonArray = JSONArray(body)
                    val bulanList = mutableListOf("ALL")
                    bulanMap.clear()
                    bulanMap["ALL"] = ""

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val bulanServer = obj.getString("bulan") // format: 2025-09
                        val bulanDisplay = formatBulan(bulanServer) // jadi: September 2025

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
            }
        })
    }

    private fun loadHistory(relay: String, mode: String, bulan: String) {
        val url = "$baseUrl?relay=$relay&mode=$mode&bulan=$bulan"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty() && body.startsWith("[")) {
                    runOnUiThread {
                        listHistory.clear()
                        val jsonArray = JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val relayVal = obj.getString("relay")
                            val status = obj.getString("status")
                            val modeVal = obj.getString("mode")
                            val waktu = obj.getString("waktu")
                            listHistory.add(HistoriModel(relayVal, status, modeVal, waktu))
                        }
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }

    // 🔹 ubah YYYY-MM jadi "September 2025"
    private fun formatBulan(bulanServer: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val date = inputFormat.parse(bulanServer)
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
            outputFormat.format(date!!)
        } catch (e: Exception) {
            bulanServer
        }
    }
}
