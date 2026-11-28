# Tata Transformer v0 — spesifikasi eksperimen

**Status: spesifikasi dan loop training awal tersedia; belum dijalankan pada korpus berizin dan belum ada bobot model terlatih.**

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


## Estimator konfigurasi dan parameter

Script `tools/model/transformer_config.py` memvalidasi dimensi konfigurasi dan menghitung estimasi parameter secara analitis tanpa framework machine learning. Baseline saat ini menghasilkan **1.350.400 parameter** dengan token embedding dan output head yang diikat (tied embeddings), bias aktif, dan dimensi pada tabel di atas.

Perintah:
```bash
python tools/model/transformer_config.py
python tools/model/transformer_config.py --output transformer-config-report.json
```

Laporan menyertakan rincian embedding, blok Transformer, normalisasi, dan output head. Angka ini adalah hitungan konfigurasi, bukan bukti bahwa model, bobot, atau proses pelatihan sudah dibuat. Implementasi aktual kelak harus mencocokkan hitungan ini dan diuji dengan framework yang dipilih.


## Implementasi training awal

Implementasi tersedia pada `tools/model/train_transformer.py` dan loader data pada `tools/model/training_data.py`.

- Loader hanya menerima split `train` dan `validation`, serta hanya contoh dengan `privacy_review=passed` dan `quality_review=passed`. Split `test` tidak dapat dimuat oleh loader.
- Model dibuat dari bobot acak. Implementasi memakai causal scaled-dot-product attention, pre-LayerNorm, GELU, tied embedding, dan prediksi token berikutnya.
- Loop training menggunakan AdamW, gradient clipping, validation loss/perplexity, dan checkpoint yang ditulis atomik.
- Vocabulary model diambil dari tokenizer aktual. Hitungan parameter runtime harus sama dengan estimator; jika berbeda, proses dihentikan.
- Checkpoint mengikat hash korpus train, korpus validation, dan tokenizer. RNG serta optimizer disimpan untuk mendukung resume yang lebih konsisten.
- PyTorch 2.x diperlukan hanya pada komputer lokal untuk pelatihan. CI fondasi tidak mengunduh PyTorch dan tidak menjalankan training.
- Belum ada pelatihan nyata: korpus sintetis yang ada masih pending dan tidak memenuhi syarat. Tidak ada model siap pakai atau bobot yang dapat diimpor ke Android.

Contoh perintah setelah dataset benar-benar disetujui:
```bash
python tools/corpus/train_tokenizer.py corpus-split.jsonl --output tata-tokenizer.json --vocab-size 4096
python tools/model/train_transformer.py --corpus corpus-split.jsonl --tokenizer tata-tokenizer.json --output checkpoints/tata-v0.pt --steps 1000 --device auto
```


## Evaluator held-out test

Script `tools/model/evaluate_transformer.py` memuat checkpoint dan menghitung loss/perplexity pada split `test` yang telah lolos tinjauan. Data test tidak pernah dimuat oleh loop training. Evaluator juga dapat menghasilkan contoh greedy dari prompt manual, tetapi hasilnya hanya untuk inspeksi manusia dan tidak membuktikan kebenaran atau keamanan. Laporan mencatat hash korpus/tokenizer dan jumlah token yang dinilai. Perplexity harus dilengkapi evaluasi kualitatif Bahasa Indonesia, halusinasi, privasi, bias, repetisi, dan uji perangkat.
