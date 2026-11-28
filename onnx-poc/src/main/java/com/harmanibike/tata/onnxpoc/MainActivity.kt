package com.harmanibike.tata.onnxpoc

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.io.File
import java.nio.LongBuffer

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = try {
            val environment = OrtEnvironment.getEnvironment()
            val modelFile = File(cacheDir, "tata_transformer_synthetic.onnx")
            assets.open("tata_transformer_synthetic.onnx").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }

            TataOnnxRuntimeProbe.open(modelFile).use { probe ->
                check(probe.inputNames == setOf("input_ids")) { "Nama input model tidak sesuai" }
                check(probe.outputNames == setOf("logits")) { "Nama output model tidak sesuai" }

                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(longArrayOf(4L, 5L, 6L, 7L)),
                    longArrayOf(1L, 4L)
                ).use { inputTensor ->
                    probe.run(mapOf("input_ids" to inputTensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = result[0].value as Array<Array<FloatArray>>
                        check(logits.size == 1 && logits[0].size == 4 && logits[0][0].size == 8) {
                            "Dimensi output model tidak sesuai"
                        }
                        val firstLogit = logits[0][0][0]
                        check(firstLogit.isFinite()) { "Output model bukan angka finite" }
                        "ONNX Runtime berhasil dimuat.\n\nVersi runtime: ${environment.version}\n\nInferensi Transformer sintetis: BERHASIL\nInput: 1 batch × 4 token\nOutput logits: 1 × 4 × 8\nContoh nilai logits: $firstLogit\n\nCatatan: ini model acak kecil untuk pengujian mesin, bukan model bahasa Tata terlatih."
                    }
                }
            }.also { modelFile.delete() }
        } catch (error: Exception) {
            "Pengujian ONNX gagal.\n\n${error.javaClass.simpleName}: ${error.message ?: "Tidak ada detail"}\n\nPastikan APK dibuat melalui GitHub Actions agar fixture pengujian tersedia."
        }

        val message = TextView(this).apply {
            text = "Tata ONNX PoC\n\n$status"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        setContentView(message)
    }
}
