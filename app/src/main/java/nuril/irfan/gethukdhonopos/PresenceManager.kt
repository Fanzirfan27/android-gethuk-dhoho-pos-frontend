// app/src/main/java/nuril/irfan/gethukdhonopos/PresenceManager.kt
package nuril.irfan.gethukdhonopos

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object PresenceManager {

    private const val TAG = "PresenceManager"

    private val auth get() = FirebaseAuth.getInstance()
    private val rtdb get() = FirebaseDatabase.getInstance().reference
    private val fs   get() = Firebase.firestore

    // quick getter supaya gak error "uid tak didefinisikan"
    private val uid: String? get() = auth.currentUser?.uid

    private var onDisconnectSetForUid: String? = null
    private var cachedRole: String? = null
    private var cachedName: String? = null

    /** Ambil role & name dari users/{uid} (cache supaya hemat call) */
    private suspend fun ensureUserProfile(): Pair<String?, String?> {
        val u = uid ?: return null to null
        if (cachedRole != null && cachedName != null) return cachedRole to cachedName

        return try {
            val doc = fs.collection("users").document(u).get().await()
            cachedRole = doc.getString("role")             // "kasir" | "super_admin" | dst
            cachedName = doc.getString("name")
                ?: auth.currentUser?.email ?: u
            cachedRole to cachedName
        } catch (e: Exception) {
            Log.w(TAG, "Gagal load profile users/$u: ${e.message}")
            // fallback: role null biar tidak menulis presence
            null to (auth.currentUser?.email ?: u)
        }
    }

    /** Set hook onDisconnect hanya kalau role == kasir (dipanggil saat kasir screen aktif) */
    suspend fun ensureOnDisconnectIfKasir() {
        val u = uid ?: return
        val (role, _) = ensureUserProfile()
        if (role != "kasir") return
        if (onDisconnectSetForUid == u) return

        val node = rtdb.child("presence").child(u)
        node.child("state").onDisconnect().setValue("offline")
        node.child("last_seen").onDisconnect().setValue(ServerValue.TIMESTAMP)
        node.child("active_shift_id").onDisconnect().setValue(null)
        node.child("role").onDisconnect().setValue(null)
        node.child("name").onDisconnect().setValue(null)

        onDisconnectSetForUid = u
        Log.d(TAG, "onDisconnect set for uid=$u")
    }

    /** Tandai ONLINE + tulis name/role (khusus kasir). Panggil dari KasirHomeActivity.onStart() */
    suspend fun setOnlineWhenConnectedIfKasir() {
        val u = uid ?: return
        val (role, name) = ensureUserProfile()
        if (role != "kasir") return

        ensureOnDisconnectIfKasir()

        FirebaseDatabase.getInstance().getReference(".info/connected")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.getValue(Boolean::class.java) != true) {
                        Log.w(TAG, ".info/connected == false")
                        return
                    }
                    val node = rtdb.child("presence").child(u)
                    node.updateChildren(
                        mapOf(
                            "state" to "online",
                            "last_seen" to ServerValue.TIMESTAMP,
                            // active_shift_id dikelola dari KasirHomeActivity
                            "role" to "kasir",
                            "name" to (name ?: "Kasir")
                        )
                    ) { err, _ ->
                        if (err != null) Log.e(TAG, "set online failed: ${err.code} ${err.message}")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, ".info/connected cancelled: ${error.toException()}")
                }
            })
    }

    /** Paksa offline sekarang (dipakai saat Logout/Tutup Aplikasi manual) */
    fun setOfflineNow(onDone: (() -> Unit)? = null) {
        val u = uid
        if (u == null) {
            onDone?.invoke()
            return
        }
        val node = rtdb.child("presence").child(u)
        // Prioritaskan state -> offline dulu
        node.child("state").setValue("offline").addOnCompleteListener {
            onDone?.invoke()
        }
        node.child("last_seen").setValue(ServerValue.TIMESTAMP)
        node.child("active_shift_id").setValue(null)
        // (role & name biarkan; tidak wajib dihapus saat manual offline)
    }

    /** Diisi/dikosongkan dari KasirHomeActivity ketika buka/tutup shift */
    fun setActiveShiftId(shiftId: String?) {
        val u = uid ?: return
        rtdb.child("presence").child(u).child("active_shift_id").setValue(shiftId)
    }

    /** Reset cache saat user berubah (panggil saat AuthState berubah) */
    fun clearCache() {
        cachedRole = null
        cachedName = null
        onDisconnectSetForUid = null
    }
}
