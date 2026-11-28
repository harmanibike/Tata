# Penilaian jalur inferensi neural network Tata di Android

**Status: jalur ekspor ONNX eksperimental dan tokenizer Kotlin sudah tersedia; runtime ONNX Android belum diintegrasikan atau divalidasi.** Dokumen ini bukan klaim bahwa model Tata sudah siap digunakan di Android.

## Temuan dari kode saat ini

| Area | Kondisi yang terverifikasi |
|---|---|
| Android | `minSdk 26`, `targetSdk 34`, Java/Kotlin JVM 17 |
| ABI native | Hanya `arm64-v8a` yang dikonfigurasi |
| Native build | CMake 3.22.1; C++17 |
| Runtime native yang ada | `llama.cpp` pada commit yang dipatok di CMake |
| Format model yang diimpor | GGUF saja; penyimpanan lokal memfilter ekstensi `.gguf` |
| JNI | Memanggil API `llama_model_load_from_file`, tokenizer, context, sampler dari llama.cpp |
| Model Tata dari training | Checkpoint PyTorch `.pt` dan tokenizer Character-BPE JSON |
| Ekspor bobot Tata | Eksperimental melalui `tools/model/export_transformer_onnx.py`; diuji di CI pada model sintetis |
| Tokenizer Kotlin Character-BPE | Implementasi parser JSON tersedia; unit test parity dengan fixture sintetis Python tersedia |
| Inferensi Transformer Tata di Android | Belum tersedia; ONNX Runtime Android belum ditambahkan |

### Kesimpulan penting

Runtime `llama.cpp` yang sudah ada adalah jalur kompatibilitas GGUF. Ia **tidak otomatis dapat memuat checkpoint PyTorch Tata**. Mengubah ekstensi `.pt` menjadi `.gguf` juga tidak mengubah format atau arsitektur bobot. Jalur GGUF pihak ketiga tetap terpisah dari model Tata yang dilatih dari bobot acak.

Trainer Tata membuat decoder-only Transformer dengan causal attention, learned positional embedding, pre-LayerNorm, GELU, dan tied embedding. Tokenizer Character-BPE kini memiliki implementasi Kotlin dan tes fixture lintas bahasa. Tes tersebut membuktikan kontrak pada fixture yang diuji, bukan parity untuk seluruh kemungkinan teks atau bukti kualitas model.

## Arah evaluasi runtime

Kandidat pertama yang sedang dievaluasi adalah ekspor model Tata ke ONNX lalu menjalankannya melalui ONNX Runtime Android CPU. Ini **belum merupakan keputusan final** dan belum ditambahkan sebagai dependensi aplikasi.

Proof-of-concept berikutnya harus membuktikan:

1. Ekspor dari checkpoint PyTorch Tata menghasilkan graph yang valid dan dapat dimuat oleh runtime Android.
2. Operasi causal attention dan seluruh tensor dapat diekspor tanpa mengubah semantik model.
3. Logits dari PyTorch dan ONNX cocok dalam toleransi numerik yang ditetapkan.
4. Tokenizer Kotlin menghasilkan urutan token ID yang sama dengan Python untuk fixture dan kumpulan uji Bahasa Indonesia yang diperluas.
5. Proses autoregresif satu token demi satu token berjalan tanpa mengirim teks atau bobot ke jaringan.
6. Ukuran file, RAM puncak, waktu token pertama, token/detik, panas, dan baterai diukur pada perangkat fisik.
7. Lisensi, ukuran AAR/native library, ABI, serta kompatibilitas Android minimum diverifikasi sebelum dependency masuk ke APK.

Alternatif seperti menulis runtime C++ Tata sendiri tidak dipilih sekarang karena menambah beban implementasi dan pengujian numerik. Menggunakan llama.cpp untuk model Tata juga tidak diasumsikan mungkin tanpa jalur konversi sah dan tervalidasi.

## Urutan implementasi yang aman

1. **Kontrak model dan tokenizer.** Manifest, urutan tensor, dtype, vocabulary/token ID, special token, batas konteks, dan checksum.
2. **Ekspor eksperimental Python.** Mulai dari checkpoint sintetis kecil; jangan mengklaimnya sebagai model produksi.
3. **Kesetaraan numerik.** Bandingkan logits PyTorch dan ONNX Runtime untuk batch dan panjang urutan berbeda.
4. **Tokenizer Kotlin.** Implementasi JSON dan parity fixture sintetis tersedia; perluasan pengujian masih diperlukan.
5. **Modul inferensi Android terisolasi.** Tidak mengganti jalur GGUF yang ada.
6. **Uji perangkat nyata.** Catat RAM, latency, throughput, ukuran model, stabilitas, dan baterai.
7. **Keputusan paket dan rilis.** Model yang gagal validasi/checksum/arsitektur tidak boleh menggantikan model aktif.

## Batasan dan gerbang yang masih terbuka

- Belum ada korpus produksi berizin yang lolos review; training model produksi belum boleh dimulai.
- Checkpoint CI berasal dari data sintetis dan hanya menguji pipeline, bukan kualitas model.
- Belum ada bobot Tata produksi terlatih untuk diekspor.
- ONNX Runtime Android belum diintegrasikan dan belum diuji pada perangkat.
- Belum ada pengukuran performa inferensi model Tata pada perangkat target fisik.
- `minSdk 26` berarti konfigurasi proyek menargetkan Android 8.0 ke atas; jangan menyatakan dukungan Android 6/7 tanpa perubahan dan pengujian.
- Jalur model Tata yang baru harus tetap tanpa permintaan jaringan dan diuji offline.

## Kriteria selesai

Tahap inferensi Android baru dapat disebut selesai jika ekspor, parity tokenizer pada kumpulan uji yang memadai, kesetaraan numerik, runtime offline, validasi paket, dan uji performa perangkat fisik semuanya lulus. Sampai itu terjadi, mesin aturan Kotlin dan jalur GGUF yang ada tidak boleh disebut sebagai neural network Tata hasil training sendiri.

## Eksperimen ekspor ONNX yang tersedia di branch

Skrip `tools/model/export_transformer_onnx.py` menyediakan ekspor eksperimental dari checkpoint `best_validation`. Skrip hanya menerima checkpoint dengan asal bobot tercatat sebagai random initialization, memuatnya memakai `weights_only=True`, memeriksa ukuran vocabulary dan hash tokenizer terhadap metadata training, memeriksa konsistensi hash register sumber train/validation, lalu membuat graph ONNX opset 17 dan manifest checksum.

Contoh pemakaian setelah tersedia checkpoint dan tokenizer hasil pipeline training yang sah:

```bash
python -m pip install torch onnx
python -m tools.model.export_transformer_onnx \
  --checkpoint runs/tata.best.pt \
  --tokenizer artifacts/tokenizer.json \
  --output artifacts/tata-experimental.onnx
```

Manifest berstatus `experimental_not_android_validated`. Ekspor yang lolos ONNX checker belum membuktikan kesetaraan numerik dengan PyTorch, kompatibilitas ONNX Runtime Android, atau kelayakan performa perangkat. Jangan masukkan artefak ini ke APK produksi sebelum seluruh gerbang tersebut lulus.
