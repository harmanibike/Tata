package com.harmanibike.tata.onnxpoc

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.TextView
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.io.File
import java.nio.LongBuffer

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = "Tata ONNX PoC\\n\\nMenjalankan pengujian lokal..."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        setContentView(message)

        Thread {
            val status = runDeviceBenchmark()
            runOnUiThread { message.text = status }
        }.start()
    }

    private fun runDeviceBenchmark(): String {
        val modelFile = File(cacheDir, "tata_transformer_synthetic.onnx")
        return try {
            val copyStart = SystemClock.elapsedRealtimeNanos()
            assets.open("tata_transformer_synthetic.onnx").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
            val copyMs = (SystemClock.elapsedRealtimeNanos() - copyStart) / 1_000_000.0

            val environment = OrtEnvironment.getEnvironment()
            val openStart = SystemClock.elapsedRealtimeNanos()
            TataOnnxRuntimeProbe.open(modelFile).use { probe ->
                val openMs = (SystemClock.elapsedRealtimeNanos() - openStart) / 1_000_000.0
                check(probe.inputNames == setOf("input_ids")) { "Nama input model tidak sesuai" }
                check(probe.outputNames == setOf("logits")) { "Nama output model tidak sesuai" }

                val cases = listOf(1 to 1, 1 to 4, 1 to 8, 2 to 8)
                val lines = mutableListOf<String>()
                for ((batch, sequence) in cases) {
                    val ids = LongArray(batch * sequence) { (it % 4 + 4).toLong() }
                    fun infer(): Int {
                        OnnxTensor.createTensor(
                            environment,
                            LongBuffer.wrap(ids),
                            longArrayOf(batch.toLong(), sequence.toLong())
                        ).use { tensor ->
                            probe.run(mapOf("input_ids" to tensor)).use { result ->
                                @Suppress("UNCHECKED_CAST")
                                val logits = result[0].value as Array<Array<FloatArray>>
                                check(logits.size == batch && logits[0].size == sequence && logits[0][0].size == 8) {
                                    "Dimensi output tidak sesuai untuk batch=$batch sequence=$sequence"
                                }
                                check(logits[0][0].all { it.isFinite() }) { "Output mengandung nilai tidak finite" }
                                return logits[0][0].size
                            }
                        }
                    }

                    repeat(3) { infer() } // warm-up, excluded from timing
                    val timings = LongArray(10) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        infer()
                        SystemClock.elapsedRealtimeNanos() - start
                    }.sorted()
                    val medianMs = (timings[4] + timings[5]) / 2.0 / 1_000_000.0
                    lines += "• ${batch}×${sequence} token: median ${"%.3f".format(java.util.Locale.US, medianMs)} ms"
                }

                "Tata ONNX PoC\\n\\nONNX Runtime: BERHASIL\\nVersi: ${environment.version}\\n\\nInferensi Transformer sintetis: BERHASIL\\nModel: ${modelFile.length() / 1024} KB\\nSalin model: ${"%.2f".format(java.util.Locale.US, copyMs)} ms\\nBuka sesi: ${"%.2f".format(java.util.Locale.US, openMs)} ms\\n\\nBenchmark inferensi (3 warm-up, median 10 kali):\\n${lines.joinToString("\\n")}\\n\\nCatatan: model kecil berbobot acak, bukan model bahasa Tata terlatih. Angka ini hanya baseline runtime, bukan performa model produksi."
            }
        } catch (error: Exception) {
            "Pengujian ONNX gagal.\\n\\n${error.javaClass.simpleName}: ${error.message ?: "Tidak ada detail"}"
        } finally {
            modelFile.delete()
        }
    }
}
