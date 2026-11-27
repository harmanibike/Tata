# Tata AI

**Tata AI** adalah aplikasi asisten suara Android native berbasis Kotlin. Tata menggunakan kemampuan lokal dan dapat mengambil informasi online ketika internet tersedia.

## Identitas aplikasi
- Nama aplikasi: Tata AI
- Application ID: `com.harmanibike.voiceo3`
- Platform: Android native (Kotlin)
- Target perangkat uji: Redmi 14C, ABI `arm64-v8a`
- Minimum Android: API 26
- Compile/target SDK: 34
- JDK: 17

## Database dan arsitektur kemampuan
- Database lokal SQLite: `tata_ai.db`.
- Versi skema database saat ini: 2.
- Tabel utama: `knowledge`, `chat_messages`, `app_settings`, `assistant_capabilities`, dan `news_sources`.
- Migrasi versi 1 ke 2 bersifat aditif. Data pengetahuan, riwayat percakapan, dan pengaturan yang sudah ada tidak dihapus saat migrasi.
- `assistant_capabilities` menjadi katalog kemampuan Tata beserta versi, status aktif, dan konfigurasi JSON.
- `news_sources` menyimpan sumber RSS. Pembaca berita mengambil daftar sumber aktif dari SQLite, bukan lagi dari daftar URL yang ditulis langsung di `NetworkHelper.kt`.
- Sumber awal yang dimasukkan ke database: ANTARA dan CNA Indonesia.
- Fondasi database kemampuan sudah tersedia secara lokal. Sinkronisasi konfigurasi dari server/database online belum diaktifkan pada tahap ini.

## Izin dan kendali data
- Saat pertama kali dibuka, Tata meminta izin sebelum menyimpan riwayat percakapan ke database lokal.
- Jika izin ditolak, percakapan tetap tampil selama aplikasi berjalan tetapi tidak disimpan sebagai riwayat.
- Izin penyimpanan riwayat dapat diubah melalui tombol **Privasi**.
- Pengetahuan baru hanya tersimpan setelah pengguna mengisi dan menekan **Simpan**. Impor JSON juga hanya berjalan setelah pengguna memilih file dan memulai proses impor.
- Penghapusan satu pengetahuan memerlukan konfirmasi.
- Perintah suara/teks seperti **“Tata, hapus semua pengetahuan yang pernah kamu pelajari”** memunculkan dialog konfirmasi sebelum menghapus.
- Penghapusan seluruh pengetahuan hanya menghapus tabel `knowledge`. Riwayat percakapan, pengaturan aplikasi, dan konfigurasi sumber berita tidak ikut dihapus.
- Menu **Privasi** menyediakan pengaturan penyimpanan riwayat, penghapusan seluruh riwayat percakapan, dan penghapusan seluruh pengetahuan. Penghapusan riwayat dan pengetahuan merupakan tindakan terpisah.

## Fitur yang tersedia
- Pengenalan suara Android dengan preferensi bahasa Indonesia (`id-ID`) dan preferensi offline; ketersediaannya bergantung pada layanan dan paket bahasa di perangkat.
- Text-to-Speech bahasa Indonesia dengan pilihan suara yang tersedia dari mesin Android, penyimpanan pilihan suara, serta karakter bicara lebih lembut.
- Tombol Bicara, input teks, dan percakapan.
- Perintah sapaan, identitas Tata, bantuan fitur, jam, tanggal, dan persentase baterai.
- Perintah membuka WhatsApp, YouTube, kamera, Pengaturan, Telepon, aplikasi peta, browser, Galeri, dan aplikasi catatan yang tersedia.
- Pengetahuan lokal yang dapat dikelola melalui tombol **Pengetahuan Tata**: tambah, edit, dan hapus pertanyaan, kata kunci, serta jawaban.
- Membaca berita hari ini secara online melalui RSS dari sumber aktif di database. Tata menampilkan judul, ringkasan singkat, sumber, waktu terbit, dan tautan artikel, lalu membacakannya melalui Text-to-Speech Android.

## Mengatur suara Tata
1. Buka aplikasi Tata AI.
2. Tekan **Suara**.
3. Pilih salah satu suara bahasa Indonesia yang tersedia dari mesin Text-to-Speech Android.
4. Tata menyimpan pilihan tersebut dan menggunakannya kembali saat aplikasi dibuka.
5. Jika tidak ada pilihan suara bahasa Indonesia, buka pengaturan Text-to-Speech Android untuk memasang atau mengunduh data suara jika tersedia.

Karakter bawaan menggunakan pitch sedikit lebih tinggi dan kecepatan bicara sedikit lebih pelan. Kesan suara perempuan muda tetap bergantung pada variasi suara yang disediakan mesin TTS HP.

## Cadangan pengetahuan
- Tombol **Cadangan** membuka pilihan ekspor atau impor JSON.
- Ekspor menyimpan `pengetahuan-tata.json` melalui pemilih file Android.
- Impor memvalidasi struktur serta pertanyaan/jawaban wajib, lalu mengganti daftar pengetahuan lokal dengan isi file cadangan.
- Ukuran file impor dibatasi maksimal 2 MB.
- Cadangan dapat dipindahkan ke perangkat lain secara manual; sinkronisasi otomatis antarperangkat belum tersedia.

## Batasan saat ini
- Tata menggunakan pencocokan pertanyaan dan kata kunci sederhana, bukan model AI percakapan bebas.
- Database kemampuan dan sumber berita saat ini berada di SQLite lokal. Pembaruan konfigurasi dari server online, cache berita offline, dan antarmuka untuk mengelola sumber berita masih menjadi pekerjaan tahap berikutnya.
- Berita membutuhkan internet dan bergantung pada ketersediaan feed RSS. Jika feed gagal diakses atau tidak memuat artikel bertanggal hari ini, Tata akan memberi tahu pengguna.
- Riwayat chat dan pengetahuan merupakan dua jenis data terpisah. Menghapus pengetahuan tidak otomatis menghapus riwayat chat.
- Fitur suara memerlukan izin mikrofon dan layanan pengenalan suara Android.

## Build APK
Workflow GitHub Actions berjalan saat push ke branch `main` atau melalui `workflow_dispatch`.

Perintah build:
```bash
gradle assembleDebug --no-daemon
```

Artifact:
`app/build/outputs/apk/debug/app-debug.apk`
