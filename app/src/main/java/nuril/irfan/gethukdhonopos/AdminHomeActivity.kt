// app/src/main/java/nuril/irfan/gethukdhonopos/AdminHomeActivity.kt
package nuril.irfan.gethukdhonopos
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig // Pastikan import ini ada
import nuril.irfan.gethukdhonopos.databinding.ActivityAdminHomeBinding
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminHomeBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }

    // Inisialisasi Remote Config
    private val remoteConfig by lazy { Firebase.remoteConfig }

    private val rtdb by lazy { FirebaseDatabase.getInstance().reference }
    private var presenceListener: ValueEventListener? = null
    private var omzetListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 1. Setup Remote Config saat aplikasi mulai
        setupRemoteConfig()

        setSupportActionBar(b.toolbar)

        b.toolbar.setNavigationOnClickListener { v ->
            val pop = PopupMenu(this, v)
            pop.menuInflater.inflate(R.menu.menu_dashboard_left, pop.menu)
            pop.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    // Tombol Refresh Theme (Seperti Button di PPT)
                    R.id.mRefreshTheme -> {
                        fetchRemoteConfig() // Panggil fungsi ambil data
                        true
                    }
                    R.id.mProduk -> { startActivity(Intent(this, AdminProdukActivity::class.java)); true }
                    R.id.mLaporan -> { startActivity(Intent(this, AdminTransaksiActivity::class.java)); true }
                    R.id.mUsers  -> { startActivity(Intent(this, AdminUsersActivity::class.java)); true }
                    R.id.chatKasir -> {
                        val intent = Intent(this, ChatActivity::class.java)
                        intent.putExtra("USER_NAME", "Super Admin")
                        startActivity(intent)
                        true
                    }
                    R.id.mLogout -> {
                        MyFirebaseMessagingService.unregisterTokenOnLogout()
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
            pop.show()
        }

        loadTodayRevenue()
        loadTodayCounts()
        loadLowStockPreview()
    }

    // --- SETUP REMOTE CONFIG ---
    private fun setupRemoteConfig() {
        // Mode Developer: Fetch interval 0 detik (agar langsung berubah saat testing) [cite: 7]
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set nilai default jika internet mati/belum fetch
        val defaultMap = mapOf(
            "dashboard_bg_color" to "#FFF7ED" // Default Putih
        )
        remoteConfig.setDefaultsAsync(defaultMap)

    }

    // --- FUNGSI FETCH & ACTIVATE (Sesuai PPT Slide 8) ---
    private fun fetchRemoteConfig() {
        b.tvOmzet.text = "Updating..." // Indikator loading kecil

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d("RemoteConfig", "Config params updated: $updated")
                    Toast.makeText(this, "Theme Berhasil Diupdate!", Toast.LENGTH_SHORT).show()

                    // Panggil fungsi ganti warna
                    updateUI()
                } else {
                    Toast.makeText(this, "Gagal update config", Toast.LENGTH_SHORT).show()
                }
                // Kembalikan teks omzet (reload ulang datanya agar teks "Updating..." hilang)
                loadTodayRevenue()
            }
    }

    // --- FUNGSI GANTI WARNA BACKGROUND ---
    private fun updateUI() {
        // Ambil string warna dari Remote Config
        val colorString = remoteConfig.getString("dashboard_bg_color")

        try {
            // Ubah warna root layout (background activity)
            b.root.setBackgroundColor(Color.parseColor(colorString))
        } catch (e: IllegalArgumentException) {
            Log.e("RemoteConfig", "Format warna salah: $colorString")
            // Fallback ke putih jika kode hex salah di console
            b.root.setBackgroundColor(Color.WHITE)
        }
    }

    override fun onStart() {
        super.onStart()
        listenPresenceRealtimeOnlyKasir()
        listenOmzetLiveRealtime()
    }

    override fun onStop() {
        super.onStop()
        presenceListener?.let { rtdb.child("presence").removeEventListener(it) }
        omzetListener?.let { rtdb.child("metrics/live_omzet").removeEventListener(it) }
        presenceListener = null
        omzetListener = null
    }

    // ... (Sisa kode loadTodayRevenue, loadTodayCounts, dll TETAP SAMA seperti kode asli Anda) ...

    private fun loadTodayRevenue() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = Timestamp(cal.time); cal.add(Calendar.DATE, 1); val end = Timestamp(cal.time)

        db.collection("transaksi")
            .whereGreaterThanOrEqualTo("tanggal", start)
            .whereLessThan("tanggal", end)
            .get()
            .addOnSuccessListener { qs ->
                val total = qs.sumOf { it.getLong("total")?.toLong() ?: 0L }
                b.tvOmzet.text = formatRupiah(total)
            }
            .addOnFailureListener { b.tvOmzet.text = "Rp –" }
    }

    private fun loadTodayCounts() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = Timestamp(cal.time); cal.add(Calendar.DATE, 1); val end = Timestamp(cal.time)

        db.collection("transaksi")
            .whereGreaterThanOrEqualTo("tanggal", start)
            .whereLessThan("tanggal", end)
            .get()
            .addOnSuccessListener { qs -> b.tvJumlahTrx.text = "${qs.size()} transaksi" }
            .addOnFailureListener { b.tvJumlahTrx.text = "- transaksi" }
    }

    private fun loadLowStockPreview() {
        db.collection("produk")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { qs ->
                val menipis = qs.documents.mapNotNull { d ->
                    val nama = d.getString("nama") ?: return@mapNotNull null
                    val stok = (d.getLong("stok") ?: 0L).toInt()
                    val min  = (d.getLong("min_stok") ?: 0L).toInt()
                    if (stok <= min) "$nama ($stok)" else null
                }
                b.tvStokMenipis.text = if (menipis.isEmpty()) "Tidak ada" else menipis.joinToString(", ")
            }
            .addOnFailureListener { b.tvStokMenipis.text = "-" }
    }

    private fun listenPresenceRealtimeOnlyKasir() {
        val ref = rtdb.child("presence").orderByChild("role").equalTo("kasir")
        presenceListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val lines = mutableListOf<String>()
                var onlineCount = 0

                for (child in snap.children) {
                    val name  = child.child("name").getValue(String::class.java) ?: child.key ?: "-"
                    val state = child.child("state").getValue(String::class.java) ?: "offline"
                    if (state == "online") onlineCount++
                    lines.add("• $name — $state")
                }

                b.tvKasirOnline.text = if (lines.isEmpty())
                    "Kasir Online: (tidak ada)"
                else
                    "Kasir Online ($onlineCount):\n" + lines.joinToString("\n")
            }
            override fun onCancelled(error: DatabaseError) {
                b.tvKasirOnline.text = "Kasir Online: (gagal muat: ${error.code})"
            }
        }
        (ref as Query).addValueEventListener(presenceListener as ValueEventListener)
    }

    private fun listenOmzetLiveRealtime() {
        val ref = rtdb.child("metrics").child("live_omzet")
        omzetListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                var totalAll = 0L
                for (shiftNode in snap.children) {
                    val t = shiftNode.child("total").getValue(Long::class.java) ?: 0L
                    totalAll += t
                }
                b.tvOmzetLive.text = "Omzet Live: " + formatRupiah(totalAll)
            }
            override fun onCancelled(error: DatabaseError) {
                b.tvOmzetLive.text = "Omzet Live: Rp –"
            }
        }
        ref.addValueEventListener(omzetListener as ValueEventListener)
    }

    private fun formatRupiah(value: Long): String {
        val localeID = Locale("in", "ID")
        val nf = NumberFormat.getCurrencyInstance(localeID)
        return nf.format(value).replace(",00", "")
    }
}