package nuril.irfan.gethukdhonopos
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import nuril.irfan.gethukdhonopos.databinding.ActivityMapsTokoBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapsTokoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsTokoBinding
    private lateinit var mLocationOverlay: MyLocationNewOverlay // [cite: 60]

    // Lokasi Toko Gethuk (Hardcode)
    private val lokasiToko = GeoPoint(-7.8172286, 112.1092159)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Konfigurasi osmdroid [cite: 64]
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = ActivityMapsTokoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkLocationPermission() // Cek izin lokasi dulu [cite: 67]
        setupMap()

        // Tombol untuk memaksa update rute & jarak
        binding.fabRoute.setOnClickListener {
            hitungJarakDanGambarGaris()
        }
    }

    private fun setupMap() {
        // Setup dasar peta
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK) // [cite: 79]
        binding.mapView.setMultiTouchControls(true) // [cite: 80]
        binding.mapView.controller.setZoom(15.0) // [cite: 81]

        // --- 1. SETUP LOKASI USER (LIVE UPDATE) ---
        // Menggunakan GpsMyLocationProvider seperti di PDF hal 6 [cite: 82-84]
        val provider = GpsMyLocationProvider(this)
        provider.addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)

        mLocationOverlay = MyLocationNewOverlay(provider, binding.mapView)
        mLocationOverlay.enableMyLocation() // Aktifkan lokasi [cite: 96]
        mLocationOverlay.enableFollowLocation() // Ikuti pergerakan user [cite: 97]

        // Ketika lokasi pertama kali ditemukan, langsung hitung jarak
        mLocationOverlay.runOnFirstFix { // [cite: 98]
            runOnUiThread {
                // Animasikan peta ke tengah antara user dan toko
                val myLoc = mLocationOverlay.myLocation
                if (myLoc != null) {
                    binding.mapView.controller.animateTo(myLoc) // [cite: 102]
                    hitungJarakDanGambarGaris()
                }
            }
        }

        // Tambahkan overlay lokasi user ke peta [cite: 106]
        binding.mapView.overlays.add(mLocationOverlay)

        // --- 2. MARKER TOKO ---
        addMarkerToko(lokasiToko, "Toko Gethuk Pisang", "Pusat Oleh-oleh")
    }

    // Fungsi menggambar garis & hitung jarak (Implementasi Polyline PDF hal 11)
    private fun hitungJarakDanGambarGaris() {
        val userLoc = mLocationOverlay.myLocation

        if (userLoc == null) {
            Toast.makeText(this, "Menunggu lokasi GPS Anda...", Toast.LENGTH_SHORT).show()
            return
        }

        // A. HITUNG JARAK
        // GeoPoint memiliki fungsi built-in distanceToAsDouble (hasil dalam meter)
        val jarakMeter = userLoc.distanceToAsDouble(lokasiToko)
        val jarakKm = jarakMeter / 1000.0
        binding.tvJarak.text = "Jarak ke Toko: %.2f km".format(jarakKm)

        // B. GAMBAR GARIS (POLYLINE)
        // Bersihkan garis lama jika ada (opsional, agar tidak menumpuk)
        // Disini kita hapus overlay selain Marker Toko dan MyLocationOverlay
        // (Sederhananya kita tumpuk saja atau manajemen list overlay lebih lanjut)

        val line = Polyline() // [cite: 194]
        line.title = "Rute ke Toko" // [cite: 195]
        line.color = Color.BLUE // [cite: 196]
        line.width = 5.0f // [cite: 197]

        // Tambahkan titik User dan Titik Toko ke dalam garis [cite: 199-201]
        val points = ArrayList<GeoPoint>()
        points.add(userLoc)      // Titik A (Lokasi Anda misal Surabaya)
        points.add(lokasiToko)   // Titik B (Toko)
        line.setPoints(points)

        // Tambahkan garis ke peta [cite: 202]
        binding.mapView.overlays.add(line)
        binding.mapView.invalidate() // Refresh [cite: 203]

        // Zoom out sedikit agar garis terlihat utuh
        binding.mapView.controller.zoomTo(10.0)
    }

    private fun addMarkerToko(geoPoint: GeoPoint, title: String, snippet: String) {
        val marker = Marker(binding.mapView) // [cite: 127]
        marker.position = geoPoint
        marker.title = title
        marker.snippet = snippet
        marker.icon = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) // [cite: 130]
        marker.setOnMarkerClickListener { m, _ ->
            m.showInfoWindow()
            true
        }
        binding.mapView.overlays.add(marker)
    }

    // Fungsi Cek Izin [cite: 120-123]
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }
    }

    // Lifecycle Management [cite: 222-238]
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (this::mLocationOverlay.isInitialized) {
            mLocationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (this::mLocationOverlay.isInitialized) {
            mLocationOverlay.disableMyLocation()
        }
    }
}