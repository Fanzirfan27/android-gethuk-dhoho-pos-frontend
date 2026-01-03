// app/src/main/java/nuril/irfan/gethukdhonopos/MyFirebaseMessagingService.kt
package nuril.irfan.gethukdhonopos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token = $token")
        // Jika user sudah login → register ke server & simpan ke Firestore
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            registerTokenAsync(uid, token)      // kirim ke server Node
            saveTokenToFirestore(uid, token)    // simpan juga di Firestore
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: "Notifikasi"
        val body  = msg.notification?.body  ?: msg.data["body"]  ?: ""
        val type  = msg.data["type"] ?: ""

        ensureChannel()

        // Ketika notifikasi diketuk → arahkan ke AdminHome (bisa kamu ganti)
        val tapIntent = Intent(this, AdminHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NOTIF_TYPE", type)
            putExtra("PRODUK_ID", msg.data["produkId"])
        }
        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(this, 1001, tapIntent, flags)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // sediakan vector icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
        } else {
            Log.w(TAG, "Notifications disabled by user.")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Pemberitahuan stok menipis & info penting" }
            m.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "alerts"

        // ====== GANTI sesuai server kamu ======
        private const val BASE = "http://10.116.217.232:8080" // contoh: http://IP-LAPTOP:8080
        private const val API_KEY = "rahasiamu-123"         // harus sama dgn server .env

        private val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()

        /**
         * Panggil SETELAH login sukses (mis. di LoginActivity) supaya token aktif terdaftar.
         */
        fun registerCurrentTokenIfAny() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                registerTokenAsync(uid, token)      // ke server
                saveTokenToFirestore(uid, token)    // ke Firestore
            }
        }

        /**
         * Panggil saat LOGOUT agar token device ini tidak lagi menerima push utk user tsb.
         */
        fun unregisterTokenOnLogout() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val json = JSONObject().apply { put("uid", uid); put("token", token) }
                val req = Request.Builder()
                    .url("$BASE/fcm/unregister")
                    .addHeader("x-api-key", API_KEY)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        Log.e(TAG, "Unregister token failed: ${e.message}")
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.close()
                        Log.d(TAG, "Unregister token OK")
                    }
                })
            }
        }

        /** ===== util: kirim token ke server Node ===== */
        private fun registerTokenAsync(uid: String, token: String) {
            val json = JSONObject().apply { put("uid", uid); put("token", token) }
            val req = Request.Builder()
                .url("$BASE/fcm/register")
                .addHeader("x-api-key", API_KEY)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "Register token failed: ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                    Log.d(TAG, "Register token OK")
                }
            })
        }

        /** ===== util: simpan token di Firestore (fcm_token & fcm_tokens[]) ===== */
        private fun saveTokenToFirestore(uid: String, token: String) {
            val db = Firebase.firestore
            val ref = db.collection("users").document(uid)
            // pastikan dokumen ada
            ref.set(mapOf("uid" to uid, "updated_at" to Timestamp.now()), SetOptions.merge())
            // simpan token terakhir & daftar token unik
            ref.set(mapOf(
                "fcm_token" to token,
                "fcm_tokens" to FieldValue.arrayUnion(token)
            ), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Token saved to Firestore") }
                .addOnFailureListener { e -> Log.e(TAG, "Save token failed: ${e.message}", e) }
        }
    }
}
