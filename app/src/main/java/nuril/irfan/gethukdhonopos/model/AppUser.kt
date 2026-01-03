package nuril.irfan.gethukdhonopos.model

data class AppUser(
    val id: String = "",
    val nama: String = "",
    val email: String = "",
    val role: String = "",
    val aktif: Boolean = false,
    val created_at: com.google.firebase.Timestamp? = null
)
