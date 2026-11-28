package com.harmanibike.voiceo3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TataCharacterBpeTokenizerTest {
    @Test
    fun matchesPythonVocabularyOrderAndMergeEncoding() {
        val tokenizer = TataCharacterBpeTokenizer(
            alphabet = listOf(" ", "a", "b", "c", "i"),
            merges = listOf("a" to "b", "ab" to "c")
        )
        assertEquals(
            listOf("<pad>", "<unk>", "<bos>", "<eos>", " ", "a", "b", "c", "i", "ab", "abc"),
            tokenizer.idToToken
        )
        assertEquals(listOf(10, 4, 8), tokenizer.encode("abc i"))
        assertEquals("abc i", tokenizer.decode(tokenizer.encode("abc i")))
    }

    @Test
    fun handlesSupplementaryUnicodeCodePointsLikePython() {
        val tokenizer = TataCharacterBpeTokenizer(
            alphabet = listOf("a", "🧩"),
            merges = emptyList()
        )
        assertEquals(listOf(4, 5), tokenizer.encode("a🧩"))
        assertEquals("a🧩", tokenizer.decode(listOf(4, 5)))
    }

    @Test
    fun unknownCharactersUseUnknownToken() {
        val tokenizer = TataCharacterBpeTokenizer(listOf("a"), emptyList())
        assertEquals(listOf(4, 1), tokenizer.encode("a🧩"))
    }

    @Test
    fun rejectsInvalidMergeOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            TataCharacterBpeTokenizer(listOf("a", "b"), listOf("ab" to "c"))
        }
    }
}
