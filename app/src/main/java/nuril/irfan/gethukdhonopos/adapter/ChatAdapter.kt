package nuril.irfan.gethukdhonopos

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import nuril.irfan.gethukdhonopos.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale

// Constructor menerima currentUserId (UID user yang sedang login)
class ChatAdapter(private val currentUserId: String, private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.txvPengirim)
        val tvMessage: TextView = view.findViewById(R.id.txvChat)
        val layoutBubble: LinearLayout = view.findViewById(R.id.layoutBubble)
        val parentLayout: LinearLayout = view.findViewById(R.id.parentLayout) // ID baru di XML row
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rowdata_simplechat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        // 1. Format Waktu
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = message.timestamp.toDate().let { timeFormat.format(it) }

        // 2. Set Text: Tampilkan "Nama - Role • Jam"
        // Contoh: "Irfan - Kasir • 10:30"
        holder.tvSender.text = "${message.senderName} - ${message.senderRole} • $timeString"
        holder.tvMessage.text = message.pesan

        // 3. LOGIKA POSISI (KANAN / KIRI)
        val params = holder.layoutBubble.layoutParams as LinearLayout.LayoutParams

        if (message.senderUid == currentUserId) {
            // --- INI PESAN SAYA (KANAN) ---
            holder.parentLayout.gravity = Gravity.END // Container rata kanan
            holder.tvSender.gravity = Gravity.END     // Text nama rata kanan

            // Opsional: Ganti warna bubble jadi Ungu (sesuai screenshot Anda)
            // Pastikan Anda punya drawable/color resource, atau pakai hardcode color sementara:
            holder.layoutBubble.setBackgroundColor(android.graphics.Color.parseColor("#E1BEE7")) // Ungu muda
        } else {
            // --- INI PESAN ORANG LAIN (KIRI) ---
            holder.parentLayout.gravity = Gravity.START // Container rata kiri
            holder.tvSender.gravity = Gravity.START     // Text nama rata kiri

            // Warna abu-abu
            holder.layoutBubble.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
        }

        holder.layoutBubble.layoutParams = params
    }

    override fun getItemCount() = messages.size
}