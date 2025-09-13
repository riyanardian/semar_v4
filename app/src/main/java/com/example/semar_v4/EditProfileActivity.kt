package com.example.semar_v4

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody


class EditProfileActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val BASE_URL = "http://192.168.0.111"

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etBio: EditText
    private lateinit var btnSave: Button
    private lateinit var imgProfile: ImageView
    private lateinit var btnChoosePhoto: Button

    private var userId: Int = 0
    private var currentPhoto: String = ""
    private var selectedImageUri: Uri? = null

    private val PICK_IMAGE_REQUEST = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etBio = findViewById(R.id.etBio)
        btnSave = findViewById(R.id.btnSave)
        imgProfile = findViewById(R.id.imgEditProfile)
        btnChoosePhoto = findViewById(R.id.btnChoosePhoto)

        // Ambil data dari intent
        userId = intent.getIntExtra("id", 0)
        etName.setText(intent.getStringExtra("username"))
        etEmail.setText(intent.getStringExtra("email"))
        etBio.setText(intent.getStringExtra("bio"))
        currentPhoto = intent.getStringExtra("photo") ?: ""

        // Load foto awal
        if (currentPhoto.isNotEmpty()) {
            Glide.with(this)
                .load("$BASE_URL/$currentPhoto")
                .into(imgProfile)
        }

        // Pilih foto baru
        btnChoosePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        btnSave.setOnClickListener {
            updateProfile(userId, etName.text.toString(), etEmail.text.toString(), etBio.text.toString())
        }
    }

    private fun updateProfile(id: Int, username: String, email: String, bio: String) {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("id", id.toString())
            .addFormDataPart("username", username)
            .addFormDataPart("email", email)
            .addFormDataPart("bio", bio)

        // Jika user pilih foto baru
        selectedImageUri?.let { uri ->
            val filePath = getRealPathFromURI(uri)
            filePath?.let {
                val file = File(it)
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                builder.addFormDataPart("photo", file.name, requestFile)
            }
        }


        val requestBody = builder.build()

        val request = Request.Builder()
            .url("$BASE_URL/UpdateProfile.php")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@EditProfileActivity, "Update gagal", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@EditProfileActivity, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()

                    // Balikkan data baru ke ProfileActivity
                    val intent = Intent()
                    intent.putExtra("username", username)
                    intent.putExtra("email", email)
                    intent.putExtra("bio", bio)
                    if (selectedImageUri != null) {
                        intent.putExtra("photo", "uploads/${System.currentTimeMillis()}_${userId}.jpg")
                        // path ini harus sama dengan yang disimpan server
                    } else {
                        intent.putExtra("photo", currentPhoto)
                    }

                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        })
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return if (cursor == null) {
            uri.path
        } else {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            val path = cursor.getString(idx)
            cursor.close()
            path
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            imgProfile.setImageURI(selectedImageUri)
        }
    }
}
