package com.example.semar_v4

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class Device : AppCompatActivity() {
    private val httpClient = OkHttpClient()

    private lateinit var btnSetWifi: Button
    private lateinit var btnBack: ImageView
    private lateinit var btnAddDevice: Button

    // MQTT
    private lateinit var mqttClient: MqttClient
    private val mqttBroker = "tcp://test.mosquitto.org:1883"
    private val topicRegister = "semar/devices/register"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        btnSetWifi = findViewById(R.id.btnSetWifi)
        btnBack = findViewById(R.id.btnBack)
        btnAddDevice = findViewById(R.id.btnAddDevice)

        btnSetWifi.setOnClickListener { showSetWifiDialog() }
        btnBack.setOnClickListener {
            startActivity(Intent(this, BerandaActivity::class.java))
            finish()
        }

        initializeMqtt()

        // Cek permission
        checkPermissions()
        val userEmail = intent.getStringExtra("userEmail") ?: ""
        val userPassword = intent.getStringExtra("userPassword") ?: ""
        checkAdmin(userEmail, userPassword) { isAdmin ->
            btnSetWifi.isEnabled = isAdmin
            btnSetWifi.alpha = if (isAdmin) 1f else 0.5f
            btnAddDevice.isEnabled = true
        }

        btnAddDevice.setOnClickListener {
            fetchDevicesFromServer { devices ->
                if (devices.isEmpty()) {
                    Toast.makeText(this, "Tidak ada device terdaftar", Toast.LENGTH_SHORT).show()
                    return@fetchDevicesFromServer
                }

                val names = devices.map { it.getString("device_name") }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Pilih Device")
                    .setItems(names) { _, which ->
                        val selected = devices[which]
                        val name = selected.getString("device_name")
                        val type = selected.getString("device_type")
                        val chip = selected.getString("chip_id")

                        saveDeviceToSharedPref(name, type, chip)

                        Toast.makeText(this, "Device $name ditambahkan", Toast.LENGTH_LONG).show()

                        // 👉 Langsung redirect ke BerandaActivity
                        val intent = Intent(this, BerandaActivity::class.java)
                        intent.putExtra("newDeviceName", name)
                        intent.putExtra("newDeviceType", type)
                        intent.putExtra("newDeviceChip", chip)
                        startActivity(intent)
                        finish()
                    }
                    .show()
            }
        }

    }

    // -------------------- MQTT SETUP --------------------
    private fun initializeMqtt() {
        try {
            mqttClient = MqttClient(
                mqttBroker,
                MqttClient.generateClientId(),
                null
            )

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false   // jangan clean session, biar pesan retained tetap ada
                connectionTimeout = 10
                keepAliveInterval = 60
            }

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    runOnUiThread {
                        Toast.makeText(this@Device, "MQTT koneksi hilang", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (message == null) return
                    val msg = message.toString()

                    runOnUiThread {
                        Toast.makeText(this@Device, "Pesan dari $topic: $msg", Toast.LENGTH_SHORT).show()
                    }

                    if (topic == topicRegister) {
                        try {
                            val json = JSONObject(msg)
                            val deviceName = json.optString("device_name", "Unknown")
                            val deviceType = json.optString("device_type", "Unknown")
                            val chipId = json.optString("chip_id", "N/A")

                            saveDeviceToSharedPref(deviceName, deviceType, chipId)
                            sendDeviceToServer(deviceName, deviceType, chipId)

                            runOnUiThread {
                                Toast.makeText(this@Device, "Device diterima: $deviceName", Toast.LENGTH_LONG).show()
                                val intent = Intent(this@Device, BerandaActivity::class.java)
                                intent.putExtra("newDeviceName", deviceName)
                                startActivity(intent)
                                finish()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options)
            subscribeToTopics()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // -------------------- Subscribe --------------------
    private fun subscribeToTopics() {
        try {
            mqttClient.subscribe(topicRegister, 1)
            runOnUiThread {
                Toast.makeText(this, "Subscribed ke $topicRegister", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // -------------------- SIMPAN LOCAL --------------------
    private fun saveDeviceToSharedPref(name: String, type: String, chip: String) {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val count = sharedPref.getInt("deviceCount", 0) + 1

        editor.putInt("deviceCount", count)
        editor.putString("device_${count}_name", name)
        editor.putString("device_${count}_type", type)
        editor.putString("device_${count}_chip", chip)
        editor.apply()
    }
    // 🔹 Cek & minta permission runtime
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }
    // -------------------- KIRIM KE MYSQL --------------------
    private fun sendDeviceToServer(name: String, type: String, chip: String) {
        val client = OkHttpClient()

        val formBody = FormBody.Builder()
            .add("device_name", name)
            .add("device_type", type)
            .add("chip_id", chip)
            .build()

        val request = Request.Builder()
            .url("http://103.197.190.79/api_mysql/device.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@Device, "Gagal kirim ke server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val resp = it.body?.string() ?: "No response"
                    runOnUiThread {
                        Toast.makeText(this@Device, "Server response: $resp", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // -------------------- WIFI CONFIG --------------------
    inner class WifiConfigSender(private val context: Context) {
        private val client = OkHttpClient()

        fun sendWifiConfig(ssid: String, pass: String, callback: (Boolean, String) -> Unit) {
            val url = "http://192.168.4.1/setwifi"

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
                    callback(false, "ESP error: ${e.message}")
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
                                callback(true, "WiFi berhasil dikirim, tunggu ESP konek...")

                                // 🔹 Delay 5 detik lalu connect MQTT ulang
                                runOnUiThread {
                                    android.os.Handler(mainLooper).postDelayed({
                                        initializeMqtt()
                                    }, 5000)
                                }
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

    // -------------------- DIALOG SET WIFI --------------------
    private fun showSetWifiDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set WiFi Lokasi")

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
                    }
                }
            } else {
                Toast.makeText(this, "Isi SSID & Password WiFi", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
            }
        } catch (_: Exception) {}
    }

    private fun fetchDevicesFromServer(onResult: (List<JSONObject>) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://103.197.190.79/api_mysql/get_devices.php") // ganti sesuai IP server
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@Device, "❌ Gagal ambil data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("API_ERROR", "Gagal ambil data", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: "[]"
                    Log.d("API_RESPONSE", body) // debug respon asli

                    try {
                        val list = mutableListOf<JSONObject>()

                        // cek dulu responnya array atau object
                        if (body.trim().startsWith("[")) {
                            val arr = JSONArray(body)
                            for (i in 0 until arr.length()) {
                                list.add(arr.getJSONObject(i))
                            }
                        } else if (body.trim().startsWith("{")) {
                            val obj = JSONObject(body)
                            // misal kalau server balikin {"devices":[...]}
                            if (obj.has("devices")) {
                                val arr = obj.getJSONArray("devices")
                                for (i in 0 until arr.length()) {
                                    list.add(arr.getJSONObject(i))
                                }
                            }
                        }

                        runOnUiThread {
                            onResult(list)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@Device, "⚠️ Error parsing JSON", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("API_PARSE_ERROR", "Respon bukan JSON valid", e)
                    }
                }
            }
        })
    }
    private fun checkAdmin(email: String, password: String, callback: (Boolean) -> Unit) {
        val url = "http://103.197.190.79/api_mysql/admin_device.php"

        val formBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    val isAdmin = json.getBoolean("isAdmin")
                    runOnUiThread { callback(isAdmin) }
                } ?: runOnUiThread { callback(false) }
            }
        })
    }


}
