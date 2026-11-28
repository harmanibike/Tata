package com.harmanibike.tata.onnxpoc

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.FloatBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TataOnnxRuntimeDeviceTest {

    @Test
    fun opensLocalSyntheticModelAndRunsIdentityInferenceOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.cacheDir, "tiny_identity.onnx")
        context.assets.open("tiny_identity.onnx").use { input ->
            modelFile.outputStream().use { output -> input.copyTo(output) }
        }

        val environment = OrtEnvironment.getEnvironment()
        TataOnnxRuntimeProbe.open(modelFile).use { probe ->
            assertEquals(setOf("input"), probe.inputNames)
            assertEquals(setOf("output"), probe.outputNames)

            val tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(floatArrayOf(1.25f, -3.5f)),
                longArrayOf(1, 2)
            )
            tensor.use {
                environment.createSession(modelFile.absolutePath).use { session ->
                    session.run(mapOf("input" to tensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val output = result[0].value as Array<FloatArray>
                        assertEquals(1, output.size)
                        assertArrayEquals(floatArrayOf(1.25f, -3.5f), output[0], 0.00001f)
                    }
                }
            }
        }
        modelFile.delete()
    }
}
