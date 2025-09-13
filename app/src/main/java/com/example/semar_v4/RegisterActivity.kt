package com.example.semar_v4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*
import java.io.IOException

class RegisterActivity : AppCompatActivity() {
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Setup edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Cek permission
        checkPermissions()

        // Ambil view dari layout
        val inputUsername = findViewById<EditText>(R.id.inputUsername)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val inputEmail = findViewById<EditText>(R.id.inputEmail)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // Klik tombol register
        btnRegister.setOnClickListener {
            val username = inputUsername.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            val email = inputEmail.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty() && email.isNotEmpty()) {
                sendRegister(username, password, email)
            } else {
                Toast.makeText(this, "Isi semua field!", Toast.LENGTH_LONG).show()
            }
        }
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

    // 🔹 Fungsi kirim data ke Register.php
    private fun sendRegister(username: String, password: String, email: String) {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("email", email)
            .build()

        val request = Request.Builder()
            .url("http://192.168.0.111/Registrasi.php") // ganti IP sesuai XAMPP/laptop kamu
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Gagal kirim ke server!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Respon server: $resp", Toast.LENGTH_LONG).show()

                    // Kalau respon dari server menunjukkan sukses
                    if (resp?.contains("Registrasi Berhasil", ignoreCase = true) == true) {
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish() // supaya tidak bisa kembali ke register
                    }
                }
            }

        })
        }
}