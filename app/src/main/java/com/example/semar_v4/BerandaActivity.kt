package com.example.semar_v4

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*   // tambahkan import ini
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class BerandaActivity : AppCompatActivity() {

    private lateinit var ssidInput: EditText
    private lateinit var passInput: EditText
    private lateinit var btnConnect: Button
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_beranda)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ketika tombol ditekan -> kirim ke ESP
        btnConnect.setOnClickListener {
            val ssid = ssidInput.text.toString().trim()
            val pass = passInput.text.toString().trim()

            if (ssid.isNotEmpty() && pass.isNotEmpty()) {
                sendWiFiConfig(ssid, pass)
            } else {
                Toast.makeText(this, "Isi SSID & Password dulu!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendWiFiConfig(ssid: String, pass: String) {
        val formBody = FormBody.Builder()
            .add("ssid", ssid)
            .add("pass", pass)
            .build()

        val request = Request.Builder()
            .url("http://192.168.4.1/setwifi")  // IP default ESP saat AP mode
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@BerandaActivity, "Gagal kirim ke device!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                runOnUiThread {
                    Toast.makeText(this@BerandaActivity, "Respon: $resp", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}
