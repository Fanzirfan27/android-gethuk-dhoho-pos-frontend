// app/src/main/java/nuril/irfan/gethukdhonopos/AdminTransaksiDetailActivity.kt
package nuril.irfan.gethukdhonopos

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.adapter.TransaksiItemAdapter
import nuril.irfan.gethukdhonopos.databinding.ActivityAdminTransaksiDetailBinding
import nuril.irfan.gethukdhonopos.model.TransaksiItem
import java.text.SimpleDateFormat
import java.util.*

class AdminTransaksiDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminTransaksiDetailBinding
    private val db by lazy { Firebase.firestore }

    private lateinit var adapter: TransaksiItemAdapter
    private var trxId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminTransaksiDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Toolbar back
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        // Ambil ID transaksi dari intent
        trxId = intent.getStringExtra("TRX_ID") ?: ""
        if (trxId.isBlank()) {
            toast("ID transaksi tidak ditemukan")
            finish()
            return
        }

        // Nota singkat
        b.tvNota.text = "TRX-${trxId.takeLast(6)}"

        // Siapkan RecyclerView
        adapter = TransaksiItemAdapter()
        b.rvItems.layoutManager = LinearLayoutManager(this)
        b.rvItems.adapter = adapter

        // Muat header + items
        loadHeader()
        loadItems()
    }

    /** ------ Firestore: Header transaksi ------ **/
    private fun loadHeader() {
        db.collection("transaksi").document(trxId).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    toast("Transaksi tidak ada"); finish(); return@addOnSuccessListener
                }

                val tanggal: Timestamp? = d.getTimestamp("tanggal")
                val kasirUid: String = d.getString("kasir_uid") ?: "-"
                val metode: String = d.getString("metode_bayar") ?: "-"
                val subtotal: Long = d.getLong("subtotal") ?: 0L
                val diskon: Long = d.getLong("diskon") ?: 0L
                val total: Long = d.getLong("total") ?: 0L
                val tunai: Long = d.getLong("tunai_diterima") ?: 0L
                val kembalian: Long = d.getLong("kembalian") ?: 0L

                b.tvTanggal.text = tanggal.toIndoDateTime()
                b.tvMetode.text = "Metode: $metode"
                b.tvSubtotal.text = "Subtotal: Rp ${rp(subtotal)}"
                b.tvDiskon.text = "Diskon: Rp ${rp(diskon)}"
                b.tvTotal.text = "Total: Rp ${rp(total)}"
                b.tvTunaiKembali.text = "Tunai: Rp ${rp(tunai)} • Kembalian: Rp ${rp(kembalian)}"

                // Ambil nama kasir dari koleksi users
                if (kasirUid != "-") {
                    db.collection("users").document(kasirUid).get()
                        .addOnSuccessListener { userDoc ->
                            val namaKasir = userDoc.getString("nama")
                                ?: userDoc.getString("displayName")
                                ?: userDoc.getString("full_name")
                                ?: "(Tidak diketahui)"
                            b.tvKasir.text = "Kasir: $namaKasir"
                        }
                        .addOnFailureListener {
                            b.tvKasir.text = "Kasir: $kasirUid"
                        }
                } else {
                    b.tvKasir.text = "Kasir: -"
                }
            }
            .addOnFailureListener {
                toast("Gagal memuat header: ${it.message}")
            }
    }


    /** ------ Firestore: Items transaksi ------ **/
    private fun loadItems() {
        db.collection("transaksi").document(trxId).collection("detail_transaksi")
            .get()
            .addOnSuccessListener { qs ->
                val list = qs.documents.map { d ->
                    TransaksiItem(
                        id = d.id,
                        produk_id = d.getString("produk_id") ?: "",
                        nama = d.getString("nama") ?: "",
                        qty = d.getLong("qty") ?: 0L,
                        harga_satuan = d.getLong("harga_satuan") ?: 0L,
                        total = d.getLong("total") ?: 0L
                    )
                }
                adapter.submit(list)
            }
            .addOnFailureListener {
                toast("Gagal memuat items: ${it.message}")
            }
    }

    /** ------ Utils ------ **/
    private fun rp(v: Long) = "%,d".format(v).replace(',', '.')
    private fun Timestamp?.toIndoDateTime(): String {
        if (this == null) return "-"
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))
        return df.format(this.toDate())
    }
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
