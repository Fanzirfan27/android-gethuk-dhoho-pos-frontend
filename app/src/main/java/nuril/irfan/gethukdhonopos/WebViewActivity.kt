package nuril.irfan.gethukdhonopos

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val url = intent.getStringExtra("URL_TARGET") ?: "https://www.tiktok.com"

        setupWebView()
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wbSet = webView.settings

        // --- Pengaturan Wajib untuk TikTok Web ---
        // Mengacu pada referensi pengaturan WebView [cite: 240, 241]
        wbSet.javaScriptEnabled = true           // [cite: 246]
        wbSet.domStorageEnabled = true           // [cite: 244]
        wbSet.displayZoomControls = false        // [cite: 244]
        wbSet.useWideViewPort = true             // [cite: 245]
        wbSet.allowFileAccess = true             // [cite: 251]
        wbSet.allowContentAccess = true          // [cite: 252]
        wbSet.loadsImagesAutomatically = true    // [cite: 253]
        wbSet.databaseEnabled = true

        // --- Manipulasi User Agent ---
        // Agar TikTok mengira ini browser Chrome biasa & tidak memaksa buka aplikasi
        wbSet.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        wbSet.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // --- Cookies (Penting untuk Login/Sesi) ---
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // --- Hardware Acceleration (Agar video tidak lag) ---
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // --- PENCEGAHAN BUKA APLIKASI TIKTOK ---
        webView.webViewClient = object : WebViewClient() { // [cite: 254]
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // 1. Jika link adalah HTTP atau HTTPS (Web biasa), IZINKAN muat di WebView
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false // false = WebView, silakan proses URL ini
                }

                // 2. Jika link adalah skema lain (snssdk1180://, intent://, dll)
                // KITA BLOKIR TOTAL.
                // return true artinya: "Saya sudah menanganinya (dengan tidak melakukan apa-apa)".
                // Ini mencegah error "Unknown URL Scheme" DAN mencegah aplikasi TikTok terbuka.
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    // Navigasi Back agar tidak langsung keluar aplikasi
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { // [cite: 302]
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { // [cite: 304, 305]
            webView.goBack() // [cite: 306]
            return true // [cite: 307]
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}