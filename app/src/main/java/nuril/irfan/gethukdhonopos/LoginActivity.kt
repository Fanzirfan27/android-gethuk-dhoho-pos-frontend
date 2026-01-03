package nuril.irfan.gethukdhonopos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    companion object {
        private const val TAG_FIAM = "FIAM"
        const val EXTRA_SHOW_WELCOME = "EXTRA_SHOW_WELCOME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // FIAM siap dipakai (umumnya tak masalah kalau diset di KasirHome juga)
        FirebaseInAppMessaging.getInstance().apply {
            setMessagesSuppressed(false)
            isAutomaticDataCollectionEnabled = true
        }

        // Bantu testing: ambil Installation ID untuk “Test on device”
        FirebaseInstallations.getInstance().id.addOnSuccessListener { id ->
            Log.d(TAG_FIAM, "Installation ID: $id")
        }

        // Auto-route jika sudah login
        auth.currentUser?.let { fetchRoleAndRoute(it.uid) }
        b.tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        b.btnLogin.setOnClickListener {
            val email = b.etEmail.text?.toString()?.trim().orEmpty()
            val pass  = b.etPassword.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || pass.isEmpty()) {
                toast("Email & password wajib diisi"); return@setOnClickListener
            }
            setLoading(true)
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { fetchRoleAndRoute(it.user!!.uid) }
                .addOnFailureListener { e -> setLoading(false); toast(msgAuth(e)) }
        }

//        b.tvForgot.setOnClickListener {
//            val email = b.etEmail.text?.toString()?.trim().orEmpty()
//            if (email.isEmpty()) { toast("Isi email dulu"); return@setOnClickListener }
//            auth.sendPasswordResetEmail(email)
//                .addOnSuccessListener { toast("Link reset terkirim ke email") }
//                .addOnFailureListener { toast("Gagal kirim reset: ${it.message}") }
//        }
    }

    private fun fetchRoleAndRoute(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { s ->
                setLoading(false)
                val aktif = s.getBoolean("aktif") ?: false
                val role  = s.getString("role") ?: ""
                if (!aktif) { toast("Akun non-aktif"); auth.signOut(); return@addOnSuccessListener }

                when (role) {
                    "super_admin" -> {
                        startActivity(Intent(this, AdminHomeActivity::class.java))
                        // Setelah startActivity ke dashboard & sebelum finish()
                        MyFirebaseMessagingService.registerCurrentTokenIfAny()
                        finish()
                    }
                    "kasir" -> {
                        // Tandai untuk FIAM welcome di KasirHome
                        val analytics = FirebaseAnalytics.getInstance(this)
                        analytics.setUserProperty("role", "kasir")
                        analytics.logEvent("login_success_kasir", null)

                        // JANGAN trigger FIAM di sini —> pindahkan ke KasirHome (lebih stabil)
                        startActivity(
                            Intent(this, KasirHomeActivity::class.java)
                                .putExtra(EXTRA_SHOW_WELCOME, true)
                        )
                        // Setelah startActivity ke dashboard & sebelum finish()
                        MyFirebaseMessagingService.registerCurrentTokenIfAny()
                        finish()
                    }
                    else -> {
                        toast("Role tidak dikenali")
                        auth.signOut()
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast("Profil user belum ada: ${e.message}")
            }
    }

    private fun setLoading(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnLogin.isEnabled = !on
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun msgAuth(e: Exception) = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah"
        is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan"
        else -> "Login gagal: ${e.message}"
    }
}
