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

## Status implementasi
- Selesai: pengelola file GGUF lokal melalui **Pengaturan > Model AI lokal**. File dipilih melalui pemilih dokumen Android, header biner GGUF versi 2–3 dan jumlah tensor/metadata diperiksa, lalu file disalin ke penyimpanan privat aplikasi.
- Selesai: daftar nama dan ukuran model, nama file aman, impor sementara lalu rename atomik, serta penghapusan file model dengan konfirmasi.
- Selesai: informasi ruang kosong dan perlindungan penyimpanan saat impor.
- Selesai: parser header GGUF ringan yang menolak magic, versi, dan jumlah tensor/metadata yang tidak masuk akal; ini belum memvalidasi seluruh metadata, tokenizer, tensor, atau kompatibilitas arsitektur model. Tata membaca ukuran file jika disediakan pemilih dokumen, memeriksa ruang yang tersedia, dan menghentikan penyalinan jika ruang kosong akan turun di bawah 256 MB.
- Selesai: model tidak otomatis dimuat, tidak dikirim ke server, tidak dimasukkan ke APK, dan penghapusan model tidak menghapus pengetahuan/riwayat.
- Selesai: kompilasi native `llama.cpp` untuk Android arm64 pada GitHub Actions dan verifikasi artefak `libllama.so`.
- Selesai tahap integrasi awal: JNI native memuat/melepas model, membentuk prompt memakai chat template GGUF bila tersedia, melakukan tokenisasi, sampling, dan menghasilkan jawaban di thread latar belakang Kotlin.
- Selesai tahap UI awal: pengguna dapat mengaktifkan model pilihan secara manual dari **Pengaturan > Model AI lokal**, melihat status aktif, melepas model dari RAM, atau menghapus file.
- Batasan saat ini: model harus diimpor sendiri; belum ada model bawaan. Model yang lolos header GGUF belum tentu kompatibel dengan llama.cpp atau cukup kecil untuk RAM perangkat. Uji perangkat fisik tetap wajib.
- Batasan saat ini: keluaran belum streaming per token; pembatalan dilakukan dengan melepas model. Riwayat chat tidak dipakai otomatis sebagai konteks model, dan jawaban tidak disimpan sebagai pengetahuan.

## Tahapan implementasi
1. [Selesai] Fondasi pengelola model GGUF privat, pemeriksaan header struktural, dan perlindungan ruang penyimpanan.
2. [Selesai] Audit kompatibilitas NDK/JNI dan pin versi llama.cpp pada commit tetap.
3. [Selesai tahap awal] Buat native library arm64-v8a dan bridge JNI untuk load, generate, cancel, unload.
4. Validasi metadata GGUF penuh, arsitektur, tokenizer, dan batas konteks sebelum model diaktifkan.
5. Tingkatkan UI status model: belum dipasang / siap / sedang memuat / aktif / gagal; tambahkan streaming token dan tombol hentikan generasi yang jelas.
6. [Selesai tahap integrasi awal] Integrasikan ke CommandRouter setelah perintah khusus, pengetahuan lokal, dan kalkulasi deterministik.
7. Uji model dan pemakaian RAM di perangkat nyata; optimalkan konteks, jumlah thread, dan panjang jawaban.
8. Jalankan unit test, build debug, signed release, lalu uji instalasi dan percakapan pada perangkat fisik sebelum menaikkan versionCode.

## Kriteria selesai
- Pertanyaan bebas berbahasa Indonesia menghasilkan jawaban dari model lokal tanpa API.
- Mode offline benar-benar dapat digunakan setelah model diunduh/dipilih.
- UI tetap responsif; pengguna dapat menghentikan generasi.
- Model dapat dilepas dari RAM dan dihapus dengan persetujuan.
- Fitur lama, izin simpan data, dan tanda tangan APK tetap terjaga.
- Tes CI dan build APK lulus. Uji pada perangkat fisik tetap diperlukan untuk mengukur kualitas, suhu, kecepatan, dan RAM.


## Pengamanan penggunaan RAM dan prompt
- Model hanya dimuat setelah pengguna memilih **Aktifkan model**.
- Saat aplikasi masuk background (`onStop`), Tata meminta generasi berhenti dan melepas model dari RAM melalui thread IO. Pengguna perlu mengaktifkan ulang model saat kembali.
- Input pertanyaan dibatasi 700 karakter dan konteks percakapan gabungan dibatasi 1.400 karakter di sisi Kotlin. Jika jumlah token tetap melebihi kapasitas konteks setelah instruksi sistem diperhitungkan, generasi ditolak dengan pesan agar pengguna meringkas pertanyaan; bagian awal prompt tidak dipotong sehingga instruksi sistem tidak hilang.
- Konteks awal 1.024 token, keluaran generasi dibatasi 32–220 token sesuai pemanggil, dan 2 thread sebagai konfigurasi awal. Ini adalah batas awal konservatif, bukan jaminan semua model akan berjalan lancar di setiap HP.


## Konteks percakapan generatif
- Model lokal menyimpan maksimal dua pasangan pertanyaan-jawaban terakhir hanya di memori proses untuk membantu pertanyaan lanjutan. Setiap pesan riwayat dipotong agar tidak mendominasi konteks.
- Konteks sementara tidak masuk SQLite, backup pengetahuan, atau penyimpanan permanen. Riwayat sementara dibersihkan ketika model dilepas dari RAM, termasuk saat aplikasi masuk background.
- Percakapan berpola seperti sapaan dan dukungan emosi tetap dapat ditangani mesin lokal berbasis aturan; pertanyaan terbuka dan tindak lanjutnya diteruskan ke model generatif ketika model aktif.


## Peningkatan kualitas respons (tahap awal)
- Instruksi sistem meminta model menjawab inti pertanyaan lebih dahulu, menyesuaikan panjang jawaban, memakai riwayat hanya jika relevan, menghindari pengulangan, dan mengakui ketidakpastian.
- Model dilarang mengarang angka, sumber, kutipan, tanggal, atau kemampuan aplikasi, serta dilarang mengklaim telah melakukan pencarian atau tindakan yang belum dilakukan.
- Router hanya menjalankan pencarian web jika pengguna memberi perintah eksplisit melalui pola **Cari/Telusuri di internet/web**. Pertanyaan terbuka yang tidak terjawab tidak otomatis dikirim keluar perangkat.
- Ini adalah penguatan prompt dan pengelolaan konteks, bukan jaminan kebenaran model. Akurasi tetap bergantung pada model GGUF yang dipilih dan harus diuji pada perangkat nyata.
## Jalur alternatif: Tata Generatif Hibrida tanpa GGUF

Pengembangan tidak mewajibkan GGUF. Jalur hibrida dibagi menjadi tiga lapisan yang dapat berkembang terpisah:

1. **Mesin kemampuan berbasis kode:** pemahaman maksud, penalaran deterministik, pengelolaan konteks sementara, dan penyusunan respons.
2. **Mesin pengetahuan:** SQLite dan Learning Lab. Data baru hanya menjadi pengetahuan aktif setelah ditinjau dan disetujui; data ini tidak otomatis melatih bobot neural network.
3. **Mesin generatif neural milik Tata (tahap riset berikutnya):** model yang arsitektur dan bobotnya dikembangkan serta dilatih sendiri. Pelatihan memerlukan korpus teks berizin, tokenizer, pipeline training, evaluasi, dan komputasi yang memadai. Ini belum tersedia di APK.

### Implementasi awal generator berbasis kode
- TataGenerativeEngine menyusun respons dari maksud dan bagian kalimat yang diberikan pengguna untuk permintaan membuat rencana, membandingkan pilihan, dan menimbang keputusan.
- Generator berjalan di Kotlin, offline, tanpa API, tanpa GGUF, tanpa jaringan, dan tanpa penyimpanan percakapan ke database.
- Generator tidak mengarang fakta. Untuk pertanyaan faktual yang tidak ditemukan di pengetahuan lokal, Tata menyatakan keterbatasannya dan meminta pengguna mengajarkan sumber melalui Learning Lab atau secara eksplisit meminta riset web.
- Jika model lokal opsional tersedia dan aktif, model tersebut tetap dapat digunakan untuk generasi bahasa lebih luas. Namun generator berbasis kode tetap menjadi jalur tanpa model eksternal.
- Pengujian JUnit memeriksa bahwa rencana memuat tujuan pengguna, perbandingan meminta opsi yang jelas, dan pertanyaan faktual biasa tidak dijawab dengan template yang menyesatkan.

### Batasan yang harus dinyatakan
Generator berbasis kode **bukan neural network dan belum setara ChatGPT**. Ia menghasilkan kalimat baru dengan menggabungkan struktur respons dan informasi yang benar-benar diberikan pengguna atau tersedia di pengetahuan lokal. Untuk menghasilkan bahasa yang fleksibel dan pengetahuan umum luas, tahap neural network milik Tata masih membutuhkan data pelatihan, proses training, dan pengujian tersendiri. Jangan menyebut tahap awal ini sebagai model bahasa yang sudah dilatih.
