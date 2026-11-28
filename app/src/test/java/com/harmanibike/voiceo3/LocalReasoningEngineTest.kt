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
    fun calculatesDiscountAndFinalPrice() {
        assertEquals(
            "Diskon 10% dari Rp150.000 = Rp15.000. Harga setelah diskon: Rp135.000.",
            engine.calculate("hitung diskon 10 persen dari 150000")
        )
        assertEquals(
            "Persentase diskon harus 0–100% dan harga tidak boleh negatif.",
            engine.calculate("diskon 120 persen dari 100000")
        )
    }

    @Test
    fun calculatesPriceIncreaseAndDecrease() {
        assertEquals(
            "Perubahan 10% dari Rp100.000 adalah Rp10.000. Harga baru: Rp110.000.",
            engine.calculate("100000 naik 10 persen")
        )
        assertEquals(
            "Perubahan 20% dari Rp100.000 adalah Rp20.000. Harga baru: Rp80.000.",
            engine.calculate("harga 100000 turun 20 persen")
        )
        assertEquals(
            "Harga Rp100.000 naik 5%: perubahan Rp5.000. Harga baru: Rp105.000.",
            engine.calculate("naik 5 persen dari 100000")
        )
    }

    @Test
    fun calculatesProfitLossAndBreakEven() {
        assertEquals(
            "Keuntungan Rp25.000 (25% dari modal).",
            engine.calculate("hitung keuntungan modal 100000 jual 125000")
        )
        assertEquals(
            "Kerugian Rp10.000 (10% dari modal).",
            engine.calculate("modal 100000 jual 90000")
        )
        assertEquals(
            "Tidak untung dan tidak rugi. Harga jual sama dengan modal.",
            engine.calculate("modal 100000 jual 100000")
        )
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
