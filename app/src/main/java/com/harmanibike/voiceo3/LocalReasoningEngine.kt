package com.harmanibike.voiceo3

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Kemampuan hitung lokal yang menjadi bagian dari script APK.
 * Tidak memakai eval, API, database, atau jaringan.
 * Hanya menerima ekspresi aritmetika numerik yang dapat dipastikan.
 */
class LocalReasoningEngine {
    fun calculate(rawText: String): String? {
        val normalizedText = rawText.lowercase(Locale("id", "ID")).trim()
        convertUnit(normalizedText)?.let { return it }
        calculateBusiness(normalizedText)?.let { return it }

        val percentMatch = Regex(
            "^(?:tolong\\s+)?(?:hitung|berapa|coba hitung)?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(?:%|persen)\\s+dari\\s+([0-9]+(?:[.,][0-9]+)?)$"
        ).matchEntire(normalizedText)
        if (percentMatch != null) {
            val percentage = percentMatch.groupValues[1].replace(',', '.').toDouble()
            val base = percentMatch.groupValues[2].replace(',', '.').toDouble()
            return formatResult(percentage / 100.0 * base)
        }

        var expression = normalizedText
        expression = expression
            .replace(Regex("^(tolong\\s+)?(hitung|berapa|hasil dari|coba hitung)\\s*"), "")
            .replace(Regex("\\bditambah\\b|\\btambah\\b|\\bplus\\b"), "+")
            .replace(Regex("\\bdikurangi\\b|\\bkurangi\\b|\\bkurang\\b|\\bminus\\b"), "-")
            .replace(Regex("\\bdikalikan\\b|\\bdikali\\b|\\bkali\\b|\\btimes\\b"), "*")
            .replace(Regex("\\bdibagi\\b|\\bbagi\\b|\\bper\\b"), "/")
            .replace(Regex("\\bpangkat\\b"), "^")
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')
            .trim()

        if (expression.length !in 3..120) return null
        if (!expression.any { it in "+-*/^" }) return null
        if (!expression.matches(Regex("[0-9+*/^().\\s-]+"))) return null
        if (Regex("\\d").findAll(expression).count() !in 2..30) return null

        return try {
            val parser = ExpressionParser(expression)
            val result = parser.parse()
            if (!result.isFinite()) return "Hasil perhitungan terlalu besar atau tidak dapat ditentukan."
            formatResult(result)
        } catch (_: ArithmeticException) {
            "Perhitungan tidak dapat dilakukan karena ada pembagian dengan nol."
        } catch (_: IllegalArgumentException) {
            null
        }
    }


    private data class UnitDefinition(val symbol: String, val dimension: String, val factor: Double)
    private val units = mapOf(
        "milimeter" to UnitDefinition("mm", "panjang", 0.001), "mm" to UnitDefinition("mm", "panjang", 0.001),
        "sentimeter" to UnitDefinition("cm", "panjang", 0.01), "cm" to UnitDefinition("cm", "panjang", 0.01),
        "meter" to UnitDefinition("m", "panjang", 1.0), "m" to UnitDefinition("m", "panjang", 1.0),
        "kilometer" to UnitDefinition("km", "panjang", 1000.0), "km" to UnitDefinition("km", "panjang", 1000.0),
        "miligram" to UnitDefinition("mg", "massa", 0.001), "mg" to UnitDefinition("mg", "massa", 0.001),
        "gram" to UnitDefinition("g", "massa", 1.0), "g" to UnitDefinition("g", "massa", 1.0),
        "kilogram" to UnitDefinition("kg", "massa", 1000.0), "kg" to UnitDefinition("kg", "massa", 1000.0),
        "mililiter" to UnitDefinition("ml", "volume", 0.001), "ml" to UnitDefinition("ml", "volume", 0.001),
        "liter" to UnitDefinition("l", "volume", 1.0), "l" to UnitDefinition("l", "volume", 1.0),
        "detik" to UnitDefinition("detik", "waktu", 1.0), "sekon" to UnitDefinition("detik", "waktu", 1.0),
        "menit" to UnitDefinition("menit", "waktu", 60.0), "jam" to UnitDefinition("jam", "waktu", 3600.0),
        "hari" to UnitDefinition("hari", "waktu", 86400.0)
    )

    private fun convertUnit(text: String): String? {
        val unitPattern = units.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val match = Regex("^(?:tolong\\s+)?(?:ubah|konversi|berapa|hitung)?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*($unitPattern)\\s+(?:ke|menjadi|dalam)\\s+($unitPattern)$").matchEntire(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = units[match.groupValues[2]] ?: return null
        val to = units[match.groupValues[3]] ?: return null
        if (from.dimension != to.dimension) return "Satuan ${from.symbol} dan ${to.symbol} tidak sejenis, jadi tidak bisa dikonversi langsung."
        val result = amount * from.factor / to.factor
        if (!result.isFinite()) return "Hasil konversi terlalu besar atau tidak dapat ditentukan."
        return "${formatNumber(amount)} ${from.symbol} = ${formatNumber(result)} ${to.symbol}."
    }

    private fun calculateBusiness(text: String): String? {
        val n = "([0-9]+(?:[.,][0-9]+)?)"
        val discount = Regex("^(?:tolong\\s+)?(?:hitung\\s+)?(?:diskon|potongan)\\s+$n\\s*(?:%|persen)\\s+(?:dari|untuk)\\s+$n$").matchEntire(text)
        if (discount != null) {
            val percent = discount.groupValues[1].toDoubleId()
            val price = discount.groupValues[2].toDoubleId()
            if (percent !in 0.0..100.0 || price < 0) return "Persentase diskon harus 0–100% dan harga tidak boleh negatif."
            val saving = price * percent / 100.0
            return "Diskon ${formatNumber(percent)}% dari Rp${formatMoney(price)} = Rp${formatMoney(saving)}. Harga setelah diskon: Rp${formatMoney(price - saving)}."
        }

        val change = Regex("^(?:tolong\\s+)?(?:hitung\\s+)?(?:harga\\s+)?$n\\s+(naik|turun|meningkat|menurun)\\s+$n\\s*(?:%|persen)$").matchEntire(text)
            ?: Regex("^(?:tolong\\s+)?(?:hitung\\s+)?(naik|turun)\\s+$n\\s*(?:%|persen)\\s+dari\\s+$n$").matchEntire(text)
        if (change != null) {
            val reverse = change.groupValues[1] == "naik" || change.groupValues[1] == "turun"
            val direction = if (reverse) change.groupValues[1] else change.groupValues[2]
            val price = (if (reverse) change.groupValues[3] else change.groupValues[1]).toDoubleId()
            val percent = (if (reverse) change.groupValues[2] else change.groupValues[3]).toDoubleId()
            if (price < 0 || percent < 0) return "Harga dan persentase tidak boleh negatif."
            val difference = price * percent / 100.0
            val up = direction == "naik" || direction == "meningkat"
            val result = if (up) price + difference else price - difference
            return "Perubahan ${formatNumber(percent)}% dari Rp${formatMoney(price)} adalah Rp${formatMoney(difference)}. Harga baru: Rp${formatMoney(result)}."
        }

        val profit = Regex("^(?:tolong\\s+)?(?:hitung\\s+)?(?:keuntungan|kerugian|laba|rugi)\\s+(?:dari\\s+)?(?:modal|beli)\\s+$n\\s+(?:jual|penjualan)\\s+$n$").matchEntire(text)
            ?: Regex("^(?:tolong\\s+)?(?:hitung\\s+)?modal\\s+$n\\s+(?:jual|penjualan)\\s+$n$").matchEntire(text)
            ?: return null
        val cost = profit.groupValues[1].toDoubleId()
        val selling = profit.groupValues[2].toDoubleId()
        if (cost < 0 || selling < 0) return "Modal dan harga jual tidak boleh negatif."
        val difference = selling - cost
        if (cost == 0.0) return "Persentase keuntungan tidak dapat dihitung karena modal nol. Selisih nominal: Rp${formatMoney(difference)}."
        val percent = kotlin.math.abs(difference) / cost * 100.0
        return when {
            difference > 0 -> "Keuntungan Rp${formatMoney(difference)} (${formatNumber(percent)}% dari modal)."
            difference < 0 -> "Kerugian Rp${formatMoney(-difference)} (${formatNumber(percent)}% dari modal)."
            else -> "Tidak untung dan tidak rugi. Harga jual sama dengan modal."
        }
    }

    private fun String.toDoubleId(): Double = replace(',', '.').toDouble()

    private fun formatNumber(value: Double): String {
        val symbols = DecimalFormatSymbols(Locale("id", "ID"))
        return DecimalFormat("#,##0.########", symbols).format(value)
    }

    private fun formatMoney(value: Double): String {
        val symbols = DecimalFormatSymbols(Locale("id", "ID"))
        return DecimalFormat("#,##0.##", symbols).format(value)
    }

    private fun formatResult(value: Double): String {
        if (!value.isFinite()) return "Hasil perhitungan terlalu besar atau tidak dapat ditentukan."
        val symbols = DecimalFormatSymbols(Locale("id", "ID"))
        val formatter = DecimalFormat("#,##0.########", symbols)
        return "Hasil perhitungannya adalah ${formatter.format(value)}."
    }

    private class ExpressionParser(private val input: String) {
        private var position = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            require(position == input.length) { "Sisa karakter tidak dikenali" }
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + parseTerm()
                    consume('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * parseUnary()
                    consume('/') -> {
                        val divisor = parseUnary()
                        if (divisor == 0.0) throw ArithmeticException("Pembagian dengan nol")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            skipSpaces()
            return if (consume('^')) {
                Math.pow(base, parseUnary())
            } else base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                consume('+') -> parseUnary()
                consume('-') -> -parseUnary()
                else -> parsePower()
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = parseExpression()
                skipSpaces()
                require(consume(')')) { "Kurung tidak lengkap" }
                return value
            }
            val start = position
            while (position < input.length &&
                (input[position].isDigit() || input[position] == '.')) {
                position++
            }
            require(position > start) { "Angka tidak ditemukan" }
            return input.substring(start, position).toDouble()
        }

        private fun consume(char: Char): Boolean {
            if (position < input.length && input[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) position++
        }
    }
}
