package nuril.irfan.gethukdhonopos

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayErrorListener
import com.google.firebase.inappmessaging.FirebaseInAppMessagingImpressionListener
import com.google.firebase.inappmessaging.model.InAppMessage
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.databinding.ActivityKasirHomeBinding

class KasirHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityKasirHomeBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }
    private val rtdb by lazy { FirebaseDatabase.getInstance().reference }
    private val analytics by lazy { FirebaseAnalytics.getInstance(this) }

    private var activeShiftId: String? = null
    private var shouldShowWelcome = false
    private var welcomeShownThisLaunch = false

    companion object {
        private const val TAG = "KasirHome"
        private const val TAG_FIAM = "FIAM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKasirHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        Log.d(TAG, "onCreate()")

        // FIAM aktif + listener log (opsional)
        FirebaseInAppMessaging.getInstance().apply {
            setMessagesSuppressed(false)
            isAutomaticDataCollectionEnabled = true

            addImpressionListener(object : FirebaseInAppMessagingImpressionListener {
                override fun impressionDetected(message: InAppMessage) {
                    Log.d(TAG_FIAM, "IMPRESSION campaign=${message.campaignName}")
                }
            })
            addDisplayErrorListener(object : FirebaseInAppMessagingDisplayErrorListener {
                override fun displayErrorEncountered(
                    message: InAppMessage,
                    error: FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason
                ) {
                    Log.e(TAG_FIAM, "ERROR campaign=${message.campaignName} reason=$error")
                }
            })
        }

        // Ambil bendera dari Login untuk show welcome FIAM
        shouldShowWelcome = intent.getBooleanExtra(LoginActivity.EXTRA_SHOW_WELCOME, false)
        Log.d(TAG_FIAM, "shouldShowWelcome=$shouldShowWelcome")

        // Tombol
        b.btnOpenShift.setOnClickListener { showOpenShiftDialog() }
        b.btnCloseShift.setOnClickListener { showCloseShiftDialog() }
        b.btnPos.setOnClickListener {
            if (activeShiftId == null) toast("Buka shift terlebih dahulu!")
            else startActivity(Intent(this, PosActivity::class.java).putExtra("SHIFT_ID", activeShiftId))
        }
        b.btnTiktok.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtra("URL_TARGET", "https://www.tiktok.com/@bimoy.co?_r=1&_t=ZS-92TjmWPUUpZ")
            startActivity(intent)
        }
        b.btnChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)

            // Mengirim data identitas ke ChatActivity
            // Karena ini halaman Kasir, kita set namanya "Kasir" atau nama user dari Auth
            val namaPengirim = auth.currentUser?.displayName ?: "Kasir"
            intent.putExtra("USER_NAME", namaPengirim)

            // (Opsional) Jika ChatActivity Anda butuh UID untuk skema database 'chats'
            intent.putExtra("USER_UID", auth.currentUser?.uid)

            startActivity(intent)
        }
        b.btnMapsToko.setOnClickListener {
            startActivity(Intent(this, MapsTokoActivity::class.java))
        }
        b.btnLogoutKasir.setOnClickListener {
            PresenceManager.setActiveShiftId(null)
            PresenceManager.setOfflineNow {
                MyFirebaseMessagingService.unregisterTokenOnLogout()
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart()")

        lifecycleScope.launchWhenStarted {
            PresenceManager.ensureOnDisconnectIfKasir()
            PresenceManager.setOnlineWhenConnectedIfKasir()
        }

        // Tampilkan FIAM welcome (kalau diminta) setelah Activity ter-attach


        refreshShiftStatus()
    }
    override fun onPostResume() {
        super.onPostResume()
        maybeShowWelcomeFiam()
    }


    /** FIAM welcome — trigger event SETELAH Activity siap */
    private fun maybeShowWelcomeFiam() {
        if (!shouldShowWelcome || welcomeShownThisLaunch) return
        welcomeShownThisLaunch = true

        // Pastikan FIAM sudah bind ke Activity; beri jeda 1500ms
        b.root.postDelayed({
            Log.d(TAG_FIAM, "Trigger FIAM & Analytics: kasir_login_ok")

            // 1) Programmatic trigger ke FIAM
            FirebaseInAppMessaging.getInstance().triggerEvent("kasir_login_ok")

            // 2) Log juga ke Analytics (wajib kalau campaign di console tipe “Analytics event”)
            analytics.logEvent("kasir_login_ok", null)

        }, 1500L)
    }


    // ====== STATUS & METRIK SHIFT ======
    private fun refreshShiftStatus() {
        val uid = auth.currentUser?.uid ?: return
        Log.d(TAG, "refreshShiftStatus() uid=$uid")

        db.collection("shift")
            .whereEqualTo("kasir_uid", uid)
            .whereEqualTo("status", "open")
            .orderBy("mulai", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                Log.d(TAG, "refreshShiftStatus() result size=${qs.size()}")
                if (qs.isEmpty) {
                    activeShiftId = null
                    b.tvShiftStatus.text = "Status: Belum dibuka"
                    b.tvShiftInfo.text = "Buka shift untuk mulai transaksi"
                    b.btnOpenShift.visibility = View.VISIBLE
                    b.btnCloseShift.visibility = View.GONE
                    b.cardRingkasanShift.visibility = View.GONE
                    PresenceManager.setActiveShiftId(null)
                } else {
                    val doc = qs.documents.first()
                    activeShiftId = doc.id
                    val mulai = doc.getTimestamp("mulai")?.toDate()
                    val modal = doc.getLong("modal_awal") ?: 0L

                    Log.d(TAG, "Shift ACTIVE id=$activeShiftId mulai=$mulai modal=$modal")

                    b.tvShiftStatus.text = "Status: AKTIF"
                    b.tvShiftInfo.text = "Mulai: $mulai\nModal Awal: Rp %,d".format(modal)
                    b.btnOpenShift.visibility = View.GONE
                    b.btnCloseShift.visibility = View.VISIBLE
                    b.cardRingkasanShift.visibility = View.VISIBLE

                    PresenceManager.setActiveShiftId(activeShiftId)
                    loadShiftMetrics(activeShiftId!!)

                    // init live_omzet jika belum ada
                    rtdb.child("metrics/live_omzet/$activeShiftId/total").get()
                        .addOnSuccessListener { s ->
                            if (!s.exists()) {
                                Log.d(TAG, "Init live_omzet for shift=$activeShiftId")
                                rtdb.child("metrics/live_omzet/$activeShiftId")
                                    .setValue(mapOf("total" to 0L, "kasir_uid" to uid))
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "refreshShiftStatus() FAILED: ${e.message}", e)
                toast("Gagal memuat shift: ${e.message}")
            }
    }

    private fun loadShiftMetrics(shiftId: String) {
        val uid = auth.currentUser?.uid ?: return
        Log.d(TAG, "loadShiftMetrics() shiftId=$shiftId uid=$uid")

        db.collection("shift").document(shiftId).get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("status") ?: "-"
                val modalAwal = doc.getLong("modal_awal") ?: 0L
                val cashAkhir = doc.getLong("cash_akhir")
                b.tvModalAwal.text = "Modal Awal: Rp %,d".format(modalAwal)

                db.collection("transaksi")
                    .whereEqualTo("shift_id", shiftId)
                    .whereEqualTo("kasir_uid", uid)
                    .get()
                    .addOnSuccessListener { qs ->
                        val jumlahTrx = qs.size()
                        val totalOmzet = qs.sumOf { it.getLong("total")?.toLong() ?: 0L }

                        Log.d(TAG, "metrics jumlahTrx=$jumlahTrx totalOmzet=$totalOmzet status=$status")

                        b.tvTotalTransaksiShift.text = "Jumlah Transaksi: $jumlahTrx"
                        b.tvOmzetShift.text = "Omzet Shift: Rp %,d".format(totalOmzet)

                        if (status == "open") {
                            val estimasi = modalAwal + totalOmzet
                            b.tvEstimasiCashSaatIni.visibility = View.VISIBLE
                            b.tvCashAkhir.text = "Cash Akhir: -"
                            b.tvSelisih.text = "Selisih: -"
                            b.tvEstimasiCashSaatIni.text =
                                "Estimasi Cash Saat Ini: Rp %,d (modal + omzet)".format(estimasi)
                        } else {
                            b.tvEstimasiCashSaatIni.visibility = View.GONE
                            val ca = cashAkhir ?: 0L
                            val selisih = ca - (modalAwal + totalOmzet)
                            b.tvCashAkhir.text = "Cash Akhir: Rp %,d".format(ca)
                            b.tvSelisih.text = "Selisih: Rp %,d".format(selisih)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "loadShiftMetrics() trx FAILED: ${e.message}", e)
                        b.tvTotalTransaksiShift.text = "Jumlah Transaksi: -"
                        b.tvOmzetShift.text = "Rp –"
                        b.tvEstimasiCashSaatIni.text = "Estimasi Cash Saat Ini: -"
                        b.tvCashAkhir.text = "Cash Akhir: -"
                        b.tvSelisih.text = "Selisih: -"
                    }
            }
    }

    // ==== Aksi: buka / tutup shift ====
    private fun showOpenShiftDialog() {
        if (activeShiftId != null) { toast("Shift sudah aktif"); return }
        val input = EditText(this).apply {
            hint = "Modal awal (Rp)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Buka Shift")
            .setMessage("Masukkan modal awal kasir hari ini")
            .setView(input)
            .setPositiveButton("Buka") { _, _ ->
                val modal = input.text.toString().toLongOrNull() ?: 0L
                if (modal <= 0) toast("Isi modal awal dengan benar!") else openShift(modal)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openShift(modalAwal: Long) {
        val uid = auth.currentUser?.uid ?: return
        Log.d(TAG, "openShift(modal=$modalAwal) uid=$uid")

        val data = hashMapOf(
            "kasir_uid" to uid,
            "mulai" to Timestamp.now(),
            "selesai" to null,
            "modal_awal" to modalAwal,
            "total_penjualan" to 0L,
            "cash_akhir" to null,
            "selisih" to null,
            "status" to "open"
        )
        db.collection("shift").add(data)
            .addOnSuccessListener { doc ->
                activeShiftId = doc.id
                Log.d(TAG, "openShift() SUCCESS id=$activeShiftId")
                toast("Shift berhasil dibuka (modal: Rp %,d)".format(modalAwal))

                PresenceManager.setActiveShiftId(activeShiftId)
                rtdb.child("metrics/live_omzet/$activeShiftId")
                    .setValue(mapOf("total" to 0L, "kasir_uid" to uid))

                refreshShiftStatus()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "openShift() FAILED: ${e.message}", e)
                toast("Gagal buka shift: ${e.message}")
            }
    }

    private fun showCloseShiftDialog() {
        val shiftId = activeShiftId ?: run { toast("Belum ada shift aktif"); return }
        val input = EditText(this).apply {
            hint = "Cash akhir (Rp)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Tutup Shift")
            .setMessage("Masukkan jumlah uang kasir saat menutup shift")
            .setView(input)
            .setPositiveButton("Tutup") { _, _ ->
                val cashAkhir = input.text.toString().toLongOrNull() ?: 0L
                if (cashAkhir <= 0) toast("Isi nominal dengan benar!") else closeShift(shiftId, cashAkhir)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun closeShift(shiftId: String, cashAkhir: Long) {
        val uid = auth.currentUser?.uid ?: return
        Log.d(TAG, "closeShift(shiftId=$shiftId, cashAkhir=$cashAkhir) uid=$uid")

        db.collection("transaksi")
            .whereEqualTo("shift_id", shiftId)
            .whereEqualTo("kasir_uid", uid)
            .get()
            .addOnSuccessListener { qs ->
                val totalPenjualan = qs.sumOf { it.getLong("total")?.toLong() ?: 0L }
                Log.d(TAG, "closeShift() totalPenjualan=$totalPenjualan")

                db.collection("shift").document(shiftId).get()
                    .addOnSuccessListener { doc ->
                        val modalAwal = doc.getLong("modal_awal") ?: 0L
                        val selisih = cashAkhir - (modalAwal + totalPenjualan)
                        val updates = mapOf(
                            "selesai" to Timestamp.now(),
                            "cash_akhir" to cashAkhir,
                            "total_penjualan" to totalPenjualan,
                            "selisih" to selisih,
                            "status" to "closed"
                        )
                        db.collection("shift").document(shiftId).update(updates)
                            .addOnSuccessListener {
                                Log.d(TAG, "closeShift() SUCCESS selisih=$selisih")
                                toast("Shift ditutup (Selisih: Rp %,d)".format(selisih))

                                PresenceManager.setActiveShiftId(null)
                                rtdb.child("metrics/live_omzet/$shiftId").removeValue()
                                activeShiftId = null
                                refreshShiftStatus()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "closeShift() update FAILED: ${e.message}", e)
                                toast("Gagal update shift: ${e.message}")
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "closeShift() hitung penjualan FAILED: ${e.message}", e)
                toast("Gagal hitung penjualan: ${e.message}")
            }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
