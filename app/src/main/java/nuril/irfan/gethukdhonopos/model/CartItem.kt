package nuril.irfan.gethukdhonopos.model

data class CartItem(
    val produkId: String,
    val nama: String,
    val hargaSatuan: Long,
    var qty: Long = 0
) {
    val total: Long get() = hargaSatuan * qty
}