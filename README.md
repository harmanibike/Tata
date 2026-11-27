# Asisten O3

**Asisten O3** adalah aplikasi asisten suara Android native berbasis Kotlin untuk perangkat arm64, dengan perintah lokal cepat dan fitur online saat internet tersedia.

## Identitas aplikasi
- Nama aplikasi: Asisten O3
- Application ID: `com.harmanibike.voiceo3`
- Platform: Android native (Kotlin)
- Target perangkat uji: Redmi 14C, ABI `arm64-v8a`
- Minimum Android: API 26
- Compile/target SDK: 34
- JDK: 17

## Fitur yang sudah disiapkan
- Pengenalan suara Android dengan preferensi bahasa Indonesia (`id-ID`) dan mode offline.
- Text-to-Speech bahasa Indonesia.
- Tombol Bicara dan log percakapan.
- Perintah lokal untuk mengetahui jam dan tanggal.
- Perintah membuka WhatsApp, YouTube, kamera, dan aplikasi catatan yang tersedia.
- KnowledgeStore berbasis file `knowledge.json` di `filesDir`, dengan data awal alamat Harmani Bike: Jln. Pancasila No. 5, Sukosewu.
- Pemeriksaan koneksi tervalidasi dan pengambilan harga PAXGUSDT dari Binance secara asynchronous pada dispatcher IO.

## Build APK
Workflow GitHub Actions berjalan saat push ke branch `main` atau melalui `workflow_dispatch`.

Perintah build:
```bash
gradle assembleDebug --no-daemon
```

Artifact:
`app/build/outputs/apk/debug/app-debug.apk`

## Catatan
- SpeechRecognizer memakai `EXTRA_PREFER_OFFLINE=true`, tetapi ketersediaan pengenalan suara offline bahasa Indonesia bergantung pada layanan dan paket bahasa yang terpasang di perangkat.
- Harga PAXGUSDT membutuhkan internet dan dapat gagal jika API tidak tersedia.
- Aplikasi meminta izin mikrofon saat fitur Bicara digunakan.
- Build saat ini difokuskan untuk ABI arm64-v8a agar sesuai target Redmi 14C.
