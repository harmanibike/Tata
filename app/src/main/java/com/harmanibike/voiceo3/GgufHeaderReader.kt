package com.harmanibike.voiceo3

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufHeader(
    val version: Long,
    val tensorCount: Long,
    val metadataCount: Long
)

object GgufHeaderReader {
    const val HEADER_SIZE = 24

    fun parse(bytes: ByteArray): GgufHeader? {
        if (bytes.size < HEADER_SIZE) return null
        if (bytes[0] != 'G'.code.toByte() ||
            bytes[1] != 'G'.code.toByte() ||
            bytes[2] != 'U'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()
        ) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        val version = buffer.int.toLong() and 0xffffffffL
        val tensorCount = buffer.long
        val metadataCount = buffer.long

        if (version !in 2L..3L) return null
        if (tensorCount !in 1L..1_000_000L) return null
        if (metadataCount !in 1L..100_000L) return null

        return GgufHeader(version, tensorCount, metadataCount)
    }
}
