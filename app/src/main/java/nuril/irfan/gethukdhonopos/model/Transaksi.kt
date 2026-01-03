package nuril.irfan.gethukdhonopos.model
import com.google.firebase.Timestamp

data class Transaksi(
    val id: String = "",
    val tanggal: Timestamp? = null,
    val kasir_uid: String = "",
    val metode_bayar: String = "",
    val subtotal: Long = 0,
    val diskon: Long = 0,
    val total: Long = 0,
    val tunai_diterima: Long = 0,
    val kembalian: Long = 0,
    val status: String = "",
    val shift_id: String = ""
)
