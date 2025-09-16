package com.example.semar_v4

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvBio: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var btnEdit: Button
    private lateinit var btnLogout: Button

    private var userId: Int = 0
    private var photo: String = ""
    private val BASE_URL = "http://10.204.219.1"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvBio = findViewById(R.id.tvBio)
        imgProfile = findViewById(R.id.profileImage)
        btnEdit = findViewById(R.id.btnEditProfile)
        btnLogout = findViewById(R.id.btnLogout)

        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
        userId = sharedPref.getInt("id", 0)
        val usernameFromLogin = sharedPref.getString("username", "") ?: ""
        val emailFromLogin = sharedPref.getString("email", "") ?: ""

        tvName.text = usernameFromLogin
        tvEmail.text = emailFromLogin

        if (userId != 0) {
            loadProfile(userId)
        } else {
            imgProfile.setImageResource(R.drawable.ic_user_placeholder)
        }

        btnEdit.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("id", userId)
            intent.putExtra("username", tvName.text.toString())
            intent.putExtra("email", tvEmail.text.toString())
            intent.putExtra("bio", tvBio.text.toString())
            intent.putExtra("photo", photo)
            startActivityForResult(intent, 1001)
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation(sharedPref)
        }
    }

    private fun showLogoutConfirmation(sharedPref: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Logout")
            .setMessage("Apakah kamu yakin ingin logout?")
            .setPositiveButton("Ya") { _, _ ->
                with(sharedPref.edit()) {
                    clear()
                    apply()
                }

                Toast.makeText(this, "Berhasil logout", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadProfile(id: Int) {
        val request = Request.Builder()
            .url("$BASE_URL/GetProfile.php?id=$id")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Gagal koneksi ke server", Toast.LENGTH_SHORT).show()
                    imgProfile.setImageResource(R.drawable.ic_user_placeholder)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    try {
                        val json = JSONObject(it)
                        runOnUiThread {
                            if (json.getString("status") == "success") {
                                val data = json.getJSONObject("data")

                                // Tambahan: kalau bio kosong/null tampilkan "Tulis bio"
                                val bioText = data.optString("bio", "")
                                tvBio.text = if (bioText.isNullOrEmpty() || bioText == "null") {
                                    "Tulis bio"
                                } else {
                                    bioText
                                }

                                photo = data.optString("photo", "")
                                if (photo.isNotEmpty() && photo != "null") {
                                    Glide.with(this@ProfileActivity)
                                        .load("$BASE_URL/$photo")
                                        .placeholder(R.drawable.ic_user_placeholder)
                                        .error(R.drawable.ic_user_placeholder)
                                        .into(imgProfile)
                                } else {
                                    imgProfile.setImageResource(R.drawable.ic_user_placeholder)
                                }
                            } else {
                                imgProfile.setImageResource(R.drawable.ic_user_placeholder)
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@ProfileActivity, "Error parsing data", Toast.LENGTH_SHORT).show()
                            imgProfile.setImageResource(R.drawable.ic_user_placeholder)
                        }
                    }
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            loadProfile(userId)
            Toast.makeText(this, "Profil diperbarui", Toast.LENGTH_SHORT).show()
        }
    }
}
