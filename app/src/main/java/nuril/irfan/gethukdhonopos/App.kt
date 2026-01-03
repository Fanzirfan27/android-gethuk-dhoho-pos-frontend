// app/src/main/java/nuril/irfan/gethukdhonopos/App.kt
package nuril.irfan.gethukdhonopos

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    object SupabaseConfig {
        const val URL = "https://xlatsxuhakppluqyoihc.supabase.co"
        const val ANON =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhsYXRzeHVoYWtwcGx1cXlvaWhjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk4MDY0OTQsImV4cCI6MjA3NTM4MjQ5NH0.XYUBbYCSfW22t2APEwkA0Fug6FN25Jn1tUIcu9Hbl1s"
    }

    companion object {
        lateinit var supabase: SupabaseClient
            private set
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val appScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Init Supabase
        supabase = createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON
        ) { install(Storage) }

        // Presence mengikuti perubahan auth (login/logout / switch akun)
        auth.addAuthStateListener {
            // reset cache PresenceManager ketika user berubah
            PresenceManager.clearCache()

            val user = auth.currentUser
            if (user != null) {
                // Hanya set presence untuk role "kasir"
                appScope.launch {
                    PresenceManager.ensureOnDisconnectIfKasir()
                    PresenceManager.setOnlineWhenConnectedIfKasir()
                }
            } else {
                // Logout: biarkan onDisconnect yg handle (state -> offline)
                // (Jika ingin paksa offline cepat saat tombol logout ditekan,
                //  lakukan di screen yg memanggil signOut: PresenceManager.setOfflineNow { auth.signOut() } )
            }
        }

        // Optional: saat app kembali ke foreground, cepat tandai online (khusus kasir)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                if (auth.currentUser != null) {
                    appScope.launch {
                        PresenceManager.ensureOnDisconnectIfKasir()
                        PresenceManager.setOnlineWhenConnectedIfKasir()
                    }
                }
            }
            override fun onActivityStopped(activity: Activity) { started-- }

            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
