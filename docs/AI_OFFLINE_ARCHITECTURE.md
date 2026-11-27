# Rancangan AI Lokal Tata

Dokumen ini menjadi dasar integrasi AI lokal secara bertahap. **Belum ada model Whisper atau Qwen yang dimasukkan ke APK saat ini.**

## Tujuan

Tata tetap dapat menjalankan perintah dasar tanpa internet, lalu memperoleh kemampuan percakapan ketika mesin AI lokal tersedia. Mode online menjadi pilihan tambahan, bukan syarat agar fungsi dasar bekerja.

## Arsitektur yang disarankan

| Lapisan | Pilihan awal | Tanggung jawab |
|---|---|---|
| Input suara | Whisper.cpp multilingual (uji model tiny terlebih dahulu) | Mengubah ucapan Indonesia menjadi teks secara lokal |
| Pemrosesan bahasa | Qwen2.5-0.5B-Instruct GGUF Q4_K_M melalui llama.cpp | Menjawab percakapan sederhana secara lokal |
| Suara jawaban | Android TextToSpeech id-ID | Membacakan jawaban Tata |
| Perintah perangkat | CommandRouter yang sudah ada | Membuka aplikasi, membaca baterai, waktu, dan perintah lokal |
| Pengetahuan/memori | Penyimpanan lokal yang sudah ada, lalu ditingkatkan bertahap | Menyimpan preferensi dan konteks yang memang diizinkan |
| Mode online | Backend/proxy aman (tahap berikutnya) | Menyediakan model lebih kuat tanpa menaruh API key rahasia di APK |

## Keputusan teknis penting

1. **Jangan memasang Vosk dulu.** Belum ada model bahasa Indonesia Vosk resmi yang sudah diverifikasi untuk kebutuhan aplikasi ini. Vosk baru dipertimbangkan jika ada model Indonesia yang jelas sumber, lisensi, ukuran, dan hasil uji perangkatnya.
2. **Uji Whisper.cpp terlebih dahulu untuk ASR.** Proyek upstream menyediakan contoh aplikasi Android dan menyarankan model tiny atau base untuk perangkat Android. Pilih model multilingual, kemudian ukur akurasi bahasa Indonesia, waktu transkripsi, RAM, baterai, dan suhu pada perangkat nyata.
3. **Qwen diunduh terpisah dari APK.** Varian Q4_K_M yang ditemukan berukuran sekitar 398 MB pada salah satu repositori quantized pihak ketiga. Ukuran aktual dapat berbeda menurut sumber/versi. File model tidak boleh dimasukkan ke APK karena akan memperbesar unduhan aplikasi.
4. **Qwen 0.5B adalah model kecil.** Gunakan untuk percakapan ringan dan perintah berbasis bahasa, bukan menjanjikan kualitas setara layanan AI besar. Uji kualitas jawaban bahasa Indonesia sebelum menjadikannya default.
5. **Integrasi native dilakukan setelah baseline aman.** Whisper.cpp dan llama.cpp memerlukan komponen native/NDK serta pengujian ABI. Jangan menambahkan dua engine sekaligus dalam satu perubahan besar.
6. **Sediakan fallback.** Jika model belum diunduh, gagal dimuat, atau perangkat kehabisan memori, Tata kembali ke SpeechRecognizer Android dan CommandRouter yang sudah ada.
7. **Privasi dan keamanan.** Percakapan lokal diproses di perangkat. Jika mode online ditambahkan, API key rahasia disimpan di backend, bukan di source code atau APK.

## Tahapan implementasi

### Tahap 0 — Dokumentasi dan baseline
- Simpan keputusan arsitektur ini.
- Pastikan build APK yang ada tetap berhasil.
- Jangan mengubah perilaku perintah yang sudah berjalan.

### Tahap 1 — Mesin pengenal suara lokal
- Buat abstraksi pengenal suara agar Whisper dapat dipasang tanpa menghapus fallback Android.
- Integrasikan Whisper.cpp Android/NDK secara terpisah.
- Unduh model multilingual kecil setelah pengguna memilih fitur offline.
- Uji kalimat Indonesia, kebisingan bengkel, waktu proses, RAM, baterai, dan suhu.
- Jangan menyatakan fitur siap sebelum uji perangkat lulus.

### Tahap 2 — Mesin percakapan lokal
- Tambahkan lapisan `LocalChatEngine` dengan backend llama.cpp.
- Unduh Qwen GGUF setelah persetujuan pengguna dan tampilkan ukuran unduhan serta ruang penyimpanan yang diperlukan.
- Batasi panjang konteks dan jumlah token untuk mengendalikan penggunaan memori.
- Uji kualitas bahasa Indonesia, waktu respons, dan stabilitas pada Redmi 14C.

### Tahap 3 — Pengalaman percakapan
- Gabungkan hasil ASR → percakapan AI → TextToSpeech.
- Pertahankan riwayat percakapan dan konteks lanjutan secara lokal.
- Berikan tombol hentikan bicara dan hentikan pembacaan suara.
- Tampilkan status: model belum diunduh, siap offline, atau memakai mode online.

### Tahap 4 — Mode online dan penyempurnaan
- Tambahkan backend/proxy untuk penyedia AI online.
- Tambahkan pengaturan mode lokal/online dan aturan privasi.
- Uji pemulihan saat jaringan putus serta fallback ke mode lokal.

## Kriteria lulus sebelum rilis

- APK debug dan release dapat dibangun.
- Perintah dasar lama tetap berfungsi.
- Tata dapat mengenali beberapa kalimat Indonesia secara offline pada perangkat target.
- Model Qwen dapat dimuat dan menjawab tanpa crash.
- Tidak ada API key rahasia di APK.
- Model dapat dihapus dan diunduh ulang dari pengaturan.
- Penggunaan RAM, suhu, baterai, dan waktu respons diukur pada perangkat nyata.
- Semua kegagalan model memiliki pesan yang jelas dan fallback yang aman.

## Referensi teknis

- Whisper.cpp Android sample: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
- Qwen2.5-0.5B-Instruct GGUF (quantization pihak ketiga; cek ulang sumber dan lisensi file sebelum distribusi): https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF
- Dokumentasi llama.cpp: https://github.com/ggml-org/llama.cpp

## Status

- [x] Rancangan arsitektur disusun.
- [ ] Integrasi Whisper.cpp.
- [ ] Uji pengenalan bahasa Indonesia pada perangkat target.
- [ ] Integrasi llama.cpp dan Qwen GGUF.
- [ ] Uji percakapan offline end-to-end.
- [ ] Mode online aman.
