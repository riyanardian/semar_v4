package com.example.semar_v4

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Device : AppCompatActivity() {

    private lateinit var btnSetWifi: Button
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_device)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnSetWifi = findViewById(R.id.btnSetWifi)
        btnBack = findViewById(R.id.btnBack)

        // Tombol set WiFi lokasi (rumah/kantor)
        btnSetWifi.setOnClickListener {
            showSetWifiDialog()
        }

        // Tombol kembali
        btnBack.setOnClickListener {
            val intent = Intent(this, BerandaActivity::class.java)
            startActivity(intent)
        }
    }

    // 🔹 Class untuk kirim config WiFi ke ESP
    class WifiConfigSender(private val context: Context) {

        private val client = OkHttpClient()

        fun sendWifiConfig(ssid: String, pass: String, callback: (Boolean, String) -> Unit) {
            val url = "http://192.168.4.1/setwifi"   // alamat default ESP AP

            val formBody = FormBody.Builder()
                .add("ssid", ssid)
                .add("pass", pass)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(false, "Gagal konek ke ESP: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            callback(false, "Response error: ${it.code}")
                            return
                        }

                        val body = it.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val status = json.optString("status")

                            if (status == "ok") {
                                val deviceName = json.getString("device_name")
                                val deviceType = json.getString("device_type")
                                val chipId = json.getString("chip_id")

                                // ✅ Simpan device ke SharedPreferences
                                val sharedPref = context.getSharedPreferences("my_devices", Context.MODE_PRIVATE)
                                val editor = sharedPref.edit()
                                val count = sharedPref.getInt("deviceCount", 0) + 1

                                editor.putInt("deviceCount", count)
                                editor.putString("device_${count}_name", deviceName)
                                editor.putString("device_${count}_type", deviceType)
                                editor.putString("device_${count}_chip", chipId)
                                editor.apply()

                                callback(true, "Berhasil tambah $deviceName ($deviceType)")
                            } else {
                                callback(false, "ESP balikin error")
                            }
                        } else {
                            callback(false, "Response kosong")
                        }
                    }
                }
            })
        }
    }

    // 🔹 Dialog set WiFi lokasi
    private fun showSetWifiDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Lokasi WiFi")
        builder.setMessage("Masukkan SSID & Password WiFi rumah")

        val inputLayout = layoutInflater.inflate(R.layout.setwifiap, null)
        val inputSSID = inputLayout.findViewById<EditText>(R.id.inputSSID)
        val inputPassword = inputLayout.findViewById<EditText>(R.id.inputPassword)

        builder.setView(inputLayout)

        builder.setPositiveButton("Kirim") { _, _ ->
            val ssid = inputSSID.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (ssid.isNotEmpty() && password.isNotEmpty()) {
                val sender = WifiConfigSender(this)
                sender.sendWifiConfig(ssid, password) { success, msg ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            // balik ke Beranda setelah sukses
                            val intent = Intent(this, BerandaActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Isi SSID & Password WiFi lokasi", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Batal", null)
        builder.show()
    }
}
