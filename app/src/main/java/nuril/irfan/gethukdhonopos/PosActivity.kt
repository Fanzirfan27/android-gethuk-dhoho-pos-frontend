package nuril.irfan.gethukdhonopos

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import nuril.irfan.gethukdhonopos.adapter.ProdukPosAdapter
import nuril.irfan.gethukdhonopos.databinding.ActivityPosBinding
import nuril.irfan.gethukdhonopos.model.CartItem
import nuril.irfan.gethukdhonopos.model.Produk

class PosActivity : AppCompatActivity() {

    private lateinit var b: ActivityPosBinding
    private val db by lazy { Firebase.firestore }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var adapter: ProdukPosAdapter
    private val cart = linkedMapOf<String, CartItem>() // key = produkId

    private var shiftId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPosBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        // Ambil shift aktif dari intent
        shiftId = intent.getStringExtra("SHIFT_ID")
        if (shiftId.isNullOrBlank()) {
            toast("Shift tidak ditemukan")
            finish()
            return
        }

        // Siapkan RecyclerView & Adapter produk
        adapter = ProdukPosAdapter(
            onChangeQty = { p, delta -> changeQty(p, delta) },
            getQty = { id -> cart[id]?.qty ?: 0L }
        )
        b.rvProduk.layoutManager = LinearLayoutManager(this)
        b.rvProduk.adapter = adapter

        // Event pencarian
        b.etCari.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadProduk(v.text?.toString()?.trim().orEmpty())
                true
            } else false
        }

        // Tombol Bayar → buka PembayaranActivity (biar Pembayaran yang simpan transaksi & cetak)
        b.btnBayar.setOnClickListener {
            if (cart.isEmpty()) {
                toast("Keranjang kosong")
                return@setOnClickListener
            }

            val subtotal = cart.values.sumOf { it.total }
            val diskon = 0L
            val total = subtotal - diskon

            val gson = Gson()
            val intent = Intent(this, PembayaranActivity::class.java).apply {
                putExtra("subtotal", subtotal)
                putExtra("diskon", diskon)
                putExtra("total", total)
                putExtra("shift_id", shiftId)
                putExtra("cart_json", gson.toJson(cart.values.toList()))
            }
            startActivity(intent)
        }

        // Muat produk & ringkasan awal
        loadProduk("")
        redrawSummary()
    }

    // 🔹 Muat daftar produk aktif
    private fun loadProduk(query: String) {
        db.collection("produk")
            .whereEqualTo("active", true)
            .orderBy("nama", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { qs ->
                val all = qs.documents.map { d ->
                    Produk(
                        id = d.id,
                        nama = d.getString("nama") ?: "",
                        harga = d.getLong("harga") ?: 0L,
                        stok = d.getLong("stok") ?: 0L,
                        sku = d.getString("sku") ?: "",
                        foto_url = d.getString("foto_url") ?: "",
                        active = d.getBoolean("active") ?: true,
                        min_stok = d.getLong("min_stok") ?: 0L
                    )
                }
                val filtered = if (query.isBlank()) all
                else all.filter { it.nama.contains(query, true) || it.sku.contains(query, true) }
                adapter.submit(filtered)
            }
            .addOnFailureListener { toast("Gagal memuat produk: ${it.message}") }
    }

    // 🔹 Tambah / kurangi item di keranjang
    private fun changeQty(p: Produk, delta: Int) {
        val current = cart[p.id]?.qty ?: 0L
        val newQty = (current + delta).coerceAtLeast(0L)
        if (newQty > p.stok) {
            toast("Stok tidak cukup!")
            return
        }
        if (newQty == 0L) cart.remove(p.id) else cart[p.id] = CartItem(p.id, p.nama, p.harga, newQty)
        adapter.notifyDataSetChanged()
        redrawSummary()
    }

    // 🔹 Update ringkasan total di bawah
    private fun redrawSummary() {
        val itemCount = cart.values.sumOf { it.qty }.toInt()
        val subtotal = cart.values.sumOf { it.total }
        val diskon = 0L
        val total = subtotal - diskon

        b.tvItemCount.text = "$itemCount item"
        b.tvSubtotal.text = "Rp %,d".format(subtotal)
        b.tvDiskon.text = "Rp %,d".format(diskon)
        b.tvTotal.text = "Rp %,d".format(total)

        b.btnBayar.isEnabled = cart.isNotEmpty()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
