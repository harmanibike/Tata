# Tata AI

**Tata AI** adalah aplikasi asisten suara Android native berbasis Kotlin untuk perangkat arm64, dengan perintah lokal cepat dan fitur online saat internet tersedia.

### Cadangan Pengetahuan Tata
- Tombol **CADANGAN PENGETAHUAN JSON** membuka pilihan ekspor atau impor.
- Ekspor menyimpan `pengetahuan-tata.json` ke lokasi yang dipilih melalui pemilih file Android.
- Impor membaca file JSON, memeriksa struktur serta pertanyaan/jawaban wajib, lalu mengganti daftar pengetahuan di perangkat dengan isi file cadangan.
- Pilih file JSON cadangan yang benar saat impor. Proses impor dibatasi maksimal 2 MB.
- Cadangan dapat dipindahkan ke perangkat lain secara manual; data tidak otomatis tersinkron antarperangkat.

## Identitas aplikasi
- Nama aplikasi: Tata AI
- Application ID: `com.harmanibike.voiceo3`
- Platform: Android native (Kotlin)
- Target perangkat uji: Redmi 14C, ABI `arm64-v8a`
- Minimum Android: API 26
- Compile/target SDK: 34
- JDK: 17

## Fitur yang tersedia
- Pengenalan suara Android dengan preferensi bahasa Indonesia (`id-ID`) dan preferensi offline; ketersediaannya bergantung pada layanan dan paket bahasa di perangkat.
- Text-to-Speech bahasa Indonesia.
- Tombol Bicara dan log percakapan.
- Perintah sapaan, identitas Tata, bantuan fitur, jam, tanggal, dan persentase baterai.
- Perintah membuka WhatsApp, YouTube, kamera, Pengaturan, Telepon, aplikasi peta, browser, Galeri, dan aplikasi catatan yang tersedia.
- Pengetahuan lokal yang dapat dikelola langsung dari aplikasi melalui tombol **Pengetahuan Tata**: tambah, edit, hapus pertanyaan, kata kunci, dan jawaban.
- Data pengetahuan disimpan di penyimpanan internal aplikasi dalam file `knowledge.json` dan tetap tersedia setelah aplikasi ditutup atau perangkat dimulai ulang.
- Data awal alamat Harmani Bike: Jln. Pancasila No. 5, Sukosewu.
- Pemeriksaan koneksi tervalidasi dan pengambilan harga PAXGUSDT dari Binance saat online.

## Cara menambah pengetahuan
1. Buka aplikasi Tata AI.
2. Tekan **Pengetahuan Tata**.
3. Tekan **Tambah data**.
4. Isi pertanyaan, kata kunci (pisahkan dengan koma), dan jawaban.
5. Tekan **Simpan**. Data dapat dipilih kembali untuk diedit atau dihapus.
6. Setelah tersimpan, tanyakan hal yang sesuai melalui tombol **Bicara**.

## Batasan saat ini
- Tata menggunakan pencocokan pertanyaan dan kata kunci sederhana, bukan model AI percakapan bebas.
- Data pengetahuan tersimpan lokal di HP ini; belum tersinkron otomatis ke perangkat lain dan belum ada ekspor/impor cadangan dari aplikasi.
- Harga PAXGUSDT membutuhkan internet dan dapat gagal jika API tidak tersedia.
- Fitur suara memerlukan izin mikrofon dan layanan pengenalan suara Android.

## Build APK
Workflow GitHub Actions berjalan saat push ke branch `main` atau melalui `workflow_dispatch`.

Perintah build:
```bash
gradle assembleDebug --no-daemon
```

Artifact:
`app/build/outputs/apk/debug/app-debug.apk`
