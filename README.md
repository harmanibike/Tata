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

## Mesin AI generatif lokal
- Tata memiliki bridge Kotlin/JNI ke `llama.cpp` untuk menjalankan model GGUF secara lokal pada Android `arm64-v8a`; rancangan teknis ada di [docs/LOCAL_AI_ENGINE.md](docs/LOCAL_AI_ENGINE.md).
- Tidak ada API inferensi AI eksternal, API key, atau layanan AI berbayar. Prompt diproses di perangkat.
- Model GGUF belum dibundel di APK. Pengguna mengelola model melalui **Pengaturan > Model AI lokal**; satu model dimuat pada satu waktu untuk menjaga RAM.
- Model hanya dimuat setelah pengguna mengaktifkannya, dan dapat dilepas dari RAM atau dihapus dengan konfirmasi. Ukuran konteks dan thread dibatasi secara konservatif.
- Build CI membuktikan kompilasi/packaging, bukan keberhasilan inferensi pada HP. Kualitas, RAM puncak, suhu, baterai, dan stabilitas masih perlu diuji pada perangkat fisik.
- Kompatibilitas minimum Android API 26 harus dipertahankan.

## Database dan arsitektur kemampuan
- Database lokal SQLite: `tata_ai.db`.
- Versi skema database saat ini: 2.
- Tabel utama: `knowledge`, `chat_messages`, `app_settings`, `assistant_capabilities`, dan `news_sources`.
- Migrasi versi 1 ke 2 bersifat aditif. Data pengetahuan, riwayat percakapan, dan pengaturan yang sudah ada tidak dihapus saat migrasi.
- `assistant_capabilities` menjadi katalog kemampuan Tata beserta versi, status aktif, dan konfigurasi JSON.
- `news_sources` menyimpan sumber RSS. Pembaca berita mengambil daftar sumber aktif dari SQLite, bukan lagi dari daftar URL yang ditulis langsung di `NetworkHelper.kt`. Sumber RSS dapat dikelola melalui tombol **Sumber** tanpa build ulang APK.
- Sumber awal yang dimasukkan ke database: ANTARA dan CNA Indonesia.
- Menu **Sumber** memungkinkan pengguna menambahkan, mengaktifkan, menonaktifkan, dan menghapus sumber RSS langsung dari aplikasi.
- Fondasi database kemampuan sudah tersedia secara lokal. Sinkronisasi konfigurasi dari server/database online belum diaktifkan pada tahap ini.

## Izin dan kendali data
- **Learning Lab lokal (tahap awal):** tersedia melalui Pengaturan > Learning Lab untuk mencatat koreksi secara eksplisit, meninjau status, dan secara terpisah memasukkan koreksi yang diterima ke pengetahuan aktif. Catatan tidak dibagikan dan tidak melatih bobot model secara otomatis. Lihat [docs/TATA_LEARNING_SYSTEM.md](docs/TATA_LEARNING_SYSTEM.md).

- Saat pertama kali dibuka, Tata meminta izin sebelum menyimpan riwayat percakapan ke database lokal.
- Spesifikasi pembelajaran dengan persetujuan ada di [docs/TATA_LEARNING_SYSTEM.md](docs/TATA_LEARNING_SYSTEM.md). Data pengetahuan disimpan lokal secara default; persetujuan menyimpan, berbagi ke komunitas, dan menggunakan contoh untuk pelatihan model adalah tiga izin yang berbeda.
- Informasi pencarian, koreksi, dan percakapan tidak otomatis menjadi data pengetahuan atau data pelatihan. Pengguna harus meninjau dan menyetujui setiap tindakan penyimpanan/kontribusi.
- Learning Lab dan pelatihan model dari data kontribusi masih merupakan tahap pengembangan; dokumentasi ini tidak menyatakan fitur tersebut sudah tersedia di APK.
- Jika izin ditolak, percakapan tetap tampil selama aplikasi berjalan tetapi tidak disimpan sebagai riwayat.
- Pertanyaan yang belum dapat dijawab Tata ditampilkan sebagai sesi pencarian sementara. Pertanyaan tersebut dan pemberitahuan pencarian tidak ditulis ke tabel `knowledge` maupun `chat_messages`, walaupun izin penyimpanan riwayat chat sedang aktif.
- Jika pengguna memilih pencarian internet atau AI lain, Tata membuka browser. Untuk ChatGPT/Gemini, pertanyaan disalin ke clipboard agar pengguna dapat menempelkannya sendiri. Tidak ada API AI yang dipanggil dan hasil pencarian tidak otomatis dipelajari atau disimpan.
- Dialog pencarian menyediakan opsi **Tempel jawaban untuk diringkas**. Pengguna dapat menempel teks hasil pencarian/AI (maksimal 12.000 karakter); Tata membuat ringkasan awal secara lokal dengan mengambil beberapa kalimat pertama, menampilkan dan membacakannya tanpa menyimpan teks sumber maupun ringkasan ke database. Dari ringkasan, pengguna dapat memilih **Ajari Tata**; Tata membuka formulir berisi pertanyaan dan jawaban awal untuk diperiksa/diedit. Pengetahuan baru hanya masuk database setelah pengguna secara sadar menekan **Simpan** pada formulir.
- Ringkasan sementara bukan pemahaman generatif atau verifikasi fakta. Tata memberi catatan bahwa sumber belum diverifikasi; kemampuan merangkum ini sengaja ringan agar berjalan offline tanpa API.
- Izin penyimpanan riwayat dapat diubah melalui tombol **Privasi**.
- Pengetahuan baru hanya tersimpan setelah pengguna mengisi dan menekan **Simpan**. Impor JSON juga hanya berjalan setelah pengguna memilih file dan memulai proses impor.
- Penghapusan satu pengetahuan memerlukan konfirmasi.
- Perintah suara/teks seperti **“Tata, hapus semua pengetahuan yang pernah kamu pelajari”** memunculkan dialog konfirmasi sebelum menghapus.
- Penghapusan seluruh pengetahuan hanya menghapus tabel `knowledge`. Riwayat percakapan, pengaturan aplikasi, dan konfigurasi sumber berita tidak ikut dihapus.
- Menu **Privasi** menyediakan pengaturan penyimpanan riwayat, penghapusan seluruh riwayat percakapan, dan penghapusan seluruh pengetahuan. Penghapusan riwayat dan pengetahuan merupakan tindakan terpisah.

## Fitur yang tersedia
- Pengelolaan file model GGUF lokal tersedia melalui **Pengaturan > Model AI lokal**. Impor memakai pemilih dokumen Android, memvalidasi magic header `GGUF`, menyalin file ke direktori privat aplikasi, dan menampilkan nama serta ukuran. Penghapusan memerlukan konfirmasi dan hanya menghapus file model yang dipilih. Ini belum merupakan fitur inferensi AI; model belum dapat dijalankan sampai native engine llama.cpp/JNI selesai diintegrasikan.

- Profil kemampuan Tata disimpan pada tabel `assistant_capabilities` dan ditampilkan melalui **Pengaturan > Tentang Tata AI > Kemampuan & profil**. Deskripsi kemampuan dapat diubah pengguna; perubahan hanya tersimpan setelah tombol **Simpan** ditekan dan nomor versi kemampuan dinaikkan. Data ini adalah konfigurasi profil, terpisah dari pengetahuan yang dipelajari.
- Kecerdasan emosional lokal mengenali petunjuk teks tentang sedih, senang/bahagia, marah/kesal, takut/cemas, kecewa, dan kesepian. Tata memberi respons empatik, pertanyaan lanjutan, dan pilihan untuk didengarkan atau mencari langkah berikutnya. Konteks emosi hanya dipakai sementara selama sesi percakapan dan tidak disimpan sebagai profil emosi pengguna.
- Pengenalan emosi saat ini berdasarkan kata/kalimat yang diketik atau dikenali oleh speech-to-text Android. Tata tidak membaca ekspresi wajah atau mengukur emosi dari suara, tidak dapat memastikan perasaan seseorang, dan bukan alat diagnosis kesehatan mental. Jika ada ancaman keselamatan langsung, pengguna perlu menghubungi orang tepercaya atau layanan darurat setempat.
- Identitas Tata tersedia melalui **Pengaturan > Tentang Tata AI** dan pertanyaan suara/teks seperti “Siapa kamu?” atau “Versi berapa?”. Tata menjelaskan bahwa AI berarti *Artificial Intelligence* atau kecerdasan buatan, serta membaca versionName dan versionCode langsung dari metadata APK yang sedang berjalan.
- Deskripsi diri menjelaskan platform Android/Kotlin, kemampuan offline dan online, batas percakapan lokal non-generatif, kebijakan tanpa API AI eksternal, serta prinsip bahwa pengetahuan tidak disimpan tanpa izin.
- Setelah ringkasan teks tempelan, pengguna dapat memilih **Tanya isi teks**. Tata mencari hingga tiga kalimat berdasarkan kecocokan kata dari teks yang masih berada di memori sesi Activity. Ini pencarian sederhana, bukan penalaran generatif atau pemeriksaan fakta; teks dan pertanyaan lanjutan tidak disimpan ke database dan hilang saat sesi Activity berakhir.
- Pengenalan suara Android dengan preferensi bahasa Indonesia (`id-ID`) dan preferensi offline; ketersediaannya bergantung pada layanan dan paket bahasa di perangkat.
- Text-to-Speech bahasa Indonesia dengan pemilihan otomatis suara berbasis jaringan saat online dan suara lokal saat offline, jika mesin TTS Android menyediakan kedua jenis suara.
- Karakter suara perempuan muda dipilih berdasarkan metadata suara yang disediakan mesin TTS; jika metadata gender tidak tersedia, Tata memilih suara Indonesia dengan kualitas tertinggi yang tersedia. Pitch dan tempo disetel lembut.
- Suara online pada tahap ini memakai voice network-enabled dari mesin TTS Android (misalnya Google Speech Services jika tersedia), bukan layanan AI TTS cloud khusus. Kualitas, ketersediaan, dan karakter suara bergantung pada mesin TTS serta paket suara yang terpasang di HP.
- Tombol Bicara, input teks, dan percakapan.
- Mesin percakapan lokal berbasis aturan untuk sapaan, kabar, ucapan terima kasih, dukungan emosional (sedih, senang, marah, cemas, kecewa, kesepian), motivasi, lelucon ringan, identitas, jam, dan tanggal.
- Pengetahuan lokal pengguna diperiksa sebelum jawaban percakapan bawaan, sehingga jawaban yang sudah diajarkan dapat diprioritaskan.
- Mesin percakapan lokal tidak mengirim pesan ke server dan tidak menyimpan pengetahuan secara otomatis. Pengguna tetap harus menyimpan pengetahuan secara eksplisit melalui menu Pengetahuan.
- Perintah sapaan, identitas Tata, bantuan fitur, jam, tanggal, dan persentase baterai.
- Perintah membuka WhatsApp, YouTube, kamera, Pengaturan, Telepon, aplikasi peta, browser, Galeri, dan aplikasi catatan yang tersedia.
- Pengetahuan lokal yang dapat dikelola melalui tombol **Pengetahuan Tata**: tambah, edit, dan hapus pertanyaan, kata kunci, serta jawaban.
- Membaca berita hari ini secara online melalui RSS dari sumber aktif di database. Tata menampilkan judul, ringkasan singkat, sumber, waktu terbit, dan tautan artikel, lalu membacakannya melalui Text-to-Speech Android.

## Suara natural online dan fallback offline
- Saat koneksi internet tervalidasi tersedia, Tata memilih suara Bahasa Indonesia bertipe network dari mesin TTS Android jika tersedia.
- Saat offline, Tata memilih suara Bahasa Indonesia lokal yang tidak memerlukan jaringan.
- Jika suara network tidak tersedia atau gagal dipilih, Tata memakai suara lokal sebagai fallback.
- Pemilihan karakter perempuan muda memakai metadata nama/fitur suara jika mesin TTS menyediakannya; jika tidak, pemilihan berdasarkan kualitas suara terbaik dan tidak dapat menjamin gender.
- Buka **Pengaturan > Suara Tata** untuk melihat dan mencoba suara Bahasa Indonesia yang tersedia. Paket suara tambahan dapat dipasang melalui pengaturan Text-to-Speech Android.
- Pitch sedikit lebih tinggi dan tempo sedikit lebih pelan diterapkan sebagai penyesuaian ringan. Suara AI cloud khusus belum diintegrasikan; itu memerlukan penyedia TTS, endpoint layanan, dan kredensial API tersendiri.

## Cadangan pengetahuan
- Tombol **Cadangan** membuka pilihan ekspor atau impor JSON.
- Ekspor menyimpan `pengetahuan-tata.json` melalui pemilih file Android.
- Impor memvalidasi struktur serta pertanyaan/jawaban wajib, lalu mengganti daftar pengetahuan lokal dengan isi file cadangan.
- Ukuran file impor dibatasi maksimal 2 MB.
- Hasil riset sementara dapat dijadikan pengetahuan hanya melalui alur **Ajari Tata > periksa/edit formulir > Simpan**. Membuka opsi Ajari Tata saja belum menyimpan data.
- Cadangan dapat dipindahkan ke perangkat lain secara manual; sinkronisasi otomatis antarperangkat belum tersedia.

## Batasan saat ini
- Percakapan offline memakai mesin aturan dan pencocokan pengetahuan lokal, bukan model bahasa generatif. Tata dapat menangani obrolan umum yang dikenali, tetapi tidak dapat menjawab pertanyaan bebas seluas model AI besar tanpa model lokal tambahan.
- Database kemampuan dan sumber berita saat ini berada di SQLite lokal. Pembaruan konfigurasi dari server online, cache berita offline, dan antarmuka untuk mengelola sumber berita masih menjadi pekerjaan tahap berikutnya.
- Berita membutuhkan internet dan bergantung pada ketersediaan feed RSS. Jika feed gagal diakses atau tidak memuat artikel bertanggal hari ini, Tata akan memberi tahu pengguna.
- Riwayat chat dan pengetahuan merupakan dua jenis data terpisah. Menghapus pengetahuan tidak otomatis menghapus riwayat chat.
- Fitur suara memerlukan izin mikrofon dan layanan pengenalan suara Android.

## Tanda tangan digital APK Release
- Build Debug tetap tersedia untuk pengujian dan menggunakan application ID dengan akhiran `.debug`.
- Build Release menggunakan konfigurasi signing permanen yang membaca keystore dan kata sandi dari GitHub Actions Secrets; file keystore tidak disimpan di repositori.
- Siapkan empat GitHub Actions Secrets berikut sebelum build Release dapat dijalankan: `TATA_KEYSTORE_BASE64`, `TATA_STORE_PASSWORD`, `TATA_KEY_ALIAS`, dan `TATA_KEY_PASSWORD`.
- Workflow hanya menjalankan job Release bertanda tangan jika keempat Secrets tersedia. Jika belum tersedia, job Debug tetap berjalan seperti biasa.
- Pertahankan keystore dan kata sandinya dengan aman serta gunakan keystore yang sama untuk semua versi Release mendatang. Jangan membuat keystore baru untuk pembaruan.
- Setiap rilis baru harus menaikkan `versionCode` dan mempertahankan `applicationId`.
- Peralihan dari APK Debug ke APK Release memerlukan instalasi awal terpisah karena Debug memakai application ID `com.harmanibike.voiceo3.debug`, sedangkan Release memakai `com.harmanibike.voiceo3`. Setelah versi Release pertama terpasang, pembaruan Release berikutnya dapat dipasang di atas versi lama tanpa uninstall selama tanda tangan dan application ID tetap sama.
- Penandatanganan APK tidak menghapus database aplikasi. Namun, tetap lakukan backup sebelum pembaruan besar.

## Build APK
Workflow GitHub Actions berjalan saat push ke branch `main` atau melalui `workflow_dispatch`. Job Debug menjalankan pengujian unit penalaran lokal terlebih dahulu; job Release bertanda tangan baru berjalan jika pengujian dan build Debug berhasil.

Perintah build:
```bash
gradle assembleDebug --no-daemon
```

Artifact:
`app/build/outputs/apk/debug/app-debug.apk`


## Prinsip arsitektur: kemampuan dan pengetahuan dipisahkan

Aturan ini menjadi prinsip tetap pengembangan Tata AI:

### 1. Kemampuan AI (kode aplikasi)
- Kemampuan adalah fungsi/program yang benar-benar dapat dilakukan Tata, misalnya memahami perintah, percakapan, penalaran, membaca dokumen, riset, pengenalan gambar, dan pengelolaan tugas.
- Implementasi kemampuan harus berada di source code aplikasi (Kotlin/Android) dan menjadi bagian dari APK. Kemampuan tidak dibuat dengan menambahkan baris pengetahuan ke database.
- Mengubah, menambah, atau memperbaiki implementasi kemampuan memerlukan perubahan script dan build APK baru.
- Tabel `assistant_capabilities` hanya boleh dipakai sebagai katalog/deskripsi, status, atau metadata kemampuan. Mengedit deskripsi di database tidak menciptakan, menghapus, atau mengubah implementasi kode kemampuan.
- Tidak menggunakan API AI eksternal atau layanan AI berbayar. Kemampuan AI yang membutuhkan model generatif harus menggunakan model lokal/on-device jika perangkat mendukungnya.

### 2. Pengetahuan (data yang dapat diperbarui)
- Pengetahuan faktual, definisi, panduan, koreksi, dan materi yang dipelajari disimpan terpisah dalam database pengetahuan.
- Pengetahuan dapat ditambah, diperbarui, dikoreksi, diekspor, diimpor, atau dihapus tanpa membangun ulang APK.
- Tata harus meminta persetujuan sebelum menyimpan informasi baru atau menghapus pengetahuan. Perintah hapus seluruh pengetahuan memerlukan konfirmasi khusus.
- Informasi riset atau teks sementara tidak otomatis menjadi pengetahuan permanen.

### 3. Memori pengguna dan data sementara
- Memori tentang pengguna disimpan terpisah dari pengetahuan umum dan hanya setelah pengguna memberi izin.
- Konteks percakapan sementara boleh digunakan selama sesi untuk menjaga kesinambungan percakapan, tetapi tidak otomatis disimpan ke database.
- Menghapus pengetahuan umum tidak boleh diam-diam menghapus atau mengubah memori pengguna maupun riwayat percakapan; setiap jenis data dikelola secara terpisah.

**Ringkasnya:** script menentukan apa yang Tata mampu lakukan; database pengetahuan menentukan apa yang Tata ketahui. Memperbarui pengetahuan tidak memerlukan APK baru, sedangkan menambah kemampuan yang benar-benar baru memerlukan perubahan kode dan build APK.


## Kemampuan permanen: penalaran hitung lokal
- Tata memiliki mesin perhitungan aritmetika yang ditanam dalam source code Kotlin dan menjadi bagian dari APK.
- Mendukung operasi tambah, kurang, kali, bagi, pangkat, tanda kurung, bilangan desimal, persentase dari suatu nilai, diskon, keuntungan/kerugian berdasarkan modal dan harga jual, kenaikan/penurunan harga dalam persen, serta konversi satuan panjang (mm, cm, m, km), massa (mg, g, kg), volume (ml, l), dan waktu (detik, menit, jam, hari) (contoh: "berapa 10 persen dari 200" atau "hitung 25% dari 50"); perintah dapat menggunakan kata seperti "tambah", "dikurangi", "dikali", dan "dibagi".
- Mesin memakai parser aritmetika terbatas, bukan `eval` atau eksekusi kode bebas.
- Perhitungan berjalan di perangkat tanpa API, jaringan, atau penyimpanan ke database.
- Jika ekspresi tidak dapat dikenali, Tata tidak menebak hasil dan mengembalikan pertanyaan ke alur percakapan/riset yang sudah ada.
- Parser mengikuti prioritas operasi matematika, termasuk tanda negatif dan pangkat, serta menangani pembagian dengan nol tanpa menebak hasil.
- Pengujian otomatis JUnit mencakup operasi dasar, prioritas operasi, tanda kurung, pangkat, persentase, konversi satuan lintas kategori, penolakan satuan yang tidak sejenis, pembagian dengan nol, dan pertanyaan non-matematika. GitHub Actions menjalankan `testDebugUnitTest` sebelum membuat APK Debug.
- Contoh: "berapa 125 tambah 75", "hitung (20 dikali 5) dibagi 2", "berapa 2 pangkat 3", "berapa 10 persen dari 200", "berapa 2 km ke meter", dan "ubah 500 gram ke kilogram", "hitung diskon 10 persen dari 150000", "modal 100000 jual 125000", dan "100000 naik 10 persen".
- Ini merupakan kemampuan script, bukan pengetahuan. Menambah operasi atau mengubah cara parser bekerja memerlukan perubahan kode dan build APK.


## Mesin percakapan generatif lokal (integrasi awal)
- Tata memiliki bridge Kotlin/JNI ke `llama.cpp` yang dipatok pada commit upstream tertentu dan dikompilasi untuk Android `arm64-v8a`.
- File model GGUF dikelola melalui **Pengaturan > Model AI lokal**. Tidak ada model atau bobot AI pihak ketiga yang dibundel ke APK saat ini.
- Pengguna mengaktifkan model secara manual; konteks awal dibatasi 1.024 token dan 2 thread. Model tidak dimuat otomatis saat aplikasi dibuka.
- Saat aktif, pertanyaan terbuka dapat diteruskan ke model lokal setelah jalur perintah, pengetahuan, kalkulasi, dan percakapan khusus diperiksa.
- Model dapat dilepas dari RAM atau dihapus secara terpisah. Model besar berpotensi membuat perangkat lambat, panas, atau kehabisan RAM.
- Uji inferensi, stabilitas, konsumsi RAM, suhu, dan kualitas jawaban pada HP fisik tetap diperlukan; CI hanya membuktikan kompilasi dan packaging.

### Koreksi langsung dari percakapan

- Setiap jawaban Tata AI yang terkait dengan pertanyaan pengguna menampilkan tindakan **Koreksi jawaban Tata**.
- Tindakan tersebut membuka Learning Lab dengan pertanyaan terakhir terisi otomatis dan jawaban Tata sebelumnya ditampilkan sebagai konteks.
- Pengguna menulis koreksi sendiri; catatan hanya disimpan setelah menekan **Simpan lokal** lalu menyetujui konfirmasi.
- Koreksi tetap berstatus **Perlu ditinjau** dan tidak otomatis mengubah pengetahuan aktif maupun bobot model.
- Untuk mengaktifkan koreksi sebagai pengetahuan, pengguna harus meninjau dan menyetujui tindakan terpisah di Learning Lab.
- Konteks pertanyaan terakhir yang dipakai tombol koreksi hanya berada di memori Activity dan tidak menambah penyimpanan otomatis.
- Catatan dapat diedit kembali tanpa membuat duplikat, dan daftar dapat difilter menurut status: Perlu ditinjau, Diterima, Ditolak, atau Sudah diuji.
- Perubahan isi catatan tidak otomatis mengubah pengetahuan aktif; tindakan aktivasi tetap memerlukan persetujuan terpisah.
### Navigasi tersembunyi bergaya chat

- Navigasi utama dipindahkan dari navbar bawah ke menu tersembunyi yang dibuka melalui ikon ☰ pada header.
- Menu menyediakan akses ke percakapan, mode suara, riwayat, pengetahuan, Learning Lab, model AI lokal, dan pengaturan.
- Area bawah tidak lagi ditempati navbar; kolom percakapan dan input pesan menjadi lebih lega.
- Lingkaran spektrum/equalizer tetap dipertahankan sebagai kontrol utama interaksi suara.


## Pertukaran data Learning Lab secara manual
- Buka **Learning Lab > File** untuk mengekspor catatan pembelajaran atau menggabungkan file dari perangkat lain.
- File `tata-learning-lab.json` hanya berisi konteks, koreksi, dan sumber Learning Lab; tidak memuat percakapan, memori pribadi, pengetahuan aktif, atau model.
- Impor memeriksa duplikat dan konflik sebelum meminta konfirmasi. Duplikat identik dilewati, sedangkan koreksi berbeda untuk pertanyaan yang sama disimpan sebagai catatan terpisah berstatus **Perlu ditinjau**.
- Data yang sudah ada tidak ditimpa. Hasil impor tidak otomatis menjadi pengetahuan aktif dan tidak melatih bobot model.

- Saat koreksi diterapkan ke pengetahuan aktif, Tata mendeteksi pertanyaan yang sama terlebih dahulu. Jawaban berbeda tidak menimpa data lama; koreksi baru harus ditinjau. Status **Diterapkan** terpisah dari **Sudah diuji**.

- Format pertukaran Learning Lab versi 2 menyertakan identitas pertanyaan, hash isi, waktu pembuatan asal, jumlah catatan, dan hash payload untuk pemeriksaan integritas tanpa ID akun/perangkat.


### Pencarian web langsung (tanpa API AI)
- Pertanyaan yang tidak terjawab oleh pengetahuan lokal, penalaran, atau percakapan offline akan mencoba pencarian web jika koneksi tervalidasi tersedia.
- Tata mengambil hasil pencarian publik DuckDuckGo HTML, membaca maksimal tiga halaman HTTPS, membersihkan elemen halaman non-isi, lalu menampilkan cuplikan, isi yang berhasil dibaca, dan tautan sumber.
- Implementasi menggunakan koneksi HTTPS langsung dan parser Jsoup; tidak memakai API AI, API key, server inference, atau sinkronisasi database.
- Hasil web bukan jaminan kebenaran. Situs dapat memblokir pengambilan otomatis, mengubah struktur HTML, atau menyajikan informasi usang; pengguna perlu memeriksa sumber dan tanggalnya.
- Jika pencarian gagal atau offline, Tata melanjutkan ke model AI lokal (jika aktif), lalu menawarkan pencarian eksternal secara manual.
- Pencarian web mengonsumsi data internet dan belum melakukan penyimpanan otomatis ke pengetahuan Tata.


### Riwayat revisi pengetahuan
- Versi database 4 menambahkan tabel `knowledge_revisions` secara aditif; catatan pengetahuan yang sudah ada tidak dihapus saat migrasi.
- Saat pengetahuan diedit atau dihapus, versi sebelumnya disimpan terlebih dahulu. Perubahan yang tidak mengubah isi tidak membuat revisi kosong.
- Menu `Pengetahuan Tata` menyediakan `Riwayat` untuk melihat snapshot dan memulihkan versi lama setelah konfirmasi.
- Pemulihan menyimpan versi aktif sebelum menggantinya; jika data sebelumnya telah dihapus, pemulihan membuat kembali entri pengetahuan. Catatan revisi tetap dipertahankan.
- Riwayat ini mulai merekam perubahan sejak fitur tersedia; tidak dapat merekonstruksi perubahan sebelum fitur dipasang.
