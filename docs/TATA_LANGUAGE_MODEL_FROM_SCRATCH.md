# Tata Language Model — Rencana Model Bahasa dari Nol

Status: **rencana riset dan prototipe; belum ada model Tata hasil pelatihan**.

## Tujuan
Membangun model bahasa generatif milik proyek Tata AI dengan bobot yang dilatih dari inisialisasi acak, bukan mengambil bobot Qwen, Llama, SmolLM, atau model pihak ketiga lalu mengganti nama. Model harus dapat dijalankan lokal/offline di Android dan tidak memakai API AI eksternal.

## Batas realistis
- Melatih model dari nol memerlukan korpus teks yang legal, bersih, beragam, tokenizer, kode training, perangkat komputasi, evaluasi, dan banyak iterasi.
- HP Android target bukan perangkat yang realistis untuk melatih model bahasa generatif berkualitas. HP menjadi perangkat inferensi/pengujian; pelatihan awal dilakukan di komputer yang memiliki RAM/GPU memadai. Jika perangkat pelatihan belum tersedia, mulai dari eksperimen CPU kecil untuk membuktikan pipeline, bukan menjanjikan kualitas asisten umum.
- Model 100–200 juta parameter bukan otomatis cukup pintar atau cukup kecil untuk semua HP. Ukuran file bergantung pada jumlah parameter dan presisi/kuantisasi; RAM runtime juga mencakup cache konteks dan runtime.
- Model kecil hasil training dari nol kemungkinan jauh di bawah model matang yang telah dilatih pada korpus besar. Kualitas harus diukur, bukan diasumsikan.
- Tidak menjanjikan setara ChatGPT dan tidak mengklaim model sudah ada sebelum checkpoint, evaluasi, dan inferensi nyata tersedia.

## Prinsip tetap
1. Tanpa API AI eksternal, tanpa kunci API, dan tanpa layanan AI berbayar.
2. Tidak mengunduh atau memakai bobot model pihak ketiga sebagai dasar model Tata.
3. Data pelatihan harus memiliki lisensi/izin yang sesuai; simpan manifest sumber, lisensi, bahasa, dan proses pembersihannya.
4. Pisahkan kemampuan aplikasi Kotlin, bobot model, pengetahuan SQLite, memori pengguna yang memerlukan izin, dan konteks sementara.
5. Pengguna tidak perlu mengimpor GGUF secara manual pada rancangan akhir. Tata AI akan mengelola paket model/cartridge sendiri.
6. Satu model aktif pada satu waktu untuk menekan RAM. Model lain dapat disimpan, diganti, atau dilepas secara aman.
7. Jangan menghapus model, pengetahuan, memori, atau riwayat tanpa persetujuan yang sesuai.

## Tahapan pengembangan

### Tahap 0 — spesifikasi dan kelayakan
- Tetapkan target bahasa utama: Bahasa Indonesia, dengan dukungan istilah umum dan percakapan sehari-hari.
- Susun daftar kemampuan yang diuji: melanjutkan teks, menjawab instruksi sederhana, menjaga konteks pendek, menolak mengarang ketika tidak tahu, dan mengikuti identitas Tata.
- Tentukan batas ukuran file, RAM, panjang konteks, waktu respons, dan perangkat uji.
- Buat dataset evaluasi terpisah dari data pelatihan untuk mencegah penilaian yang menipu.

### Tahap 1 — pipeline model mini
- Implementasikan tokenizer dan decoder-only Transformer kecil sebagai eksperimen yang dapat direproduksi.
- Mulai dari konfigurasi sangat kecil untuk memverifikasi data loader, training loop, checkpoint, resume, sampling, dan evaluasi.
- Latih dari bobot acak menggunakan korpus yang hak penggunaannya jelas.
- Simpan konfigurasi, seed, versi kode, manifest dataset, metrik loss, dan checkpoint.
- Jangan mengemas model eksperimen ke APK utama sebelum kualitas dan stabilitasnya layak.

### Tahap 2 — model Tata Mini
- Tingkatkan skala hanya jika pipeline, dataset, dan sumber komputasi terbukti memadai.
- Lakukan pretraining, lalu instruction tuning menggunakan contoh instruksi/percakapan yang berizin dan ditinjau.
- Evaluasi bahasa Indonesia, halusinasi, bias, keamanan, kepatuhan instruksi, konsistensi, dan kualitas percakapan multi-turn.
- Bandingkan beberapa ukuran/kuantisasi dengan pengukuran nyata, bukan perkiraan ukuran saja.

### Tahap 3 — runtime Android
- Rancang format ekspor dan runtime lokal yang cocok dengan model Tata.
- Integrasikan melalui JNI/NDK ke aplikasi Android setelah format dan runtime dipilih.
- Uji ABI, versi Android, waktu muat, RAM puncak, suhu, baterai, pembatalan generasi, dan crash pada perangkat nyata.
- Pertahankan fallback ke kemampuan Kotlin lokal ketika model tidak tersedia atau gagal dimuat.

### Tahap 4 — Tata AI Cartridge
- Katalog model menampilkan nama, versi, ukuran, checksum, lisensi, kebutuhan RAM perkiraan, dan status kompatibilitas.
- Instalasi, verifikasi checksum, aktivasi, pergantian, unload, dan penghapusan memerlukan status yang jelas dan konfirmasi untuk penghapusan.
- Satu model aktif pada satu waktu. Pergantian model membersihkan cache inferensi dan menyesuaikan konteks; pengetahuan dan riwayat yang disimpan terpisah tidak ikut terhapus.
- Model bawaan APK hanya diputuskan setelah ukuran akhir, lisensi distribusi, dan kompatibilitas perangkat diketahui. Opsi awal yang lebih realistis adalah satu APK Tata AI dengan pengelola unduhan model resmi, tanpa meminta pengguna mencari file GGUF sendiri.

## Gerbang kelulusan
Jangan menyebut Tata Mini siap digunakan sebelum:
- bobot benar-benar dilatih dari inisialisasi acak dan asal-usulnya terdokumentasi;
- dataset dan lisensinya terdokumentasi;
- checkpoint dapat dimuat ulang dan menghasilkan teks;
- evaluasi terpisah menunjukkan kemampuan yang berguna;
- runtime Android lulus uji perangkat nyata;
- penggunaan RAM, suhu, baterai, dan stabilitas memenuhi target perangkat.

## Keputusan awal
Mulai dari Tahap 0 dan Tahap 1. Jangan mengganti mesin llama.cpp/JNI yang sudah ada atau merusak APK saat ini. Bangun pipeline riset terpisah terlebih dahulu. Integrasi ke Tata AI dilakukan setelah model eksperimental benar-benar terbukti.
