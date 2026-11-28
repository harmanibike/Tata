package com.harmanibike.voiceo3

/**
 * Eksperimen tokenizer Character-BPE Tata.
 * Urutan ID harus identik dengan tools.model.training_data.tokenizer_vocabulary.
 * Belum dipakai oleh engine percakapan produksi.
 */
class TataCharacterBpeTokenizer(
    alphabet: List<String>,
    merges: List<Pair<String, String>>
) {
    companion object {
        val SPECIAL_TOKENS = listOf("<pad>", "<unk>", "<bos>", "<eos>")
    }

    val alphabet: List<String> = alphabet.toList()
    val merges: List<Pair<String, String>> = merges.toList()
    val idToToken: List<String>
    private val tokenToId: Map<String, Int>

    init {
        require(this.alphabet.all { it.codePointCount(0, it.length) == 1 }) {
            "Setiap entri alphabet harus satu Unicode code point"
        }
        require(this.alphabet.distinct().size == this.alphabet.size) {
            "Alphabet mengandung token duplikat"
        }
        val known = (SPECIAL_TOKENS + this.alphabet).toMutableSet()
        val ordered = (SPECIAL_TOKENS + this.alphabet).toMutableList()
        for ((left, right) in this.merges) {
            require(left.isNotEmpty() && right.isNotEmpty()) { "Merge kosong" }
            require(left in known && right in known) { "Merge memakai token yang belum tersedia" }
            val merged = left + right
            require(known.add(merged)) { "Merge menghasilkan token duplikat" }
            ordered.add(merged)
        }
        idToToken = ordered
        tokenToId = ordered.withIndex().associate { it.value to it.index }
    }

    fun encode(text: String): List<Int> {
        var tokens = text.codePoints().toArray().map { cp ->
            String(Character.toChars(cp)).let { if (it in alphabet) it else "<unk>" }
        }
        for ((left, right) in merges) {
            val merged = left + right
            val output = ArrayList<String>(tokens.size)
            var index = 0
            while (index < tokens.size) {
                if (index + 1 < tokens.size &&
                    tokens[index] == left && tokens[index + 1] == right
                ) {
                    output.add(merged)
                    index += 2
                } else {
                    output.add(tokens[index])
                    index += 1
                }
            }
            tokens = output
        }
        return tokens.map { tokenToId.getValue(it) }
    }

    fun decode(ids: List<Int>): String = buildString {
        for (id in ids) {
            val token = idToToken.getOrNull(id) ?: error("ID token di luar vocabulary: $id")
            if (token !in SPECIAL_TOKENS) append(token)
        }
    }
}
