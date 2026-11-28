package com.harmanibike.tata.onnxpoc

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import ai.onnxruntime.OrtEnvironment

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = try {
            val environment = OrtEnvironment.getEnvironment()
            "ONNX Runtime berhasil dimuat.\n\nVersi runtime: \${environment.version}\n\nBelum ada model bahasa Tata yang dimuat. Aplikasi ini hanya untuk pengujian runtime ONNX."
        } catch (error: Exception) {
            "ONNX Runtime gagal dimuat.\n\n\${error.javaClass.simpleName}: \${error.message ?: "Tidak ada detail"}"
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
