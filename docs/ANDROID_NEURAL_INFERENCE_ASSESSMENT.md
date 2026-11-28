# Penilaian jalur inferensi neural network Tata di Android

**Status: audit struktur selesai; runtime untuk model Transformer Tata sendiri belum dipilih atau diimplementasikan.** Dokumen ini adalah keputusan teknis sementara, bukan klaim bahwa model sudah dapat berjalan di Android.

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
| Ekspor bobot Tata | Belum tersedia |
| Tokenizer Kotlin yang cocok dengan Character-BPE Tata | Belum tersedia |
| Inferensi Transformer Tata di Android | Belum tersedia |

### Kesimpulan penting

Runtime `llama.cpp` yang sudah ada adalah jalur kompatibilitas GGUF. Ia **tidak otomatis dapat memuat checkpoint PyTorch Tata**. Mengubah ekstensi `.pt` menjadi `.gguf` juga tidak mengubah format atau arsitektur bobot. Jalur GGUF pihak ketiga tetap terpisah dari model Tata yang dilatih dari bobot acak.

Trainer Tata saat ini membuat decoder-only Transformer dengan causal attention, learned positional embedding, pre-LayerNorm, GELU, dan tied embedding. Tokenizer yang tersedia adalah Character-BPE eksperimental berbasis JSON. Android belum memiliki implementasi tokenizer pasangan yang menjamin ID token identik dengan Python.

## Arah evaluasi runtime

Kandidat pertama yang layak diuji adalah ekspor model Tata ke ONNX lalu menjalankannya melalui ONNX Runtime Android CPU. Ini **belum merupakan keputusan final** dan belum ditambahkan sebagai dependensi aplikasi.

Sebelum memilihnya, proof-of-concept harus membuktikan semua hal berikut:

1. Ekspor dari checkpoint PyTorch Tata menghasilkan graph yang valid dan dapat dimuat oleh runtime Android yang sesuai.
2. Operasi causal attention dan seluruh tensor dapat diekspor tanpa mengubah semantik model.
3. Logits dari PyTorch dan ONNX cocok dalam toleransi numerik yang ditetapkan.
4. Tokenizer Kotlin menghasilkan urutan token ID yang sama persis dengan tokenizer Python untuk kumpulan uji Bahasa Indonesia.
5. Proses autoregresif satu token demi satu token dapat berjalan tanpa mengirim teks atau bobot ke jaringan.
6. Ukuran file, RAM puncak, waktu token pertama, token/detik, panas, dan baterai diukur pada perangkat fisik.
7. Lisensi, ukuran AAR/native library, ABI, serta kompatibilitas Android minimum diverifikasi sebelum dependency masuk ke APK.

Alternatif seperti menulis runtime C++ Tata sendiri tidak dipilih sekarang karena akan menambah beban implementasi dan pengujian numerik. Menggunakan llama.cpp untuk model Tata juga tidak diasumsikan mungkin sebelum ada jalur konversi yang sah, tervalidasi, dan tetap mempertahankan arsitektur serta tokenizer Tata.

## Urutan implementasi yang aman

1. **Kunci kontrak model dan tokenizer.** Definisikan manifest model, urutan tensor, dtype, vocabulary/token ID, special token, normalisasi teks, batas konteks, dan checksum.
2. **Buat ekspor eksperimental di sisi Python.** Mulai dari checkpoint sintetis kecil untuk menguji ekspor; jangan memakai atau mengklaim model produksi.
3. **Uji kesetaraan numerik.** Bandingkan logits PyTorch dan hasil runtime target pada input tetap, termasuk batch dan panjang urutan yang berbeda.
4. **Buat tokenizer Kotlin dan tes parity.** Fixture uji mencakup imbuhan, reduplikasi, rupiah, tanggal/jam, emoji, newline, serta penanda role.
5. **Buat modul inferensi Android terisolasi.** Tidak mengganti jalur GGUF yang ada; pengguna tetap dapat memakai fitur lama.
6. **Uji pada perangkat nyata.** Catat RAM, latency, throughput, ukuran model, stabilitas, dan konsumsi baterai sebelum integrasi UI.
7. **Baru putuskan paket model dan rilis.** Model yang gagal validasi/checksum/arsitektur tidak boleh menggantikan model aktif.

## Batasan dan gerbang yang masih terbuka

- Belum ada korpus produksi yang berizin dan lolos review, sehingga training model produksi belum boleh dimulai.
- Checkpoint CI berasal dari simulasi data sintetis dan hanya menguji pipeline training/resume.
- Belum ada bobot Tata terlatih untuk diekspor.
- Belum ada perangkat target fisik yang diuji untuk inferensi model Tata.
- `minSdk 26` berarti konfigurasi proyek saat ini menargetkan Android 8.0 ke atas; jangan menyatakan dukungan Android 6/7 tanpa perubahan dan pengujian terpisah.
- Izin `INTERNET` di manifest aplikasi tidak berarti inferensi model memakai cloud. Namun, jalur model Tata yang baru harus tetap tidak memiliki permintaan jaringan dan diuji offline.

## Kriteria selesai untuk tahap ini

Tahap inferensi Android baru dapat disebut selesai jika ekspor, tokenizer parity, kesetaraan numerik, runtime offline, validasi paket, dan uji performa perangkat fisik semuanya lulus. Sampai itu terjadi, mesin aturan Kotlin dan jalur GGUF yang ada tidak boleh disebut sebagai neural network Tata hasil training sendiri.


## Eksperimen ekspor ONNX yang tersedia di branch

Skrip `tools/model/export_transformer_onnx.py` kini menyediakan ekspor eksperimental dari checkpoint `best_validation`. Skrip hanya menerima checkpoint dengan asal bobot yang tercatat sebagai random initialization, memuatnya memakai `weights_only=True`, memeriksa ukuran vocabulary dan hash tokenizer terhadap metadata training, memeriksa konsistensi hash register sumber train/validation, lalu membuat graph ONNX opset 17 dan manifest checksum.

Contoh pemakaian setelah tersedia checkpoint dan tokenizer hasil pipeline training yang sah:

```bash
python -m pip install torch onnx
python -m tools.model.export_transformer_onnx \
  --checkpoint runs/tata.best.pt \
  --tokenizer artifacts/tokenizer.json \
  --output artifacts/tata-experimental.onnx
```

Manifest berstatus `experimental_not_android_validated`. Ekspor yang berhasil dan graph yang lolos ONNX checker **belum** membuktikan kesetaraan numerik dengan PyTorch, kompatibilitas ONNX Runtime Android, parity tokenizer Kotlin, atau kelayakan performa perangkat. Jangan masukkan artefak ini ke APK produksi sebelum seluruh gerbang tersebut lulus.
