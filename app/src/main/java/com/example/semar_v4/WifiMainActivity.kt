package com.example.semar_v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException


class WifiMainActivity : AppCompatActivity() {
    private lateinit var btnTurnOnWifi: Button
    private lateinit var btnSetWifi: Button
    private lateinit var wifiManager: WifiManager
    private val httpClient = OkHttpClient()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wifi_main)

        // Setup edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Init
        btnTurnOnWifi = findViewById(R.id.btnTurnOnWifi)
        btnSetWifi = findViewById(R.id.btnSetWifi)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        checkPermissions()

        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        // Tombol connect ke ESP
        btnTurnOnWifi.setOnClickListener {
            showWifiConnectDialog()
        }

        // Tombol set WiFi lokasi (rumah/kantor)
        btnSetWifi.setOnClickListener {
            showSetWifiDialog()
        }
    }

    // 🔹 Cek & minta permission runtime
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    // 🔹 Dialog connect ke ESP
    private fun showWifiConnectDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Sambungkan ke WiFi ESP")

        val inputLayout = layoutInflater.inflate(R.layout.setwifiap, null)
        val inputSSID = inputLayout.findViewById<EditText>(R.id.inputSSID)
        val inputPassword = inputLayout.findViewById<EditText>(R.id.inputPassword)

        builder.setView(inputLayout)

        builder.setPositiveButton("Sambungkan") { _, _ ->
            val ssid = inputSSID.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (ssid.isNotEmpty() && password.isNotEmpty()) {
                connectToESP(ssid, password)
            } else {
                Toast.makeText(this, "Isi SSID & Password ESP dulu", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Batal", null)
        builder.show()
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
                    Toast.makeText(this@WifiMainActivity, "Gagal kirim ke device!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                runOnUiThread {
                    Toast.makeText(this@WifiMainActivity, "Respon: $resp", Toast.LENGTH_LONG).show()
                }
            }
        })
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
                sendWiFiConfig(ssid, password)
                Toast.makeText(this, "WiFi ($ssid) berhasil dikirim ke ESP", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Isi SSID & Password WiFi lokasi", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    // 🔹 Hybrid connect ESP (support Android 9 & Android 10+)
    private fun connectToESP(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 ke atas pakai WifiNetworkSpecifier
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    runOnUiThread {
                        Toast.makeText(this@WifiMainActivity, "Berhasil terhubung ke ESP ($ssid)", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onUnavailable() {
                    runOnUiThread {
                        Toast.makeText(this@WifiMainActivity, "Gagal menyambung ke ESP", Toast.LENGTH_LONG).show()
                    }
                }
            })
        } else {
            // Android 9 kebawah pakai WifiConfiguration
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
            }

            val wifiConfig = WifiConfiguration()
            wifiConfig.SSID = String.format("\"%s\"", ssid)
            wifiConfig.preSharedKey = String.format("\"%s\"", password)

            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                Toast.makeText(this, "Berhasil terhubung ke ESP ($ssid)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal menyambung ke ESP WiFi", Toast.LENGTH_LONG).show()
            }
        }
    }
}
