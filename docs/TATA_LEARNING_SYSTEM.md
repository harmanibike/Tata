# Tata AI Learning System

Status: **Learning Lab lokal tahap awal sudah ditambahkan ke kode; perlu verifikasi CI dan uji perangkat sebelum dinyatakan siap.**

## Tujuan

Mengembangkan sistem pembelajaran Tata AI yang dimulai dari interaksi langsung pengguna, tetapi tetap menjaga aturan persetujuan penyimpanan yang sudah ada sejak versi awal. Setiap instalasi Tata AI mengelola data belajarnya sendiri di perangkat. Berbagi pengetahuan dan kontribusi untuk pelatihan model adalah pilihan terpisah dan tidak pernah aktif otomatis.

## Prinsip wajib

1. **Pencarian bukan persetujuan.** Hasil pencarian, teks tempelan, ringkasan sementara, dan percakapan sementara tidak otomatis masuk database pengetahuan atau data pelatihan.
2. **Persetujuan eksplisit sebelum menyimpan.** Pengguna memeriksa/mengedit informasi dan menekan Simpan. Membuka dialog, menekan Ajari Tata, atau melihat hasil pencarian belum berarti menyetujui penyimpanan.
3. **Lokal secara default.** Pengetahuan yang disimpan masuk SQLite pada perangkat. Tidak dikirim ke server.
4. **Izin terpisah untuk berbagi.** Menyimpan ke perangkat tidak berarti menyetujui pengiriman ke komunitas.
5. **Izin terpisah untuk pelatihan model.** Berbagi pengetahuan umum tidak otomatis berarti mengizinkan data itu menjadi bahan pelatihan bobot model.
6. **Data pribadi dikecualikan.** Jangan mengirim percakapan pribadi, identitas, kontak, lokasi presisi, atau informasi sensitif sebagai kontribusi komunitas.
7. **Koreksi tidak otomatis dianggap benar.** Koreksi disimpan sebagai usulan setelah disetujui, lalu dapat ditinjau, diuji, dan dibandingkan dengan sumber yang dapat dipercaya.
8. **Penghapusan harus jelas.** Pengguna dapat menghapus pengetahuan lokal dan menarik kontribusi yang masih dapat diidentifikasi sesuai kemampuan sistem distribusi. Jangan menjanjikan penghapusan dari salinan yang sudah diunduh pengguna lain.
9. **Tidak ada AI API berbayar.** Fitur inti berjalan lokal. Jika kelak ada katalog bersama, itu hanya distribusi data statis/terverifikasi, bukan layanan inferensi AI.

## Empat lapisan data yang terpisah

### 1. Informasi sementara

- Hasil riset, artikel, jawaban tempelan, dan konteks sesi.
- Berada di memori sementara.
- Tidak masuk SQLite sebagai pengetahuan atau bahan pelatihan.
- Dapat hilang saat sesi berakhir.

### 2. Pengetahuan lokal

- Disimpan ke tabel `knowledge` hanya setelah pengguna menekan Simpan.
- Menjadi sumber jawaban offline dan dapat diedit/dihapus.
- Tetap berada di perangkat dan dapat dicadangkan secara manual melalui mekanisme ekspor yang disetujui pengguna.

### 3. Riwayat pembelajaran dan evaluasi

- Menyimpan koreksi atau hasil evaluasi hanya jika pengguna secara eksplisit memilih menyimpannya.
- Mencatat asal informasi, waktu, status pemeriksaan, dan hubungan dengan pengetahuan yang diperbaiki.
- Tidak otomatis menjadi memori pribadi, kontribusi komunitas, atau data pelatihan model.
- Pengguna dapat meninjau dan menghapus catatan pembelajaran.

### 4. Kontribusi komunitas dan data pelatihan

- Dua persetujuan berbeda: (a) bagikan pengetahuan umum yang sudah disaring; (b) izinkan contoh tertentu digunakan untuk eksperimen/pelatihan model.
- Sebelum berbagi, tampilkan pratinjau persis data yang akan dikirim, tujuan, dan pilihan batal.
- Hilangkan metadata perangkat dan lakukan pemeriksaan data pribadi.
- Kontribusi ditandai dengan versi, lisensi/izin, sumber, dan status moderasi.
- Model hasil pelatihan diterbitkan sebagai versi baru yang dapat diunduh; tidak ada perubahan bobot model secara diam-diam di HP pengguna.

## Alur belajar yang diharapkan

1. Tata mencari atau menerima informasi dan menyajikannya sebagai sesi sementara.
2. Pengguna memeriksa isi dan sumber.
3. Tata menawarkan **Simpan sebagai pengetahuan lokal?**
4. Jika pengguna setuju, formulir pengetahuan dibuka untuk diperiksa dan diedit.
5. Hanya tombol **Simpan** yang menulis ke SQLite.
6. Jika pengguna melakukan koreksi, Tata dapat menawarkan **Simpan koreksi untuk evaluasi?** secara terpisah.
7. Jika pengguna ingin berbagi, Tata menampilkan persetujuan kontribusi komunitas secara terpisah.
8. Jika pengguna ingin membantu pelatihan model, Tata menampilkan persetujuan pelatihan yang terpisah lagi.

## Makna “Tata belajar” — jangan dicampur

| Mekanisme | Apa yang berubah | Kapan tersedia |
|---|---|---|
| Belajar pengetahuan | Isi database lokal `knowledge` | Setelah persetujuan simpan |
| Belajar preferensi/memori | Data personal pengguna | Setelah izin khusus |
| Belajar dari koreksi | Kumpulan contoh evaluasi lokal | Setelah persetujuan terpisah |
| Melatih model | Bobot neural network | Melalui pipeline training, evaluasi, dan rilis model baru |

Menambahkan jawaban ke SQLite membuat Tata dapat mengambil pengetahuan itu kembali, tetapi **tidak dengan sendirinya mengubah bobot neural network**. Pelatihan model memerlukan dataset yang disiapkan secara sadar, tokenizer, pipeline training, komputasi, checkpoint, evaluasi terpisah, dan penerbitan versi model.

## Arsitektur yang direncanakan

- **KnowledgeStore (SQLite lokal):** pengetahuan yang telah disetujui.
- **Learning Lab:** antarmuka untuk meninjau koreksi, sumber, dan evaluasi.
- **Consent Manager:** mencatat persetujuan per tujuan, per item, dan waktu; persetujuan berbagi/pelatihan tidak boleh disimpulkan dari persetujuan simpan lokal.
- **Contribution Exporter:** membuat paket kontribusi setelah pratinjau dan persetujuan, dengan penyaringan data pribadi.
- **Community Knowledge Catalog (opsional, tahap berikutnya):** distribusi paket pengetahuan umum yang telah ditinjau. Tidak menerima chat pribadi atau data mentah secara otomatis.
- **Tata Model Training Pipeline (riset terpisah):** mengolah contoh yang memang diizinkan untuk pelatihan, mencatat lisensi dan asal-usul data, mengevaluasi model, lalu merilis versi model baru.
- **AI Cartridge:** memasang/mengganti/melepas model; pengetahuan lokal dan riwayat pengguna tidak ikut terhapus saat model diganti.

## Tahapan implementasi

### Tahap A — pertahankan alur lama

- Jangan mengubah aturan pencarian sementara dan tombol Simpan yang sudah ada.
- Audit seluruh jalur penulisan ke `knowledge` agar tidak ada penyimpanan otomatis.
- Pastikan penghapusan semua pengetahuan tetap terpisah dari riwayat chat, pengaturan, dan model.

### Tahap B — Learning Lab lokal

Implementasi awal di APK:
- Menu **Pengaturan > Learning Lab** menampilkan catatan koreksi yang disimpan di SQLite lokal.
- Pengguna menambahkan konteks, koreksi, dan sumber opsional melalui formulir.
- Menekan **Simpan lokal** diikuti konfirmasi eksplisit menjadi satu-satunya jalur membuat catatan pembelajaran.
- Status catatan dapat diubah menjadi perlu ditinjau, diterima, ditolak, diterapkan ke pengetahuan aktif, atau sudah diuji.
- Catatan yang diterima baru dapat dimasukkan ke pengetahuan aktif melalui tindakan terpisah dan konfirmasi.
- Catatan dapat dihapus tanpa menghapus pengetahuan aktif.
- Migrasi database versi 2 ke 3 bersifat aditif dan membuat tabel learning_records tanpa menghapus data lama.

Pekerjaan lanjutan:
- Hubungkan koreksi langsung dari percakapan dengan pratinjau sebelum penyimpanan.
- [x] Tambahkan edit catatan dan filter status di Learning Lab; perubahan tetap lokal dan tidak mengaktifkan pengetahuan otomatis.
- [x] Tambahkan ekspor/impor paket koreksi lokal dengan pratinjau, validasi, dan konfirmasi sebelum menggabungkan catatan.
- Tambahkan uji regresi untuk memastikan koreksi tidak merusak pengetahuan lain serta pengujian migrasi/penyimpanan pada perangkat.

### Tahap C — kontribusi komunitas opsional

- Rancang ekspor paket pengetahuan umum yang telah dipilih satu per satu.
- Tampilkan pratinjau dan izin berbagi sebelum file dikirim.
- Mulai dari impor/ekspor paket manual; sinkronisasi online hanya setelah server, autentikasi, moderasi, keamanan, dan kebijakan privasi dirancang.
- Tidak menjadikan server sebagai syarat penggunaan offline.

### Tahap D — pelatihan Tata Model

- Siapkan korpus legal dan contoh instruksi yang memiliki izin pelatihan.
- Pisahkan data training, validation, dan test.
- Mulai dari eksperimen kecil dari bobot acak; simpan seed, konfigurasi, manifest sumber, checkpoint, dan metrik.
- Latih dan evaluasi di komputer yang memadai; HP menjadi perangkat inferensi dan pengujian, bukan tempat utama melatih model besar.
- Terbitkan model baru hanya jika evaluasi menunjukkan peningkatan dan tidak ada regresi penting.

## Kriteria penerimaan

- Tidak ada hasil pencarian/teks tempelan yang tersimpan tanpa persetujuan eksplisit.
- Menyimpan lokal tidak pernah otomatis mengirim data.
- Persetujuan berbagi dan persetujuan pelatihan model ditampilkan secara terpisah.
- Pengguna dapat melihat, mengedit, dan menghapus pengetahuan lokal.
- Pergantian atau penghapusan model tidak menghapus pengetahuan dan riwayat lokal.
- Setiap klaim model telah belajar dari pelatihan harus didukung checkpoint, manifest data, metrik evaluasi, dan pengujian inferensi.
- Build CI lulus dan perilaku penyimpanan diuji pada perangkat Android nyata sebelum fitur dinyatakan siap.

## Keputusan awal

Mulai dari **audit Tahap A dan validasi implementasi awal Tahap B**. Jangan mengaktifkan sinkronisasi komunitas atau pelatihan dari data pengguna secara otomatis. Pertahankan aplikasi yang sekarang tetap berfungsi; perubahan skema database harus aditif dan disertai migrasi serta pengujian agar data lama tidak hilang.


## Paket pertukaran Learning Lab (implementasi awal)

- Format file JSON khusus dengan identitas `tata-learning-lab` dan `schemaVersion: 1`.
- Ekspor hanya memuat konteks pertanyaan, koreksi, dan sumber opsional dari Learning Lab. Riwayat percakapan, memori pribadi, pengetahuan aktif, dan model tidak disertakan.
- Impor dibatasi maksimal 5 MB dan 5.000 catatan; setiap item divalidasi panjang dan kelengkapan teksnya.
- Sidik jari SHA-256 dibuat dari pertanyaan dan koreksi yang dinormalisasi (huruf besar/kecil dan spasi). Sumber tidak mengubah identitas isi.
- Duplikat identik dilewati. Pertanyaan yang sama dengan koreksi berbeda diperlakukan sebagai konflik dan disimpan sebagai catatan terpisah, bukan menimpa catatan lama.
- Semua catatan hasil impor selalu berstatus **Perlu ditinjau**, sekalipun catatan sumber sebelumnya sudah diterima atau diuji. Penerima harus memeriksa ulang.
- Penggabungan memerlukan pratinjau dan konfirmasi. Data lama tidak dihapus dan tidak ada data yang otomatis menjadi pengetahuan aktif.
- Pendeteksian konflik saat ini memakai pertanyaan yang sama setelah normalisasi teks; belum mendeteksi pertanyaan parafrasa/semakna secara semantik.
- Pengujian unit mencakup duplikat identik, konflik koreksi, serta perlindungan agar data lama tidak berubah. Uji database Android dan uji alur pemilih file di perangkat masih diperlukan.


### Pengamanan saat menerapkan koreksi ke pengetahuan aktif

- Tata memeriksa apakah pertanyaan yang sama sudah ada di pengetahuan aktif.
- Jika pertanyaan dan jawaban identik, catatan ditandai **Diterapkan** tanpa membuat salinan baru.
- Jika pertanyaannya sama tetapi jawabannya berbeda, Tata menolak penimpaan otomatis dan meminta pengguna menyelesaikan konflik dari pengelola pengetahuan.
- Jika belum ada pertanyaan yang sama, pengguna harus mengonfirmasi penambahan. Status menjadi **Diterapkan**, bukan otomatis **Sudah diuji**.
- Pengguna dapat menandai sudah diuji setelah mencoba jawaban. Mengedit catatan yang sudah diterapkan/diuji mengembalikannya ke **Perlu ditinjau**, karena koreksi terbaru belum tentu sama dengan pengetahuan aktif.
