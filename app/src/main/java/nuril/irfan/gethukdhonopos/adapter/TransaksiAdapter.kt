package nuril.irfan.gethukdhonopos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.databinding.RowTransaksiBinding
import nuril.irfan.gethukdhonopos.model.Transaksi
import java.text.SimpleDateFormat
import java.util.*

class TransaksiAdapter(
    private val onLihat: (Transaksi) -> Unit,
    private val onHapus: (Transaksi) -> Unit
) : RecyclerView.Adapter<TransaksiAdapter.VH>() {

    private val items = mutableListOf<Transaksi>()
    fun submit(list: List<Transaksi>) { items.setAll(list); notifyDataSetChanged() }
    private fun <T> MutableList<T>.setAll(list: List<T>) { clear(); addAll(list) }

    inner class VH(val b: RowTransaksiBinding) : RecyclerView.ViewHolder(b.root)

    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in","ID"))
    private fun rupiah(v: Long) = "Rp %,d".format(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = RowTransaksiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    val firestore = Firebase.firestore

    override fun onBindViewHolder(h: VH, p: Int) {
        val t = items[p]
        val tgl = t.tanggal?.toDate()

        h.b.tvNotaTanggal.text = "TRX-${t.id.takeLast(6)} • ${if (tgl != null) sdf.format(tgl) else "-"}"
        h.b.tvTotal.text = "Total: ${rupiah(t.total)}"

        // Ambil nama kasir dari koleksi users
        firestore.collection("users").document(t.kasir_uid).get()
            .addOnSuccessListener { doc ->
                val nama = doc.getString("nama") ?: "(Tidak diketahui)"
                h.b.tvKasirShift.text = "Kasir: $nama • Shift: ${t.shift_id.takeLast(6)}"
            }
            .addOnFailureListener {
                h.b.tvKasirShift.text = "Kasir: ${t.kasir_uid} • Shift: ${t.shift_id.takeLast(6)}"
            }

        h.b.btnLihat.setOnClickListener { onLihat(t) }
        h.b.btnHapus.setOnClickListener { onHapus(t) }
    }

}
