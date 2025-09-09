import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.semar_v4.BerandaActivity
import com.example.semar_v4.R
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var inputEmail: EditText
    private lateinit var inputPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnReg: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // Ganti dengan nama file layout Anda

        // Inisialisasi View
        inputEmail = findViewById(R.id.inputEmail)
        inputPassword = findViewById(R.id.inputPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnReg = findViewById(R.id.btnReg)

        // Set onClickListener
        btnLogin.setOnClickListener {
            loginUser()
        }

        btnReg.setOnClickListener {
            // Pindah ke RegisterActivity
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = inputEmail.text.toString().trim()
        val password = inputPassword.text.toString().trim()

        // Validasi input
        if (email.isEmpty()) {
            inputEmail.error = "Email tidak boleh kosong"
            inputEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            inputPassword.error = "Password tidak boleh kosong"
            inputPassword.requestFocus()
            return
        }

        // URL server login (ganti dengan URL server Anda)
        val url = "http://10.0.2.2/android_login/login.php" // Untuk emulator
        // Untuk device fisik: "http://192.168.1.100/android_login/login.php"

        // Membuat request
        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val success = jsonObject.getBoolean("success")
                    val message = jsonObject.getString("message")

                    if (success) {
                        val userId = jsonObject.getInt("user_id")

                        // Simpan user_id ke SharedPreferences
                        val sharedPreferences = getSharedPreferences("user_data", MODE_PRIVATE)
                        sharedPreferences.edit().putInt("user_id", userId).apply()

                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                        // Pindah ke MainActivity
                        startActivity(Intent(this, BerandaActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["email"] = email
                params["password"] = password
                return params
            }
        }

        // Menambahkan request ke queue
        Volley.newRequestQueue(this).add(stringRequest)
    }
}