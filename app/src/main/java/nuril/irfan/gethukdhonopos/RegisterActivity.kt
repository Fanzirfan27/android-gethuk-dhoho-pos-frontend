package nuril.irfan.gethukdhonopos

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnDaftar.setOnClickListener {
            val nama = b.etNama.text?.toString()?.trim().orEmpty()
            val email = b.etEmail.text?.toString()?.trim().orEmpty()
            val pass  = b.etPassword.text?.toString()?.trim().orEmpty()
            if (nama.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                toast("Semua field wajib diisi"); return@setOnClickListener
            }

            setLoading(true)
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { res ->
                    val uid = res.user!!.uid
                    val data = hashMapOf(
                        "nama" to nama,
                        "email" to email,
                        "role" to "kasir",
                        "aktif" to false,               // ← pending approval
                        "created_at" to Timestamp.now()
                    )
                    db.collection("users").document(uid).set(data)
                        .addOnSuccessListener {
                            toast("Pendaftaran berhasil. Menunggu persetujuan admin.")
                            // sign-out biar tidak bisa masuk sebelum di-approve
                            auth.signOut()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            toast("Gagal menyimpan profil: ${e.message}")
                            // rollback akun auth (opsional)
                            res.user?.delete()
                            setLoading(false)
                        }
                }
                .addOnFailureListener { e ->
                    toast("Gagal daftar: ${e.message}")
                    setLoading(false)
                }
        }
    }

    private fun setLoading(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnDaftar.isEnabled = !on
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
