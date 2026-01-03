package nuril.irfan.gethukdhonopos.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nuril.irfan.gethukdhonopos.databinding.RowItemTransaksiBinding
import nuril.irfan.gethukdhonopos.model.TransaksiItem

class TransaksiItemAdapter : RecyclerView.Adapter<TransaksiItemAdapter.VH>() {
    private val items = mutableListOf<TransaksiItem>()
    fun submit(list: List<TransaksiItem>) { items.setAll(list); notifyDataSetChanged() }
    private fun <T> MutableList<T>.setAll(list: List<T>) { clear(); addAll(list) }

    inner class VH(val b: RowItemTransaksiBinding) : RecyclerView.ViewHolder(b.root)
    private fun rp(v: Long) = "%,d".format(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = RowItemTransaksiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, p: Int) {
        val it = items[p]
        h.b.tvNama.text = it.nama
        h.b.tvQtyHarga.text = "${it.qty} x ${rp(it.harga_satuan)}"
        h.b.tvTotal.text = rp(it.total)
    }
}
