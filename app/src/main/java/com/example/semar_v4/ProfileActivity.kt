package com.example.semar_v4

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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

    private var userId: Int = 0
    private var photo: String = ""
    private val BASE_URL = "http://192.168.0.111" // sesuaikan dengan folder API kamu

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvBio = findViewById(R.id.tvBio)
        imgProfile = findViewById(R.id.profileImage)
        btnEdit = findViewById(R.id.btnEditProfile)

        // Ambil userId dari LoginActivity
        userId = intent.getIntExtra("id", 0)

        // Load data profile dari server
        loadProfile(userId)

        btnEdit.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("id", userId)
            intent.putExtra("username", tvName.text.toString())
            intent.putExtra("email", tvEmail.text.toString())
            intent.putExtra("bio", tvBio.text.toString())
            intent.putExtra("photo", photo)
            startActivityForResult(intent, 1001)
        }
    }

    private fun loadProfile(id: Int) {
        val request = Request.Builder()
            .url("$BASE_URL/GetProfile.php?id=$id")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Gagal koneksi ke server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    runOnUiThread {
                        if (json.getString("status") == "success") {
                            val data = json.getJSONObject("data")
                            tvName.text = data.getString("username")
                            tvEmail.text = data.getString("email")
                            tvBio.text = data.optString("bio", "")
                            photo = data.optString("photo", "")

                            if (photo.isNotEmpty()) {
                                Glide.with(this@ProfileActivity)
                                    .load("$BASE_URL/$photo")
                                    .into(imgProfile)
                            }
                        } else {
                            Toast.makeText(this@ProfileActivity, json.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            // Refresh data setelah edit
            loadProfile(userId)
            Toast.makeText(this, "Profil diperbarui", Toast.LENGTH_SHORT).show()
        }
    }
}
