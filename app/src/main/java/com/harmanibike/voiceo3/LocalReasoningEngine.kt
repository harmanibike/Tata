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
        var expression = rawText.lowercase(Locale("id", "ID")).trim()
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
            val symbols = DecimalFormatSymbols(Locale("id", "ID"))
            val formatter = DecimalFormat("#,##0.########", symbols)
            "Hasil perhitungannya adalah ${formatter.format(result)}."
        } catch (_: ArithmeticException) {
            "Perhitungan tidak dapat dilakukan karena ada pembagian dengan nol."
        } catch (_: IllegalArgumentException) {
            null
        }
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
            var value = parsePower()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * parsePower()
                    consume('/') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw ArithmeticException("Pembagian dengan nol")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipSpaces()
            return if (consume('^')) {
                Math.pow(base, parsePower())
            } else base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                consume('+') -> parseUnary()
                consume('-') -> -parseUnary()
                else -> parsePrimary()
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
