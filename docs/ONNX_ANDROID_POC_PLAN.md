# Rencana proof-of-concept ONNX Runtime Android Tata

**Status: rencana teknis; dependency belum ditambahkan dan belum ada inferensi ONNX di aplikasi.**

## Keputusan sementara

Kandidat runtime CPU Android adalah artefak Maven:

```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
```

Versi 1.32.0 tercatat tersedia di Maven Central pada saat rencana ini ditulis dan metadata paket menyatakan lisensi MIT. Versi harus dipatok eksplisit; jangan memakai `latest.release` agar build dapat direproduksi.

Sumber pemeriksaan:
- Dokumentasi instalasi resmi ONNX Runtime: https://onnxruntime.ai/docs/install/
- Paket Android di Maven Central: https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android

Ini hanya kandidat untuk eksperimen. Jangan tambahkan dependency ke modul `:app` sebelum proof-of-concept terisolasi lulus.

## Batasan keselamatan dan ruang lingkup

- Tidak mengubah `:app`, jalur GGUF/llama.cpp, chat engine, atau APK lama.
- Buat modul Android library terpisah untuk PoC, atau gunakan project uji terpisah jika konfigurasi Gradle membuat modul baru terlalu invasif.
- Tidak menambahkan izin INTERNET, download model otomatis, telemetry, atau cloud inference.
- Model ONNX dan tokenizer hanya dibaca dari file lokal yang dipasok oleh penguji.
- Gunakan model sintetis kecil, bukan bobot produksi atau data pengguna.
- Pertahankan `minSdk 26` dan ABI yang telah dikonfigurasi; kompatibilitas runtime tetap harus dibuktikan melalui build dan perangkat.
- Jika dependency meningkatkan ukuran APK utama, itu alasan tambahan untuk mempertahankan isolasi.

## Tahap uji

1. **Dependency resolution:** pin versi, verifikasi AAR berhasil diunduh dan lisensinya tercatat.
2. **Runtime initialization:** buat `OrtEnvironment`, `OrtSession.SessionOptions`, lalu tutup resource dengan benar.
3. **Local model load:** buka ONNX dari file lokal; tes harus berjalan tanpa akses jaringan.
4. **Tensor contract:** masukkan `input_ids` bertipe INT64 dengan shape `[batch, sequence]`; periksa output `logits` bertipe FLOAT dengan shape `[batch, sequence, vocab]`.
5. **Numerical parity:** jalankan input deterministik yang sama pada PyTorch CPU, ONNX Runtime Python, dan ONNX Runtime Android; bandingkan logits memakai toleransi yang disepakati.
6. **Dynamic shapes:** tes minimal batch 1 dan 2, serta sequence pendek dan lebih panjang, sesuai batas context model.
7. **Offline assertion:** blokir jaringan pada perangkat/emulator saat memuat dan menjalankan model; tidak boleh ada request jaringan.
8. **Resource measurement:** catat ukuran AAR/penambahan ukuran APK PoC, RAM puncak, waktu inisialisasi, latency inferensi, dan stabilitas.
9. **Decision gate:** hanya setelah semua tes lulus, pertimbangkan adapter runtime terpisah untuk integrasi; tetap jangan mengganti engine chat tanpa uji regresi dan persetujuan.

## Risiko yang harus diuji

- Operator atau pola graph dari exporter PyTorch mungkin tidak didukung seperti yang diharapkan pada runtime Android.
- Hasil numerik dapat berbeda karena implementasi kernel dan presisi.
- Dynamic sequence, causal mask, dan dimensi vocabulary harus konsisten.
- AAR membawa native libraries; ukuran dan ABI harus diverifikasi pada artefak nyata.
- API Java/Kotlin mengelola resource native; session, tensor, result, dan environment harus ditutup sesuai lifecycle.
- Keberhasilan di emulator bukan bukti performa pada perangkat fisik.

## Kriteria lulus

PoC dinyatakan lulus hanya jika model sintetis lokal dapat dimuat dan dijalankan di Android, bentuk/dtype input-output sesuai kontrak, hasil logits cocok dengan referensi dalam toleransi yang disepakati, tidak ada akses jaringan, dan pengukuran ukuran/RAM/latency tersedia. Jika salah satu gagal, jangan integrasikan runtime ke aplikasi utama.

## Kondisi yang belum terpenuhi

- Belum ada model ONNX Tata produksi; ekspor saat ini bersifat eksperimental.
- Belum ada uji ONNX Runtime Android.
- Belum ada hasil perangkat fisik.
- Belum ada keputusan akhir untuk runtime produksi.
