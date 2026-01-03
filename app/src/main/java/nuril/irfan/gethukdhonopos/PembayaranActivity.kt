// app/src/main/java/nuril/irfan/gethukdhonopos/PembayaranActivity.kt
package nuril.irfan.gethukdhonopos

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
// ==== TAMBAHAN MIDTRANS: Import ====
import com.midtrans.sdk.uikit.api.model.TransactionResult
import com.midtrans.sdk.uikit.external.UiKitApi
import com.midtrans.sdk.uikit.internal.util.UiKitConstants
// ===================================
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import nuril.irfan.gethukdhonopos.databinding.ActivityPembayaranBinding
import nuril.irfan.gethukdhonopos.model.CartItem
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID

// ==== Tambahan: OkHttp untuk call server Node ====
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PembayaranActivity : AppCompatActivity() {

    private lateinit var b: ActivityPembayaranBinding
    private val db by lazy { Firebase.firestore }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance().reference }

    private var subtotal: Long = 0L
    private var diskon: Long = 0L
    private var total: Long = 0L
    private var shiftId: String = ""
    private lateinit var items: List<CartItem>

    // ==== TAMBAHAN MIDTRANS: Variabel ====
    private lateinit var uiKitApi: UiKitApi
    // =====================================

    companion object {
        private const val TAG = "PEMBAYARAN_DEBUG"

        // GANTI IP & API KEY SESUAI SERVERMU
        private const val SERVER_BASE = "http://10.230.144.232:8080"

        // URL Backend khusus untuk minta Token Midtrans (Sesuaikan dengan backend Anda)
        // Contoh: "https://sipmpolinema.site/paymentgateway/charge/index.php" atau endpoint Node.js Anda
        private const val MIDTRANS_TOKEN_URL = "http://10.230.144.232/midtrans/index.php"

        private const val API_KEY = "rahasiamu-123"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val httpClient = OkHttpClient()
    }

    // ==== TAMBAHAN MIDTRANS: Launcher Callback ====
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getParcelableExtra<TransactionResult>(UiKitConstants.KEY_TRANSACTION_RESULT)?.let { transactionResult ->
                when (transactionResult.status) {
                    UiKitConstants.STATUS_SUCCESS -> {
                        Toast.makeText(this, "Pembayaran Berhasil!", Toast.LENGTH_LONG).show()
                        // Simpan transaksi sebagai "Midtrans" (Non-Tunai), kembalian 0
                        simpanTransaksiDanCetak(tunai = total, kembalian = 0, metodeBayar = "Midtrans")
                    }
                    UiKitConstants.STATUS_PENDING -> {
                        Toast.makeText(this, "Menunggu Pembayaran...", Toast.LENGTH_LONG).show()
                    }
                    UiKitConstants.STATUS_FAILED -> {
                        Toast.makeText(this, "Pembayaran Gagal", Toast.LENGTH_LONG).show()
                    }
                    UiKitConstants.STATUS_CANCELED -> {
                        Toast.makeText(this, "Pembayaran Dibatalkan", Toast.LENGTH_SHORT).show()
                    }
                    UiKitConstants.STATUS_INVALID -> {
                        Toast.makeText(this, "Transaksi Invalid", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // ==============================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPembayaranBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        subtotal = intent.getLongExtra("subtotal", 0L)
        diskon = intent.getLongExtra("diskon", 0L)
        total = intent.getLongExtra("total", 0L)
        shiftId = intent.getStringExtra("shift_id") ?: ""

        val cartJson = intent.getStringExtra("cart_json") ?: "[]"
        val type = object : TypeToken<List<CartItem>>() {}.type
        items = Gson().fromJson(cartJson, type)

        // ==== TAMBAHAN MIDTRANS: Init SDK ====
        initMidtransSdk()
        // =====================================

        setupList()
        setupSummary()
        setupTunaiInput()
        setupMidtransButton() // Panggil fungsi setup button
    }

    // ==== TAMBAHAN MIDTRANS: Fungsi Init ====
    private fun initMidtransSdk() {
        uiKitApi = UiKitApi.Builder()
            .withContext(applicationContext)
            .withMerchantClientKey("Mid-client-Mrj2DCDL3KGEH6Qk") // Ganti dengan Client Key Sandbox Anda [cite: 18]
            .withMerchantUrl("http://10.230.144.232/midtrans/index.php/") // Wajib diakhiri '/' [cite: 26]
            .enableLog(true)
            .build()
    }

    private fun setupMidtransButton() {
        // Pastikan Anda menambahkan Button dengan ID 'btnMidtrans' di XML
        // Jika belum ada, tambahkan di XML lalu uncomment baris ini:

         val btnMidtrans = findViewById<android.widget.Button>(R.id.btnBayarNonTunai)
         btnMidtrans?.setOnClickListener {
            if (total <= 0) {
                toast("Total 0, tidak bisa bayar")
                return@setOnClickListener
            }
            getSnapTokenAndPay()
         }
    }

    private fun getSnapTokenAndPay() {
        toast("Meminta token transaksi...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Buat Body JSON sesuai format Backend Anda/Midtrans [cite: 24]
                val orderId = "ORDER-${UUID.randomUUID()}"
                val jsonBody = JSONObject().apply {
                    put("transaction_details", JSONObject().apply {
                        put("order_id", orderId)
                        put("gross_amount", total)
                    })
                    // Tambahkan customer details jika perlu
                }

                // 2. Request Token ke Backend Anda
                val request = Request.Builder()
                    .url(MIDTRANS_TOKEN_URL)
                    .post(jsonBody.toString().toRequestBody(JSON))
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val respStr = response.body?.string()
                    val respJson = JSONObject(respStr ?: "{}")
                    val snapToken = respJson.optString("token")

                    if (snapToken.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            // 3. Buka UI Midtrans [cite: 27]
                            UiKitApi.getDefaultInstance().startPaymentUiFlow(
                                activity = this@PembayaranActivity,
                                launcher = launcher,
                                snapToken = snapToken
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) { toast("Gagal dapat token") }
                    }
                } else {
                    withContext(Dispatchers.Main) { toast("Error Server: ${response.code}") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Midtrans Error", e)
                withContext(Dispatchers.Main) { toast("Exception: ${e.message}") }
            }
        }
    }
    // ============================================

    private fun setupList() {
        b.rvItems.layoutManager = LinearLayoutManager(this)
        b.rvItems.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
                val v = layoutInflater.inflate(R.layout.item_cart_pembayaran, parent, false)
                return VH(v)
            }
            override fun getItemCount() = items.size
            override fun onBindViewHolder(holder: VH, position: Int) {
                val it = items[position]
                holder.nama.text = it.nama
                holder.qtyHarga.text = "${it.qty} x Rp %,d".format(it.hargaSatuan)
                holder.total.text = "Rp %,d".format(it.total)
            }
        }
    }
    private class VH(v: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
        val nama: android.widget.TextView = v.findViewById(R.id.tvNama)
        val qtyHarga: android.widget.TextView = v.findViewById(R.id.tvQtyHarga)
        val total: android.widget.TextView = v.findViewById(R.id.tvTotal)
    }

    private fun setupSummary() {
        val totalItem = items.sumOf { it.qty }.toInt()
        b.tvTotalItem.text = "$totalItem item"
        b.tvSubtotal.text = "Rp %,d".format(subtotal)
        b.tvDiskon.text = "Rp %,d".format(diskon)
        b.tvTotalBayar.text = "Rp %,d".format(total)
        b.tvKembalian.text = "Kembalian: Rp 0"
    }

    private fun setupTunaiInput() {
        b.etTunai.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val tunai = b.etTunai.text?.toString()?.toLongOrNull() ?: 0L
                val kembalian = (tunai - total).coerceAtLeast(0L)
                b.tvKembalian.text = "Kembalian: Rp %,d".format(kembalian)
                b.btnSelesai.isEnabled = tunai >= total
            }
        })

        b.btnSelesai.setOnClickListener {
            val tunai = b.etTunai.text?.toString()?.toLongOrNull() ?: 0L
            val kembalian = (tunai - total).coerceAtLeast(0L)
            // Default "Cash"
            simpanTransaksiDanCetak(tunai, kembalian, "Cash")
        }
    }

    // ------- model untuk notifikasi stok menipis -------
    data class LowStockAlert(
        val produkId: String,
        val nama: String,
        val stokBaru: Long,
        val minStok: Long
    )

    // ==== MODIFIKASI: Tambahkan parameter 'metodeBayar' dengan default value "Cash" ====
    // Agar kode lama tetap jalan tanpa error, tapi kita bisa inject "Midtrans" saat perlu.
    private fun simpanTransaksiDanCetak(tunai: Long, kembalian: Long, metodeBayar: String = "Cash") {
        val kasirUid = auth.currentUser?.uid ?: run { toast("User tidak login"); return }
        val trxRef = db.collection("transaksi").document()

        lifecycleScope.launch {
            try {
                // Jalankan transaksi: semua READ dulu, lalu WRITE
                val lowStockAlerts: List<LowStockAlert> = db.runTransaction { tr ->

                    // ---------- (A) READ PHASE ----------
                    // 1) Ambil user (nama kasir) dari koleksi users
                    val userRef = db.collection("users").document(kasirUid)
                    val userSnap = tr.get(userRef)
                    val namaKasir = userSnap.getString("nama")
                        ?: userSnap.getString("displayName")
                        ?: userSnap.getString("full_name")
                        ?: "(Tidak diketahui)"

                    // 2) Siapkan daftar referensi produk dan ambil semua snapshot SEKALIGUS (tanpa write)
                    data class ProdRead(val ref: com.google.firebase.firestore.DocumentReference, val snap: com.google.firebase.firestore.DocumentSnapshot)
                    val prodReads = items.map { it.produkId }
                        .map { db.collection("produk").document(it) }
                        .map { ref -> ProdRead(ref, tr.get(ref)) }

                    // 3) (opsional) Validasi shift
                    val shiftRef = db.collection("shift").document(shiftId)

                    // 4) Validasi stok
                    data class ProdWritePlan(
                        val ref: com.google.firebase.firestore.DocumentReference,
                        val nama: String,
                        val stokBaru: Long,
                        val minStok: Long,
                        val qty: Long
                    )

                    val writePlans = items.map { item ->
                        val pr = prodReads.first { it.ref.id == item.produkId }
                        if (!pr.snap.exists()) throw IllegalStateException("Produk ${item.produkId} tidak ditemukan")

                        val nama = pr.snap.getString("nama") ?: item.produkId
                        val stokNow = pr.snap.getLong("stok") ?: 0L
                        val minStok = pr.snap.getLong("min_stok") ?: 0L

                        if (stokNow < item.qty)
                            throw IllegalStateException("Stok $nama tidak cukup (tersisa $stokNow, diminta ${item.qty})")

                        ProdWritePlan(
                            ref = pr.ref,
                            nama = nama,
                            stokBaru = stokNow - item.qty,
                            minStok = minStok,
                            qty = item.qty
                        )
                    }

                    val subtotalTx = subtotal
                    val diskonTx = diskon
                    val totalTx = total

                    // Siapkan daftar alert
                    val alerts = mutableListOf<LowStockAlert>()
                    writePlans.forEach { plan ->
                        if (plan.stokBaru <= plan.minStok) {
                            alerts.add(
                                LowStockAlert(
                                    produkId = plan.ref.id,
                                    nama = plan.nama,
                                    stokBaru = plan.stokBaru,
                                    minStok = plan.minStok
                                )
                            )
                        }
                    }

                    // ---------- (B) WRITE PHASE ----------
                    // 1) Update stok produk
                    writePlans.forEach { plan ->
                        tr.update(plan.ref, mapOf(
                            "stok" to plan.stokBaru,
                            "updated_at" to FieldValue.serverTimestamp()
                        ))
                    }

                    // 2) Tulis header transaksi
                    val trxData = mapOf(
                        "tanggal" to Timestamp.now(),
                        "kasir_uid" to kasirUid,
                        "kasir_nama" to namaKasir,
                        "metode_bayar" to metodeBayar, // <<--- GUNAKAN PARAMETER DISINI
                        "subtotal" to subtotalTx,
                        "diskon" to diskonTx,
                        "total" to totalTx,
                        "tunai_diterima" to tunai,
                        "kembalian" to kembalian,
                        "status" to "selesai",
                        "shift_id" to shiftId
                    )
                    tr.set(trxRef, trxData)

                    // 3) Tulis detail_transaksi
                    items.forEach { item ->
                        val dref = trxRef.collection("detail_transaksi").document()
                        tr.set(dref, mapOf(
                            "produk_id" to item.produkId,
                            "nama" to item.nama,
                            "qty" to item.qty,
                            "harga_satuan" to item.hargaSatuan,
                            "total" to item.total
                        ))
                    }

                    // 4) Update agregat shift
                    tr.update(shiftRef, "total_penjualan", FieldValue.increment(totalTx))

                    alerts
                }.await()

                // (C) Di luar transaksi
                bumpLiveOmzet(shiftId, total)

                if (lowStockAlerts.isNotEmpty()) {
                    lowStockAlerts.forEach { notifyLowStock(it) }
                }

                showCetakDialog(trxRef.id, tunai, kembalian)

            } catch (e: Exception) {
                Log.e(TAG, "simpanTransaksiDanCetak() FAILED", e)
                toast("Gagal simpan transaksi: ${e.message}")
            }
        }
    }

    private fun bumpLiveOmzet(shiftId: String, delta: Long) {
        val ref = rtdb.child("metrics/live_omzet/$shiftId/total")
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val cur = currentData.getValue(Long::class.java) ?: 0L
                currentData.value = cur + delta
                return Transaction.success(currentData)
            }
            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                snapshot: com.google.firebase.database.DataSnapshot?
            ) {
                if (error != null) Log.e(TAG, "RTDB bump gagal: ${error.code} ${error.message}")
            }
        })
    }

    // ------- panggil server Node untuk notifikasi stok menipis -------
    private fun notifyLowStock(alert: LowStockAlert) {
        try {
            val body = JSONObject().apply {
                put("title", "Stok Menipis")
                put("body", "Produk ${alert.nama} sisa ${alert.stokBaru} (batas ${alert.minStok})")
                put("produkId", alert.produkId)
                put("stok", alert.stokBaru)
                put("min_stok", alert.minStok)
            }.toString().toRequestBody(JSON)

            val req = Request.Builder()
                .url("$SERVER_BASE/notify/lowstock")
                .addHeader("x-api-key", API_KEY)
                .post(body)
                .build()

            httpClient.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "notifyLowStock() gagal: ${e.message}", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        Log.d(TAG, "notifyLowStock() status=${it.code} resp=${it.body?.string()}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "notifyLowStock() exception", e)
        }
    }

    private fun showCetakDialog(trxId: String, tunai: Long, kembalian: Long) {
        AlertDialog.Builder(this)
            .setTitle("Transaksi Selesai ✅")
            .setMessage("Ingin mencetak struk transaksi ini?")
            .setPositiveButton("Cetak Struk") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val pdf = generateReceiptPdf(trxId, tunai, kembalian)
                        val url = uploadToSupabase(pdf, trxId)
                        db.collection("transaksi").document(trxId)
                            .update(mapOf("struk_url" to url))
                            .await()
                        toast("Struk berhasil di-upload ✅")
                        finish()
                    } catch (e: Exception) {
                        toast("Gagal cetak struk: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Selesai Tanpa Cetak") { _, _ ->
                toast("Transaksi selesai")
                finish()
            }
            .show()
    }

    private suspend fun generateReceiptPdf(trxId: String, tunai: Long, kembalian: Long): File =
        withContext(Dispatchers.IO) {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(300, 500 + items.size * 40, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply { textSize = 12f }
            var y = 30

            canvas.drawText("Gethuk Dhoho POS", 80f, y.toFloat(), paint); y += 20
            canvas.drawText("Tanggal: ${Date()}", 20f, y.toFloat(), paint); y += 10
            canvas.drawText("Kasir: ${auth.currentUser?.email ?: "-"}", 20f, y.toFloat(), paint); y += 20
            canvas.drawText("----------------------------------", 20f, y.toFloat(), paint); y += 20

            items.forEach {
                canvas.drawText(it.nama, 20f, y.toFloat(), paint); y += 15
                canvas.drawText("${it.qty} x Rp ${it.hargaSatuan} = Rp ${it.total}", 30f, y.toFloat(), paint); y += 20
            }

            canvas.drawText("----------------------------------", 20f, y.toFloat(), paint); y += 20
            canvas.drawText("Subtotal : Rp $subtotal", 20f, y.toFloat(), paint); y += 15
            canvas.drawText("Diskon   : Rp $diskon", 20f, y.toFloat(), paint); y += 15
            canvas.drawText("Total    : Rp $total", 20f, y.toFloat(), paint); y += 15
            canvas.drawText("Tunai    : Rp $tunai", 20f, y.toFloat(), paint); y += 15
            canvas.drawText("Kembalian: Rp $kembalian", 20f, y.toFloat(), paint); y += 25
            canvas.drawText("Terima kasih atas pembelian Anda!", 40f, y.toFloat(), paint)

            pdf.finishPage(page)

            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "struk")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "struk_$trxId.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            file
        }

    private suspend fun uploadToSupabase(file: File, trxId: String): String =
        withContext(Dispatchers.IO) {
            val bucket = App.supabase.storage.from("struk")
            val bytes = file.readBytes()
            val path = "pdf/$trxId.pdf"
            bucket.upload(path, bytes)
            bucket.publicUrl(path)
        }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}