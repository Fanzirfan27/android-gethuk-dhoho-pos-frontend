package nuril.irfan.gethukdhonopos.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import nuril.irfan.gethukdhonopos.databinding.ItemProdukPosBinding
import nuril.irfan.gethukdhonopos.model.Produk

class ProdukPosAdapter(
    private val onChangeQty: (Produk, Int) -> Unit,
    private val getQty: (String) -> Long
) : RecyclerView.Adapter<ProdukPosAdapter.VH>() {

    private val items = mutableListOf<Produk>()

    fun submit(list: List<Produk>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemProdukPosBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProdukPosBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]
        h.b.tvNama.text = p.nama
        h.b.tvHarga.text = "Rp %,d".format(p.harga)
        h.b.tvStok.text  = "Stok: ${p.stok}"
        h.b.img.load(p.foto_url)

        h.b.tvQty.text = getQty(p.id).toString()

        h.b.btnPlus.setOnClickListener { onChangeQty(p, +1) }
        h.b.btnMinus.setOnClickListener { onChangeQty(p, -1) }
    }

    override fun getItemCount() = items.size
}
