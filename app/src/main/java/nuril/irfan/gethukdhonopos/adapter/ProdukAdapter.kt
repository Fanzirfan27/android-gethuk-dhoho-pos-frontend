package nuril.irfan.gethukdhonopos.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import nuril.irfan.gethukdhonopos.databinding.ItemProdukBinding

import nuril.irfan.gethukdhonopos.model.Produk

class ProdukAdapter(
    private val onToggleActive: (Produk, Boolean) -> Unit,
    private val onClick: (Produk) -> Unit,
    private val onLongClick: (Produk) -> Unit,
    private val onDelete: (Produk) -> Unit
) : ListAdapter<Produk, ProdukAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Produk>() {
            override fun areItemsTheSame(o: Produk, n: Produk) = o.id == n.id
            override fun areContentsTheSame(o: Produk, n: Produk) = o == n
        }
    }

    inner class VH(val b: ItemProdukBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProdukBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = getItem(pos)
        h.b.tvNama.text = p.nama
        h.b.tvHarga.text = "Rp %,d".format(p.harga)
        h.b.tvStok.text  = "Stok: ${p.stok}"
        h.b.img.load(p.foto_url)
        h.b.swActive.setOnCheckedChangeListener(null)
        h.b.swActive.isChecked = p.active
        h.b.swActive.setOnCheckedChangeListener { _, checked -> onToggleActive(p, checked) }
        h.b.root.setOnClickListener { onClick(p) }
        h.b.root.setOnLongClickListener { onLongClick(p); true }
        h.b.btnDelete.setOnClickListener { onDelete(p) }
    }
}
