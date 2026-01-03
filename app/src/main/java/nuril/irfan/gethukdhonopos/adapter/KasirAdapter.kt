package nuril.irfan.gethukdhonopos.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nuril.irfan.gethukdhonopos.databinding.RowUserKasirBinding
import nuril.irfan.gethukdhonopos.model.AppUser

class KasirAdapter(
    private val onApprove: (AppUser) -> Unit,
    private val onBlock: (AppUser) -> Unit,
    private val onDelete: (AppUser) -> Unit
) : RecyclerView.Adapter<KasirAdapter.VH>() {

    private val items = mutableListOf<AppUser>()
    fun submit(list: List<AppUser>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    inner class VH(val b: RowUserKasirBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = RowUserKasirBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val u = items[pos]
        h.b.tvNama.text = u.nama.ifBlank { "(tanpa nama)" }
        h.b.tvEmail.text = u.email
        h.b.tvStatus.text = if (u.aktif) "AKTIF" else "PENDING"
        h.b.tvStatus.setTextColor(if (u.aktif) 0xFF1B5E20.toInt() else 0xFFBF360C.toInt())

        // tombol state
        h.b.btnApprove.isEnabled = !u.aktif
        h.b.btnBlock.isEnabled   =  u.aktif

        h.b.btnApprove.setOnClickListener { onApprove(u) }
        h.b.btnBlock.setOnClickListener   { onBlock(u) }
        h.b.btnDelete.setOnClickListener  { onDelete(u) }
    }
}
