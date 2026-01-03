package nuril.irfan.gethukdhonopos

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import nuril.irfan.gethukdhonopos.databinding.ActivityChatBinding
import nuril.irfan.gethukdhonopos.model.ChatMessage

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance() // Tambahan Auth

    private val chatCollection = db.collection("chats")
    private val messagesList = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    // Variabel untuk menyimpan data user saya sendiri
    private var myUid: String = ""
    private var myName: String = "User"
    private var myRole: String = "Role"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Ambil UID User yang login
        myUid = auth.currentUser?.uid ?: ""

        if (myUid.isEmpty()) {
            Toast.makeText(this, "User tidak terautentikasi", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Ambil Data Detail User (Nama & Role) dari Firestore 'users'
        fetchMyProfileData()

        // 3. Setup RecyclerView (Kirim UID ke adapter)
        setupRecyclerView()

        // 4. Load Pesan
        observeMessages()

        // 5. Tombol Kirim
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun fetchMyProfileData() {
        // Mengambil data dari koleksi 'users' berdasarkan UID [cite: 1]
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    myName = document.getString("nama") ?: "Tanpa Nama"
                    myRole = document.getString("role") ?: "User"

                    // Update judul toolbar biar keren
                    binding.textView11.text = "Chat Room ($myRole)"
                }
            }
            .addOnFailureListener {
                Log.e("ChatActivity", "Gagal ambil profil", it)
            }
    }

    private fun setupRecyclerView() {
        // Masukkan myUid ke constructor Adapter
        chatAdapter = ChatAdapter(myUid, messagesList)
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun sendMessage() {
        val text = binding.edtChat.text.toString().trim()
        if (text.isNotEmpty()) {
            // Simpan data lengkap ke database agar history tetap benar meski user ganti nama nanti
            val newMessage = hashMapOf(
                "pesan" to text,
                "senderUid" to myUid,      // ID User untuk cek posisi kanan/kiri
                "senderName" to myName,    // Nama untuk ditampilkan
                "senderRole" to myRole,    // Role untuk ditampilkan
                "timestamp" to Timestamp.now()
            )

            chatCollection.add(newMessage)
                .addOnSuccessListener {
                    binding.edtChat.text.clear()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal kirim: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun observeMessages() {
        chatCollection
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }

                if (snapshots != null) {
                    messagesList.clear()
                    for (doc in snapshots) {
                        val data = doc.data
                        val pesan = data["pesan"] as? String ?: ""
                        val uid = data["senderUid"] as? String ?: ""
                        val name = data["senderName"] as? String ?: "Unknown"
                        val role = data["senderRole"] as? String ?: ""
                        val time = data["timestamp"] as? Timestamp ?: Timestamp.now()

                        messagesList.add(ChatMessage(doc.id, pesan, uid, name, role, time))
                    }
                    chatAdapter.notifyDataSetChanged()
                    if (messagesList.isNotEmpty()) {
                        binding.rvChat.scrollToPosition(messagesList.size - 1)
                    }
                }
            }
    }
}