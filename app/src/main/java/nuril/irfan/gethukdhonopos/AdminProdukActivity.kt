package nuril.irfan.gethukdhonopos

import nuril.irfan.gethukdhonopos.adapter.ProdukAdapter
import nuril.irfan.gethukdhonopos.databinding.ActivityAdminProdukBinding
import nuril.irfan.gethukdhonopos.databinding.DialogProdukBinding
import nuril.irfan.gethukdhonopos.model.Produk
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class AdminProdukActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminProdukBinding
    private val db by lazy { Firebase.firestore }
    private val col get() = db.collection("produk")
    enum class ProdukSort {
        NAMA_ASC,
        NAMA_DESC,
        HARGA_ASC,
        HARGA_DESC,
        STOK_ASC,
        STOK_DESC
    }

    private var currentSort = ProdukSort.NAMA_ASC
    private var lastProduk: List<Produk> = emptyList()

    private val adapter by lazy {
        ProdukAdapter(
            onToggleActive = { p, checked -> toggleActive(p, checked) },
            onClick = { p -> showProdukDialog(editing = p) },
            onLongClick = { p -> showDeleteDialog(p) },
            onDelete = { p -> showDeleteDialog(p) }
        )
    }

    // ==== Image Picker pakai OpenDocument (aman untuk Photos/Drive) ====
    private var pickedImageUri: Uri? = null
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pickedImageUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminProdukBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Setup Toolbar
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        // RecyclerView
        b.rvProduk.layoutManager = LinearLayoutManager(this)
        b.rvProduk.adapter = adapter

        // Tombol tambah produk
        b.fabAdd.setOnClickListener { showProdukDialog(null) }
        b.toolbar.setOnLongClickListener {
            showProdukSortDialog()
            true
        }


        // Listen realtime
        col.addSnapshotListener { qs, e ->
            if (e != null) return@addSnapshotListener

            val list = qs?.documents?.map { d ->
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
            } ?: emptyList()

            lastProduk = list
            adapter.submitList(applyProdukSort(list))
        }

    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_produk, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showProdukSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyProdukSort(data: List<Produk>): List<Produk> {
        return when (currentSort) {
            ProdukSort.NAMA_ASC -> data.sortedBy { it.nama.lowercase() }
            ProdukSort.NAMA_DESC -> data.sortedByDescending { it.nama.lowercase() }

            ProdukSort.HARGA_ASC -> data.sortedBy { it.harga }
            ProdukSort.HARGA_DESC -> data.sortedByDescending { it.harga }

            ProdukSort.STOK_ASC -> data.sortedBy { it.stok }
            ProdukSort.STOK_DESC -> data.sortedByDescending { it.stok }
        }
    }
    private fun showProdukSortDialog() {
        val items = arrayOf(
            "Nama (A - Z)",
            "Nama (Z - A)",
            "Harga (Termurah)",
            "Harga (Termahal)",
            "Stok (Sedikit)",
            "Stok (Banyak)"
        )

        AlertDialog.Builder(this)
            .setTitle("Urutkan Produk")
            .setItems(items) { _, which ->
                currentSort = when (which) {
                    0 -> ProdukSort.NAMA_ASC
                    1 -> ProdukSort.NAMA_DESC
                    2 -> ProdukSort.HARGA_ASC
                    3 -> ProdukSort.HARGA_DESC
                    4 -> ProdukSort.STOK_ASC
                    5 -> ProdukSort.STOK_DESC
                    else -> ProdukSort.NAMA_ASC
                }
                adapter.submitList(applyProdukSort(lastProduk))
            }
            .show()
    }



    private fun toggleActive(p: Produk, checked: Boolean) {
        col.document(p.id).update("active", checked)
            .addOnFailureListener { toast("Gagal ubah status: ${it.message}") }
    }

    private fun showDeleteDialog(p: Produk) {
        AlertDialog.Builder(this)
            .setTitle("Hapus produk?")
            .setMessage("Produk: ${p.nama}\nTindakan ini tidak bisa dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                col.document(p.id).delete()
                    .addOnSuccessListener { toast("Produk terhapus") }
                    .addOnFailureListener { toast("Gagal hapus: ${it.message}") }
            }
            .setNegativeButton("Batal", null)
            .show()
    }


    // ==== Dialog Tambah / Ubah Produk ====
    private fun showProdukDialog(editing: Produk?) {
        pickedImageUri = null
        val vb = DialogProdukBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editing == null) "Tambah Produk" else "Ubah Produk")
            .setView(vb.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        // Prefill data jika mode edit
        editing?.let { p ->
            vb.etNama.setText(p.nama)
            vb.etHarga.setText(p.harga.toString())
            vb.etStok.setText(p.stok.toString())
            vb.etSku.setText(p.sku)
            vb.etMinStok.setText(p.min_stok.toString())
            vb.swActive.isChecked = p.active
            vb.ivPreview.load(p.foto_url)
        }

        vb.btnPilihGambar.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val nama = vb.etNama.text?.toString()?.trim().orEmpty()
                val harga = vb.etHarga.text?.toString()?.toLongOrNull() ?: 0L
                val stok = vb.etStok.text?.toString()?.toLongOrNull() ?: 0L
                val sku = vb.etSku.text?.toString()?.trim().orEmpty()
                val minStok = vb.etMinStok.text?.toString()?.toLongOrNull() ?: 0L
                val active = vb.swActive.isChecked

                if (nama.isEmpty() || harga <= 0) {
                    toast("Nama & harga wajib diisi!")
                    return@setOnClickListener
                }

                setSaving(vb, dialog, true)

                lifecycleScope.launch {
                    try {
                        val fotoUrl = try {
                            if (pickedImageUri != null) {
                                vb.ivPreview.setImageURI(pickedImageUri)
                                uploadToSupabase(pickedImageUri!!)
                            } else editing?.foto_url ?: ""
                        } catch (e: Exception) {
                            ""
                        }

                        val data = hashMapOf(
                            "nama" to nama,
                            "harga" to harga,
                            "stok" to stok,
                            "sku" to sku,
                            "foto_url" to fotoUrl,
                            "active" to active,
                            "min_stok" to minStok
                        )

                        if (editing == null) {
                            col.add(data).await()
                        } else {
                            col.document(editing.id).update(data as Map<String, Any>).await()
                        }

                        toast("Produk tersimpan!")
                        dialog.dismiss()
                    } catch (e: Exception) {
                        toast("Gagal simpan: ${e.message}")
                        setSaving(vb, dialog, false)
                    }
                }
            }
        }

        dialog.show()
    }

    // ==== Upload ke Supabase Storage ====
    private suspend fun uploadToSupabase(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Tidak bisa membuka file (cek izin/sumber)")

        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "images/${UUID.randomUUID()}.$ext"
        val bucket = App.supabase.storage.from("produk")
        bucket.upload(path, bytes)
        bucket.publicUrl(path)
    }

    private fun setSaving(vb: DialogProdukBinding, dialog: AlertDialog, saving: Boolean) {
        val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btnSave.isEnabled = !saving
        vb.btnPilihGambar.isEnabled = !saving
        vb.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
