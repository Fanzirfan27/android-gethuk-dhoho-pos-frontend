package nuril.irfan.gethukdhonopos.model

data class Produk(
    var id: String = "",
    var nama: String = "",
    var harga: Long = 0L,
    var stok: Long = 0L,
    var sku: String = "",
    var foto_url: String = "",
    var active: Boolean = true,
    var min_stok: Long = 0L
)
