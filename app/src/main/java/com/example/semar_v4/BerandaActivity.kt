package com.example.semar_v4

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.semar_v4.header.ManualFragment
import com.example.semar_v4.header.OtomatisFragment
import com.example.semar_v4.service.MqttService
import de.hdodenhof.circleimageview.CircleImageView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class BerandaActivity : AppCompatActivity() {

    private lateinit var btnManual: Button
    private lateinit var btnOtomatis: Button
    private lateinit var layoutDefault: LinearLayout
    private lateinit var device: ImageView
    private lateinit var jadwal: ImageView
    private lateinit var histori: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var profileImage: CircleImageView
    private lateinit var welcomeText: TextView
    private lateinit var tvGreeting: TextView

    private val devices = mutableListOf<DeviceModel>()
    private var adapter: DeviceAdapter? = null
    private val BASE_URL = "http://103.197.190.79/api_mysql"
    private val client = OkHttpClient()
    private var modeReceiver: BroadcastReceiver? = null // simpan receiver

    private var selectedChip: String? = null
    private var selectedDeviceName: String? = null
    private var selectedDeviceType: String? = null
    private var isAdmin = false
    private var mqttService: MqttService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isBound = true
            subscribeModeTopic()

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService = null
            isBound = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_beranda)

        isAdmin = intent.getBooleanExtra("isAdmin", false)
// Button hanya aktif untuk admin

        initViews()
        setupRecyclerView()
        setGreeting()
        loadDevices()
        restoreSelectedDevice()
        setupControlButtons()
        setupClickListeners()
        loadUserSession()
        // Terapkan warna abu-abu pada tombol secara default saat aplikasi pertama kali dimuat
        btnManual.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
        btnOtomatis.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)


    }

    private fun initViews() {
        btnManual = findViewById(R.id.btnManual)
        btnOtomatis = findViewById(R.id.btnOtomatis)
        layoutDefault = findViewById(R.id.layoutDefault)
        device = findViewById(R.id.btnTambahDevice)
        jadwal = findViewById(R.id.btnJadwal)
        histori = findViewById(R.id.histori)
        recyclerView = findViewById(R.id.recyclerView)
        profileImage = findViewById(R.id.profileImage)
        welcomeText = findViewById(R.id.welcomeText)
        tvGreeting = findViewById(R.id.tvGreeting)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices,
            onClick = { d -> showDeviceDetail(d) },
            onSwitchChanged = { device, isChecked -> /* handle switch */ },
            onDeleteClicked = { pos -> deleteDeviceAt(pos) }
        )
        recyclerView.adapter = adapter
    }

    private fun deleteDeviceAt(position: Int) {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val count = sharedPref.getInt("deviceCount", 0)
        val newList = mutableListOf<Triple<String, String, String>>()
        for (i in 1..count) {
            val name = sharedPref.getString("device_${i}_name", "") ?: ""
            val type = sharedPref.getString("device_${i}_type", "") ?: ""
            val chip = sharedPref.getString("device_${i}_chip", "") ?: ""
            if (i - 1 != position) newList.add(Triple(name, type, chip))
        }
        editor.clear()
        newList.forEachIndexed { index, triple ->
            val (name, type, chip) = triple
            val newIndex = index + 1
            editor.putString("device_${newIndex}_name", name)
            editor.putString("device_${newIndex}_type", type)
            editor.putString("device_${newIndex}_chip", chip)
        }
        editor.putInt("deviceCount", newList.size)
        editor.apply()

        devices.removeAt(position)
        adapter?.notifyItemRemoved(position)

        // jika device yang dipilih dihapus, pilih device pertama yang tersisa
        if (getSelectedDevice() == null && devices.isNotEmpty()) {
            val first = devices.first()
            sharedPref.edit().apply {
                putString("selected_name", first.name)
                putString("selected_type", first.type)
                putString("selected_chip", first.chipId)
                apply()
            }
            setupControlButtons()
        }
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..10 -> "Good Morning"
            in 11..14 -> "Good Afternoon"
            in 15..17 -> "Good Evening"
            in 18..21 -> "Good Night"
            else -> "Hello"
        }
        tvGreeting.text = greeting
    }

    fun showDeviceSelection() {
        layoutDefault.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (devices.isNotEmpty()) View.VISIBLE else View.GONE
        supportFragmentManager.beginTransaction()
            .remove(supportFragmentManager.findFragmentById(R.id.bodyContainer)!!)
            .commit()
        disableControlButtons()
    }

    private fun loadDevices() {
        val sharedPref = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val deviceCount = sharedPref.getInt("deviceCount", 0)
        if (deviceCount > 0) {
            layoutDefault.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            devices.clear()
            for (i in 1..deviceCount) {
                val name = sharedPref.getString("device_${i}_name", "") ?: ""
                val type = sharedPref.getString("device_${i}_type", "") ?: ""
                val chip = sharedPref.getString("device_${i}_chip", "") ?: ""
                if (name.isNotEmpty() && type.isNotEmpty() && chip.isNotEmpty())
                    devices.add(DeviceModel(name, type, chip))
            }
            adapter?.notifyDataSetChanged()
        } else {
            layoutDefault.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun restoreSelectedDevice() {
        val sp = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val chip = sp.getString("selected_chip", null)
        if (chip != null) {
            val device = devices.firstOrNull { it.chipId == chip }
            if (device != null) {
                selectedChip = device.chipId
                selectedDeviceName = device.name
                selectedDeviceType = device.type
                return
            } else {
                sp.edit().remove("selected_chip")
                    .remove("selected_device_name")
                    .remove("selected_device_type")
                    .apply()
            }
        }
        if (devices.isNotEmpty() && selectedChip == null) {
            val first = devices.first()
            sp.edit().apply {
                putString("selected_chip", first.chipId)
                putString("selected_device_name", first.name)
                putString("selected_device_type", first.type)
                apply()
            }
            selectedChip = first.chipId
            selectedDeviceName = first.name
            selectedDeviceType = first.type
        }
    }


    private fun setupControlButtons() {
        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
        val isAdmin = sharedPref.getBoolean("isAdmin", false)

        val selectedDevice = getSelectedDevice()

        // Enable tombol hanya jika admin dan device tidak null
        val enableButtons = isAdmin && selectedDevice != null
        btnManual.isEnabled = enableButtons
        btnOtomatis.isEnabled = enableButtons

        if (enableButtons) {
            // Safe call untuk non-null device
            selectedDevice?.let { device ->
                btnManual.setOnClickListener {
                    openFragment(ManualFragment(), device)
                    updateButtonColor(true)
                    mqttService?.publish("device/$selectedChip/mode", "MANUAL")
                    Log.d("BerandaActivity", "Admin published MANUAL")

                }

                btnOtomatis.setOnClickListener {
                    openFragment(OtomatisFragment(), device)
                    updateButtonColor(false)
                    mqttService?.publish("device/$selectedChip/mode", "AUTO")
                    Log.d("BerandaActivity", "Admin published AUTO")

                }
            }
        } else {
            disableControlButtons()
        }
    }

// Di BerandaActivity

    private fun subscribeModeTopic() {
        if (selectedChip == null || !isBound) return

        val topic = "device/$selectedChip/mode"
        mqttService?.subscribeTopic(topic)
        Log.d("BerandaActivity", "Subscribed to $topic")

        // unregister receiver lama jika ada
        modeReceiver?.let {
            try { unregisterReceiver(it) } catch (e: IllegalArgumentException) { }
        }

        modeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val t = intent?.getStringExtra("topic")
                val payload = intent?.getStringExtra("payload") ?: return
                if (t == topic) {
                    runOnUiThread {
                        when(payload) {
                            "MANUAL" -> updateButtonColor(true)
                            "AUTO" -> updateButtonColor(false)
                            else -> Log.d("BerandaActivity", "Payload tidak dikenali: $payload")
                        }
                        Log.d("BerandaActivity", "UI updated for $payload")
                    }
                }
            }
        }

        val intentFilter = IntentFilter("MQTT_MESSAGE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(modeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(modeReceiver, intentFilter)
        }
    }


    private fun disableControlButtons() {
        btnManual.isEnabled = false
        btnOtomatis.isEnabled = false
        btnManual.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
        btnOtomatis.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
    }

    private fun enableControlButtons() {
        btnManual.isEnabled = true
        btnOtomatis.isEnabled = true
    }

    private fun updateButtonColor(isManual: Boolean) {
        if (isManual) {
            btnManual.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_700)
            btnOtomatis.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
        } else {
            btnManual.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
            btnOtomatis.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_700)
        }
    }

    private fun getSelectedDevice(): DeviceModel? {
        val sp = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        val chip = sp.getString("selected_chip", null) ?: return null
        return devices.firstOrNull { it.chipId == chip }
    }

    private fun openFragment(fragment: Fragment, device: DeviceModel) {
        fragment.arguments = Bundle().apply {
            putString("deviceName", device.name)
            putString("deviceType", device.type)
            putString("chipId", device.chipId)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.bodyContainer, fragment)
            .commit()
        layoutDefault.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun setupClickListeners() {
        device.setOnClickListener { startActivity(Intent(this, Device::class.java)) }
        val btnAccount = findViewById<ImageView>(R.id.btnAccount)
        btnAccount.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        profileImage.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        jadwal.setOnClickListener { startActivity(Intent(this, Jadwal::class.java)) }
        histori.setOnClickListener { startActivity(Intent(this, Histori::class.java)) }
    }

    private fun loadUserSession() {
        val sharedUser = getSharedPreferences("user_session", MODE_PRIVATE)
        val username = sharedUser.getString("username", "User")
        welcomeText.text = "Welcome, $username!!"
        val userId = sharedUser.getInt("id", 0)
        if (userId != 0) loadUserPhoto(userId)
    }

    private fun loadUserPhoto(id: Int) {
        val request = Request.Builder().url("$BASE_URL/get_profile.php?id=$id").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { profileImage.setImageResource(R.drawable.ic_user_placeholder) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    try {
                        val json = JSONObject(it)
                        if (json.getString("status") == "success") {
                            val photo = json.getJSONObject("data").optString("photo", "")
                            runOnUiThread {
                                if (photo.isNotEmpty() && photo != "null") {
                                    Glide.with(this@BerandaActivity)
                                        .load("$BASE_URL/$photo")
                                        .placeholder(R.drawable.ic_user_placeholder)
                                        .error(R.drawable.ic_user_placeholder)
                                        .into(profileImage)
                                } else profileImage.setImageResource(R.drawable.ic_user_placeholder)
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { profileImage.setImageResource(R.drawable.ic_user_placeholder) }
                    }
                }
            }
        })
    }

    private fun showDeviceDetail(device: DeviceModel) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(device.name)
            .setMessage("📌 Spesifikasi:\nTipe: ${device.type}\nChip ID: ${device.chipId}\n\nKlik OK untuk masuk kontrol.")
            .setPositiveButton("OK") { _, _ -> selectDevice(device) }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun selectDevice(device: DeviceModel) {
        val sp = getSharedPreferences("my_devices", Context.MODE_PRIVATE)
        sp.edit().apply {
            putString("selected_chip", device.chipId)
            putString("selected_device_name", device.name)
            putString("selected_device_type", device.type)
            apply()
        }
        selectedChip = device.chipId
        selectedDeviceName = device.name
        selectedDeviceType = device.type
        enableControlButtons()
        updateButtonColor(true)
        openFragment(ManualFragment(), device)
    }
    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MqttService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
