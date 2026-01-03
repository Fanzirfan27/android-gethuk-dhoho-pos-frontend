package nuril.irfan.gethukdhonopos.model

data class TransaksiItem(
    val id: String = "",
    val produk_id: String = "",
    val nama: String = "",
    val qty: Long = 0,
    val harga_satuan: Long = 0,
    val total: Long = 0
)
