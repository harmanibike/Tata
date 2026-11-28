# Tata Transformer v0 — spesifikasi eksperimen

**Status: rancangan arsitektur, belum ada implementasi pelatihan atau bobot model.**

Dokumen ini menetapkan baseline kecil untuk membuktikan pipeline model bahasa Tata dari bobot acak. Angka konfigurasi adalah titik awal eksperimen, bukan klaim performa atau keputusan final.

## Tujuan dan batas
- Decoder-only Transformer autoregresif: memprediksi token berikutnya dari token sebelumnya.
- Bobot diinisialisasi acak dan dilatih dari awal menggunakan korpus yang sumber serta izinnya sudah ditinjau.
- Tidak menggunakan bobot pretrained, GGUF pihak ketiga, API inferensi, atau server AI.
- Pelatihan dilakukan di komputer lokal yang memadai; Android hanya menjadi target inferensi setelah model dievaluasi.
- Model pertama hanya proof-of-pipeline. Jangan mengharapkan kemampuan asisten umum atau kualitas setara model besar.

## Konfigurasi baseline yang akan diuji

| Parameter | Nilai awal |
|---|---:|
| Vocabulary | 4.096 token (sementara) |
| Hidden size | 128 |
| Transformer blocks | 4 |
| Attention heads | 4 |
| Head dimension | 32 |
| Feed-forward size | 512 |
| Context length | 256 token |
| Positional encoding | learned positional embedding |
| Normalization | pre-LayerNorm |
| Activation | GELU |
| Attention | causal multi-head self-attention |
| Output head | tied token embedding / language-model head |
| Objective | next-token cross entropy |

Perkiraan jumlah parameter sekitar 1,3 juta dengan embedding output terikat. Angka aktual harus dihitung dari implementasi dan konfigurasi final.

## Alur forward
1. Token ID dipetakan ke token embedding.
2. Positional embedding ditambahkan pada setiap posisi.
3. Empat blok Transformer pre-norm memproses urutan dengan causal mask agar token tidak melihat masa depan.
4. Layer normalization akhir diterapkan.
5. Language-model head menghasilkan logits untuk seluruh kosakata.
6. Loss dihitung hanya pada target token berikutnya yang valid; padding diabaikan.

## Data dan pencegahan kebocoran
- Deduplikasi dilakukan sebelum split.
- Seluruh contoh dari `source_id` yang sama harus berada pada split yang sama.
- Hanya contoh yang lulus privacy_review dan quality_review yang dapat masuk split.
- Contoh pending tetap unassigned dan tidak boleh dipakai untuk tokenizer maupun pelatihan.
- Test set disimpan terpisah dan tidak digunakan untuk memilih hyperparameter.
- Simpan hash korpus, manifest, tokenizer hash, seed, konfigurasi, versi kode, dan checkpoint.

## Pelatihan awal
- Gunakan seed yang tercatat untuk inisialisasi dan urutan batch.
- Optimizer awal: AdamW; learning rate, warmup, batch size, dan gradient clipping ditentukan lewat eksperimen kecil, bukan ditebak sebagai nilai final.
- Simpan checkpoint berkala dan catat train loss serta validation loss.
- Hentikan eksperimen jika validation loss terus memburuk atau ada indikasi data leakage.
- Jangan menyatakan model belajar pengetahuan umum hanya karena training loss turun.

## Evaluasi wajib
- Perplexity dan validation loss pada data yang tidak dilatih.
- Evaluasi generasi Bahasa Indonesia: tata bahasa, relevansi, konsistensi, kemampuan mengatakan tidak tahu.
- Pemeriksaan halusinasi, bias, data pribadi, konten berbahaya, dan pengulangan.
- Ukuran bobot, kecepatan token/detik, peak RAM, panas, dan baterai pada perangkat target.
- Model tidak boleh masuk APK produksi sebelum evaluasi dan uji perangkat fisik.

## Gerbang berikutnya
Sebelum menulis loop training, selesaikan dan tinjau: validator, deduplikasi, split berbasis sumber, tokenizer, korpus legal yang memadai, serta evaluasi baseline. Implementasi PyTorch atau runtime lain harus ditinjau lisensi, ukuran, dan kemampuan perangkat pelatihan terlebih dahulu.
