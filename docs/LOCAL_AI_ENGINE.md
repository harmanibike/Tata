# Rencana Mesin AI Lokal Tata

## Tujuan
Menambahkan kemampuan percakapan bahasa alami generatif yang berjalan sepenuhnya di perangkat Android. Tidak ada API inferensi, API key, akun cloud, atau pengiriman prompt ke server AI.

## Pilihan teknologi
- Runtime inferensi: llama.cpp.
- Format model: GGUF, kuantisasi 4-bit sebagai titik awal.
- Integrasi Android: C++ melalui Android NDK dan JNI, dipanggil dari Kotlin.
- Arsitektur CPU awal: arm64-v8a.
- Model tidak dibundel ke APK. Pengguna memilih/mengimpor file model sendiri sehingga ukuran APK tetap kecil dan pembaruan aplikasi tidak mengunduh ulang model.
- Model dan percakapan tetap berada di penyimpanan privat perangkat.

Dokumentasi resmi llama.cpp menyediakan contoh Android dan jalur kompilasi NDK untuk arm64-v8a. Contoh aplikasi Android terbaru memakai minSdk 33; Tata saat ini mempertahankan minSdk 26, sehingga modul contoh tidak boleh disalin mentah-mentah. Integrasi harus memakai bridge JNI/NDK yang kompatibel dengan batas API Tata.

## Kebijakan perangkat dan memori
- Jangan otomatis memuat model besar ketika aplikasi dibuka.
- Muat model hanya setelah pengguna memilih dan mengaktifkannya.
- Jalankan inferensi di worker/background thread agar UI, mikrofon, dan animasi tidak macet.
- Sediakan tombol hentikan generasi dan lepaskan model ketika pengguna keluar dari mode AI atau sistem membutuhkan memori.
- Awali pengujian dengan model kecil 0.5B–1.5B parameter, GGUF Q4; ukuran file dan pemakaian RAM harus diukur pada perangkat nyata sebelum merekomendasikan model final.
- Batasi context window dan jumlah token keluaran pada perangkat RAM rendah. Jangan menjanjikan kecepatan tertentu sebelum benchmark Redmi 14C.
- Jika model tidak tersedia atau memori tidak cukup, Tata kembali ke pengetahuan, penalaran, dan percakapan aturan lokal yang sudah ada.

## Privasi dan kendali
- Model tidak boleh menyimpan percakapan sebagai pengetahuan secara otomatis.
- Memori pribadi tetap memerlukan izin eksplisit pengguna, terpisah dari riwayat chat.
- Impor model harus melalui pemilih file Android dan menampilkan nama, ukuran, serta konfirmasi pengguna.
- Penghapusan model memerlukan konfirmasi dan hanya menghapus file model yang dipilih, bukan pengetahuan atau riwayat chat.
- Tidak ada pengiriman prompt ke jaringan. Koneksi internet hanya boleh dipakai jika pengguna secara terpisah memilih riset web atau mengunduh model.

## Tahapan implementasi
1. Audit kompatibilitas NDK/JNI dan tentukan versi llama.cpp yang dipatok (pinned), bukan mengambil branch bergerak saat setiap build.
2. Buat native library arm64-v8a dan bridge JNI dengan lifecycle aman: init, load, generate token, cancel, unload, destroy.
3. Buat pengelola model GGUF: pilih file, validasi header, simpan URI/path yang aman, tampilkan ukuran dan status.
4. Buat UI pengaturan AI lokal: model belum dipasang / siap / sedang memuat / aktif / gagal; sediakan uji respons.
5. Integrasikan ke CommandRouter sebagai jalur percakapan generatif setelah perintah khusus, pengetahuan lokal, dan kalkulasi deterministik.
6. Uji model dan pemakaian RAM di Redmi 14C; optimalkan konteks, jumlah thread, dan panjang jawaban.
7. Jalankan unit test, build debug, dan signed release sebelum menaikkan versionCode.

## Kriteria selesai
- Pertanyaan bebas berbahasa Indonesia menghasilkan jawaban dari model lokal tanpa API.
- Mode offline benar-benar dapat digunakan setelah model diunduh/dipilih.
- UI tetap responsif; pengguna dapat menghentikan generasi.
- Model dapat dilepas dari RAM dan dihapus dengan persetujuan.
- Fitur lama, izin simpan data, dan tanda tangan APK tetap terjaga.
- Tes CI dan build APK lulus. Uji pada perangkat fisik tetap diperlukan untuk mengukur kualitas, suhu, kecepatan, dan RAM.
