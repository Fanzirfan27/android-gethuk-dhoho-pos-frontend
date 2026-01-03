package nuril.irfan.gethukdhonopos

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
abstract class BaseActivity : AppCompatActivity() {

    protected fun applyRemoteConfig(root: View) {
        val rc = FirebaseRemoteConfig.getInstance()

        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()

        rc.setConfigSettingsAsync(settings)

        rc.setDefaultsAsync(
            mapOf(
                "font_color" to "#000000",
                "font_size" to "16"
            )
        )

        rc.fetchAndActivate().addOnCompleteListener {
            try {
                val color = rc.getString("font_color")
                val size  = rc.getString("font_size").toFloatOrNull() ?: 16f

                applyFontRecursively(root, color, size)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyFontRecursively(view: View, color: String, size: Float) {
        if (view is TextView) {
            try {
                view.setTextColor(Color.parseColor(color))
                view.textSize = size
            } catch (e: Exception) {
                // ignore invalid color
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontRecursively(view.getChildAt(i), color, size)
            }
        }
    }
}
