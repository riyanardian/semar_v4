import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.semar_v4.R
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    private lateinit var emailRegister: EditText
    private lateinit var passwordRegister: EditText
    private lateinit var confirmPasswordRegister: EditText
    private lateinit var btnRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // Sesuaikan dengan nama file XML Anda

        // Inisialisasi View
        emailRegister = findViewById(R.id.emailRegister)
        passwordRegister = findViewById(R.id.passwordRegister)
        confirmPasswordRegister = findViewById(R.id.confirmPasswordRegister)
        btnRegister = findViewById(R.id.btnRegister)

        // Set onClickListener untuk tombol register
        btnRegister.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val email = emailRegister.text.toString().trim()
        val password = passwordRegister.text.toString().trim()
        val confirmPassword = confirmPasswordRegister.text.toString().trim()

        // Validasi input
        if (email.isEmpty()) {
            emailRegister.error = "Email tidak boleh kosong"
            emailRegister.requestFocus()
            return
        }

        if (password.isEmpty()) {
            passwordRegister.error = "Password tidak boleh kosong"
            passwordRegister.requestFocus()
            return
        }

        if (password != confirmPassword) {
            confirmPasswordRegister.error = "Password tidak cocok"
            confirmPasswordRegister.requestFocus()
            return
        }

        // URL server register (ganti dengan URL server Anda)
        val url = "http://10.0.2.2/android_login/register.php" // Untuk emulator
        // Untuk device fisik: "http://192.168.1.100/android_login/register.php"

        // Membuat request
        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val success = jsonObject.getBoolean("success")
                    val message = jsonObject.getString("message")

                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish() // Kembali ke LoginActivity
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