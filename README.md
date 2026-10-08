# AeroCine

Aplikasi kamera video sinematik tingkat rendah (Low-Level Cinema Video Engine) untuk perangkat Android berarsitektur ARM64, dioptimalkan khusus untuk karakteristik sensor dan silikon MediaTek Dimensity 7400 Ultimate (Infinix Note 60 5G).

---

## 1. Arsitektur dan Prinsip Kerja

AeroCine dibangun untuk melenyapkan keterbatasan pemrosesan bawaan pabrik (kompresi agresif, efek cat air pada denoise, over-sharpening digital buatan, dan lonjakan zoom) melalui integrasi subsistem native Android:

```
+-----------------------------------------------------------------------------+
|                               UI / PRESENTATION                             |
|  CameraScreen -> ViewfinderSurface -> ZoomControlDock -> ShutterTrigger    |
+-----------------------------------------------------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------------+
|                           STATE / LOGIC LAYER                               |
|       CameraViewModel  <--->  ZoomSpringEngine (Critically Damped 60Hz)     |
+-----------------------------------------------------------------------------+
               |                                              |
               v                                              v
+-----------------------------+               +-------------------------------+
|      SERVICE / CAMERA       |               |       SENSOR TELEMETRY        |
| CameraEngine (Camera2 API)  |               | GyroTelemetryEngine (200 Hz)  |
| - PREVIEW_STABILIZATION     |               | - ASensorManager IMU Logger   |
| - CONTROL_ZOOM_RATIO        |               | - Gyroflow Compatible CSV     |
| - EDGE_MODE_OFF             |               +-------------------------------+
| - NOISE_REDUCTION_FAST      |                               |
+-----------------------------+                               |
               |                                              |
               v                                              |
+---------------------------------------------------------+   |
|                 RENDER & ENCODING LAYER                 |   |
| CameraSurfaceRenderer (GLES 3.0 + AgX Tone Curve)       |   |
| HighBitrateMediaEncoder (HEVC 80-100 Mbps + MediaMuxer) | <-+
+---------------------------------------------------------+
```

---

## 2. Tiga Pilar Rekayasa Utama

### A. Pipeline Video Super HD (Bypass Algoritma Murahan)
* **High-Bitrate HEVC (H.265):** Perekaman menggunakan hardware encoder MediaCodec yang dikunci pada bitrate **80 Mbps hingga 100 Mbps** (4x lipat dari kamera standar ~20 Mbps). Menghilangkan kompresi makroblok pada objek bergerak.
* **Sensor Tuning Murni:** Mematikan artefak penajaman garis putih buatan (`CaptureRequest.EDGE_MODE = EDGE_MODE_OFF`) dan denoise cat air (`CaptureRequest.NOISE_REDUCTION_MODE = NOISE_REDUCTION_MODE_FAST`).
* **OpenGL ES 3.0 AgX Tonemap:** Transformasi warna berbasis kurva logaritmik AgX untuk melindungi area highlight dari pemotongan (clipping) warna putih mendadak serta menjaga saturasi alami kulit dan langit.

### B. Stabilisasi Video Kokoh (Hardware-Synchronized)
* **Camera2 Preview Stabilization (API 33+):** Mengaktifkan mode `CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION` yang mengoordinasikan OIS perangkat keras dengan kompensasi gerak elektronik berpemotongan tetap (~15%). Menghilangkan anomali gerak jeli (*rolling shutter wobble*).
* **High-Frequency Gyroscope Logging (200 Hz):** Merekam telemetri vektor rotasi sudut IMU berstempel waktu nanodetik ke dalam berkas pendamping `.csv`. Berkas ini dapat langsung diimpor ke aplikasi Gyroflow Android jika diperlukan stabilisasi setingkat gimbal pascaproduksi.

### C. Mesin Zoom Halus Berbobot Inersia (iOS Spring Physics)
* **Model Pegas Teredam Kritis (Critically Damped Spring):**
  $$\ddot{z}(t) + 2\zeta\omega_n \dot{z}(t) + \omega_n^2 (z(t) - z_{\text{target}}) = 0$$
  Dengan $\zeta = 1.0$ dan $\omega_n = 16.0$ rad/s, pergeseran zoom memiliki inersia fisik alami saat cubitan jari dilepas dan berhenti tanpa sentakan.
* **Presisi Float `CONTROL_ZOOM_RATIO`:** Memanfaatkan API Android 11+ untuk transisi skala pembesaran kontinu tanpa pemotongan piksel kasar.

---

## 3. Struktur Direktori Proyek

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/aerocine/camera/
│   │   │   ├── MainActivity.kt
│   │   │   ├── model/
│   │   │   │   ├── VideoConfig.kt
│   │   │   │   └── CameraState.kt
│   │   │   ├── core/
│   │   │   │   ├── camera/CameraEngine.kt
│   │   │   │   ├── encoder/HighBitrateMediaEncoder.kt
│   │   │   │   ├── gl/
│   │   │   │   │   ├── AgXShader.kt
│   │   │   │   │   └── CameraSurfaceRenderer.kt
│   │   │   │   ├── sensor/GyroTelemetryEngine.kt
│   │   │   │   └── spring/ZoomSpringEngine.kt
│   │   │   └── ui/
│   │   │       ├── CameraViewModel.kt
│   │   │       ├── CameraScreen.kt
│   │   │       ├── theme/Theme.kt
│   │   │       └── components/
│   │   │           ├── ViewfinderSurface.kt
│   │   │           ├── ZoomControlDock.kt
│   │   │           ├── TopSettingsBar.kt
│   │   │           └── ShutterTrigger.kt
│   │   └── res/
├── .github/workflows/build-apk.yml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

---

## 4. Cara Membangun Proyek (Build Instructions)

### Opsi A: Otomasi CI/CD (GitHub Actions)
Lakukan push repository ini ke GitHub. Alur kerja `.github/workflows/build-apk.yml` akan secara otomatis mengompilasi dan mengunggah berkas APK ke GitHub Actions Artifacts serta mirror unduhan berkecepatan tinggi.

### Opsi B: Android Studio Lokal
1. Buka folder `android/` di Android Studio (Hedgehog / Jellyfish / Ladybug).
2. Tunggu proses Gradle Sync selesai.
3. Pilih menu **Build -> Build APK(s)** atau jalankan `./gradlew assembleDebug`.
4. Berkas APK biner siap dipasang pada perangkat target.
