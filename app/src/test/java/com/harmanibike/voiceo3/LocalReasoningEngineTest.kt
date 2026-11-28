package com.harmanibike.voiceo3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReasoningEngineTest {
    private val engine = LocalReasoningEngine()

    @Test
    fun calculatesIndonesianAdditionAndMultiplicationPrecedence() {
        assertEquals("Hasil perhitungannya adalah 200.", engine.calculate("berapa 125 tambah 75"))
        assertEquals("Hasil perhitungannya adalah 40.", engine.calculate("hitung 20 tambah 5 kali 4"))
    }

    @Test
    fun calculatesParenthesesAndDivision() {
        assertEquals("Hasil perhitungannya adalah 50.", engine.calculate("hitung (20 dikali 5) dibagi 2"))
    }

    @Test
    fun calculatesPowerWithMathematicalUnaryPrecedence() {
        assertEquals("Hasil perhitungannya adalah 8.", engine.calculate("berapa 2 pangkat 3"))
        assertEquals("Hasil perhitungannya adalah -4.", engine.calculate("hitung -2 pangkat 2"))
        assertEquals("Hasil perhitungannya adalah 0,25.", engine.calculate("hitung 2 pangkat -2"))
    }

    @Test
    fun calculatesPercentageOfValue() {
        assertEquals("Hasil perhitungannya adalah 20.", engine.calculate("berapa 10 persen dari 200"))
        assertEquals("Hasil perhitungannya adalah 12,5.", engine.calculate("hitung 25% dari 50"))
    }

    @Test
    fun reportsDivisionByZero() {
        val result = engine.calculate("hitung 10 dibagi 0")
        assertNotNull(result)
        assertTrue(result!!.contains("pembagian dengan nol"))
    }

    @Test
    fun ignoresNonMathQuestions() {
        assertEquals(null, engine.calculate("siapa nama kamu"))
    }
}
