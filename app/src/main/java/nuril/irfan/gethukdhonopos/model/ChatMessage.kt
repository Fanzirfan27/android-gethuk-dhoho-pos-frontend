package nuril.irfan.gethukdhonopos.model

import com.google.firebase.Timestamp
data class ChatMessage(
    val id: String = "",
    val pesan: String = "",
    val senderUid: String = "",   // ID Unik User (Auth UID)
    val senderName: String = "",  // Nama Asli (misal: Irfan)
    val senderRole: String = "",  // Role (misal: Kasir)
    val timestamp: Timestamp = Timestamp.now()
)