# Tata AI

Asisten AI Android ringan dengan pendekatan **offline-first**. Tata dirancang sebagai satu aplikasi Flutter yang dapat menggunakan AI lokal saat offline dan layanan AI online saat koneksi tersedia.

## Target awal

- Android, satu APK.
- Baseline pengujian: perangkat RAM 4 GB.
- Tetap menyediakan fitur dasar pada perangkat Android dengan spesifikasi rendah.
- Mode AI: Otomatis, Offline, dan Online.
- Riwayat percakapan disimpan secara lokal.
- Source code dan build APK dikelola melalui GitHub Actions.

> **Catatan kemampuan perangkat:** RAM 4 GB adalah target desain/pengujian, bukan jaminan semua perangkat dapat menjalankan model generatif lokal. Model lokal akan diunduh terpisah dari APK dan hanya diaktifkan setelah pemeriksaan kompatibilitas. Pada perangkat yang tidak sanggup menjalankan model, fitur offline dasar tetap tersedia.

## Prinsip arsitektur

1. **Flutter UI** — antarmuka chat dan pengaturan.
2. **AI Router** — memilih mesin AI berdasarkan mode, koneksi, dan kesiapan model lokal.
3. **Local AI** — integrasi runtime lokal (rencana: llama.cpp + model GGUF kecil/terkuantisasi).
4. **Online AI** — adapter API yang dapat diganti penyedianya.
5. **Local storage** — SQLite untuk percakapan dan pengaturan non-rahasia.

API key rahasia tidak boleh ditanam di APK atau di-commit ke repository. Untuk penggunaan pribadi, konfigurasi lokal dapat disediakan; untuk distribusi publik, gunakan backend perantara yang menjaga secret.

## Perilaku mode otomatis

- Jika online tersedia dan konfigurasi online valid, gunakan layanan online.
- Jika online gagal/terputus, beralih ke model lokal hanya jika model sudah terpasang dan perangkat kompatibel.
- Jika model lokal tidak tersedia, tampilkan status offline dan fitur lokal dasar; jangan berpura-pura menghasilkan jawaban AI.
- Riwayat percakapan tetap tersimpan di perangkat.
- Pengguna tetap dapat memilih mode Offline atau Online secara manual.

## Tahapan pengembangan

- [x] Tahap 0 — Tetapkan nama, target, dan prinsip arsitektur.
- [ ] Tahap 1 — Fondasi Flutter, tema Tata, navigasi, dan build APK pertama.
- [ ] Tahap 2 — Chat UI, SQLite, dan riwayat percakapan.
- [ ] Tahap 3 — AI Router dan status koneksi.
- [ ] Tahap 4 — Integrasi AI online melalui adapter aman.
- [ ] Tahap 5 — Integrasi AI lokal dan pemeriksaan kemampuan perangkat.
- [ ] Tahap 6 — Mode otomatis, fallback, dan pengujian offline/online.
- [ ] Tahap 7 — Optimasi RAM, baterai, ukuran APK, dan kompatibilitas Android.
- [ ] Tahap 8 — GitHub Actions, rilis APK, dokumentasi, dan audit akhir.

Setiap tahap harus diuji sebelum dinyatakan selesai.

## Kriteria kualitas

- Aplikasi dapat dibuka tanpa koneksi internet.
- Chat dan riwayat lokal tidak hilang setelah aplikasi ditutup.
- Mode Online memberi pesan yang jelas jika API/koneksi gagal.
- Mode Offline tidak membutuhkan koneksi.
- Perpindahan mode tidak menghapus percakapan.
- Tidak ada API key rahasia di source code atau APK.
- Build Android dapat direproduksi melalui GitHub Actions.

## Status

Repository ini adalah proyek khusus Tata AI dan terpisah dari proyek aplikasi lain.
