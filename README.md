# Android App (Kotlin)

Aplikasi **Android (Kotlin)** untuk sistem **POS**, terintegrasi dengan:

* Firebase Cloud Messaging (FCM) – push notification
* Firebase Authentication
* Firestore
* Midtrans (via Backend PHP)

Aplikasi ini **tidak menyimpan secret** di client dan seluruh proses sensitif dilakukan di backend.

---

## Fitur Utama

* Login & Register (Firebase Auth)
* Simpan & sinkron data user (Firestore)
* Terima notifikasi stok menipis (FCM)
* Registrasi & unregistrasi token FCM ke backend Node.js
* Proses pembayaran Midtrans via backend PHP

---

## Struktur Project

```
app/
│
├── src/main/java/nuril/irfan/gethukdhonopos/
│   ├── ui/                    # Activity / Fragment UI
│   ├── data/                  # Model & helper
│   ├── network/               # API / OkHttp
│   ├── MyFirebaseMessagingService.kt
│   └── App.kt
│
├── res/
│   ├── layout/
│   ├── drawable/
│   └── values/
│
├── AndroidManifest.xml
└── build.gradle
```

---

## Konfigurasi Firebase

1. Buat project di **Firebase Console**
2. Tambahkan Android App
3. Download `google-services.json`
4. Letakkan di:

```
app/google-services.json
```
---

## Firebase Cloud Messaging (FCM)

### Service

File utama:

```
MyFirebaseMessagingService.kt
```

Fungsi:

* Menerima token FCM
* Kirim token ke backend Node.js
* Menampilkan notifikasi saat pesan masuk

### Konstanta Backend

```kotlin
private const val BASE = "http://IP-SERVER:8080"
private const val API_KEY = "rahasiamu-123"
```

> Sesuaikan IP backend (LAN / VPS)

---

## Midtrans Payment Flow

Alur pembayaran:

```
Android App
   ↓
Backend PHP (Midtrans)
   ↓
Midtrans Snap
```

Android **tidak pernah menyimpan Server Key**.

---

## Menjalankan Aplikasi

1. Clone repository
2. Buka dengan **Android Studio**
3. Sync Gradle
4. Jalankan di emulator / device

Minimal Android:

```
minSdkVersion 23
```


## Testing

* Login / Register
* Terima notifikasi FCM
* Trigger pembayaran Midtrans

---

## Author

**Muhammad Irfan Nuril Anwar**
Android & Backend Developer

---

## Catatan

Project ini dibuat untuk:

* Portfolio
* Sistem POS / Inventory

