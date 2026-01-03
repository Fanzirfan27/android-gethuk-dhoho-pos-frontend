package nuril.irfan.gethukdhonopos

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.adapter.KasirAdapter
import nuril.irfan.gethukdhonopos.databinding.ActivityAdminUsersBinding
import nuril.irfan.gethukdhonopos.model.AppUser
import java.util.Locale

class AdminUsersActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminUsersBinding
    private val db by lazy { Firebase.firestore }
    private lateinit var adapter: KasirAdapter
    private var lastKeyword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = KasirAdapter(
            onApprove = { setAktif(it, true) },
            onBlock   = { setAktif(it, false) },
            onDelete  = { confirmDelete(it) }
        )
        b.rvUsers.layoutManager = LinearLayoutManager(this)
        b.rvUsers.adapter = adapter

        b.etCari.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lastKeyword = v.text?.toString()?.trim().orEmpty()
                loadUsers()
                true
            } else false
        }
        b.btnRefresh.setOnClickListener { loadUsers() }

        loadUsers()
    }

    private fun setLoading(on: Boolean) {
        b.progress.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
        b.tvEmpty.visibility = android.view.View.GONE
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun loadUsers() {
        setLoading(true)
        db.collection("users")
            .whereEqualTo("role", "kasir")
            .orderBy("aktif", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { qs ->
                var list = qs.documents.map { d ->
                    AppUser(
                        id = d.id,
                        nama = d.getString("nama") ?: "",
                        email = d.getString("email") ?: "",
                        role = d.getString("role") ?: "",
                        aktif = d.getBoolean("aktif") ?: false,
                        created_at = d.getTimestamp("created_at")
                    )
                }
                if (lastKeyword.isNotBlank()) {
                    val kw = lastKeyword.lowercase(Locale.ROOT)
                    list = list.filter {
                        it.email.lowercase(Locale.ROOT).contains(kw) ||
                                it.nama.lowercase(Locale.ROOT).contains(kw)
                    }
                }
                adapter.submit(list)
                setLoading(false)
                b.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast("Gagal memuat pengguna: ${e.message}")
            }
    }

    private fun setAktif(u: AppUser, aktif: Boolean) {
        db.collection("users").document(u.id).update("aktif", aktif)
            .addOnSuccessListener {
                toast(if (aktif) "Kasir di-approve" else "Kasir diblokir")
                loadUsers()
            }
            .addOnFailureListener { e -> toast("Gagal update: ${e.message}") }
    }

    private fun confirmDelete(u: AppUser) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Kasir?")
            .setMessage("Hapus profil ${u.email}? Akun Auth tidak terhapus, tetapi tidak bisa login karena profil dihapus.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("users").document(u.id).delete()
                    .addOnSuccessListener { toast("Profil dihapus"); loadUsers() }
                    .addOnFailureListener { e -> toast("Gagal hapus: ${e.message}") }
            }
            .show()
    }
}
