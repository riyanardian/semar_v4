package com.example.semar_v4

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private val httpClient = OkHttpClient()

    override fun onStart() {
        super.onStart()
        // Cek apakah user sudah login sebelumnya
        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
        if (isLoggedIn) {
            // User sudah login, langsung ke BerandaActivity
            startActivity(Intent(this, BerandaActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val inputEmail = findViewById<EditText>(R.id.inputEmail)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnReg = findViewById<Button>(R.id.btnReg)

        btnLogin.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Isi email dan password!", Toast.LENGTH_SHORT).show()
            }
        }

        btnReg.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("http://10.204.219.1/Login.php") // ganti sesuai IP laptop kamu
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Gagal konek ke server!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                runOnUiThread {
                    try {
                        val json = JSONObject(resp)
                        val success = json.getBoolean("success")
                        val message = json.getString("message")

                        if (success) {
                            val user = json.getJSONObject("user")

                            // simpan ke SharedPreferences
                            val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putInt("id", user.getInt("id"))
                                putString("username", user.getString("username"))
                                putString("email", user.getString("email"))
                                putString("photo", user.optString("photo", ""))

                                putBoolean("isLoggedIn", true) // <-- tambahkan ini

                                putString("bio", user.optString("bio", ""))

                                apply()
                            }

                            Toast.makeText(this@LoginActivity, "Login sukses!", Toast.LENGTH_SHORT).show()

                            // pindah ke halaman utama (contoh BerandaActivity)


                            // pindah ke Beranda

                            startActivity(Intent(this@LoginActivity, BerandaActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Error parsing: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
