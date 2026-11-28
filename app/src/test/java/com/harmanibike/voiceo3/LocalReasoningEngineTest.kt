package com.harmanibike.voiceo3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun convertsLengthMassVolumeAndTimeUnits() {
        assertEquals("2 km = 2.000 m.", engine.calculate("berapa 2 km ke meter"))
        assertEquals("500 g = 0,5 kg.", engine.calculate("ubah 500 gram ke kilogram"))
        assertEquals("2 l = 2.000 ml.", engine.calculate("konversi 2 liter ke ml"))
        assertEquals("120 menit = 2 jam.", engine.calculate("hitung 120 menit ke jam"))
        assertEquals("1,5 m = 150 cm.", engine.calculate("ubah 1,5 meter ke sentimeter"))
    }

    @Test
    fun rejectsIncompatibleUnitDimensions() {
        val result = engine.calculate("ubah 2 km ke kilogram")
        assertNotNull(result)
        assertTrue(result!!.contains("tidak sejenis"))
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
            "Perubahan 5% dari Rp100.000 adalah Rp5.000. Harga baru: Rp105.000.",
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
    fun validatesGgufFileSignature() {
        assertTrue(LocalModelStore.isGgufHeader(byteArrayOf(71, 71, 85, 70)))
        assertEquals(false, LocalModelStore.isGgufHeader(byteArrayOf(71, 71, 85, 88)))
        assertEquals(false, LocalModelStore.isGgufHeader(byteArrayOf(71, 71, 85)))
    }

    @Test
    fun parsesSupportedGgufHeaderAndRejectsInvalidStructure() {
        val valid = java.nio.ByteBuffer.allocate(GgufHeaderReader.HEADER_SIZE)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71, 71, 85, 70))
            .putInt(3)
            .putLong(32)
            .putLong(12)
            .array()
        val parsed = GgufHeaderReader.parse(valid)
        assertNotNull(parsed)
        assertEquals(3L, parsed!!.version)
        assertEquals(32L, parsed.tensorCount)
        assertEquals(12L, parsed.metadataCount)

        val invalidVersion = valid.copyOf().also {
            java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4, 9)
        }
        assertEquals(null, GgufHeaderReader.parse(invalidVersion))

        val emptyModel = valid.copyOf().also {
            java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(8, 0L)
        }
        assertEquals(null, GgufHeaderReader.parse(emptyModel))
        assertEquals(null, GgufHeaderReader.parse(byteArrayOf(71, 71, 85, 70)))
    }

    @Test
    fun keepsStorageReserveWhenImportingLocalModels() {
        val reserve = 256L * 1024L * 1024L
        assertEquals(0L, LocalModelStore.safeImportLimit(reserve))
        assertEquals(0L, LocalModelStore.safeImportLimit(reserve - 1L))
        assertEquals(1024L * 1024L, LocalModelStore.safeImportLimit(reserve + 1024L * 1024L))
    }

    @Test
    fun learningTransferDetectsDuplicatesAndConflictsWithoutOverwriting() {
        val existing = listOf(
            LearningRecord(1, "Apa itu fotosintesis?", "Proses tumbuhan membuat makanan.", "Buku", "diterima", 1L)
        )
        val incoming = listOf(
            TransferLearningRecord(" Apa itu FOTOSINTESIS? ", "Proses tumbuhan membuat makanan.", "Sumber lain"),
            TransferLearningRecord("Apa itu fotosintesis?", "Proses tumbuhan membuat makanan dengan bantuan cahaya.", "Referensi baru"),
            TransferLearningRecord("Apa itu gravitasi?", "Gaya tarik antar benda.", "Buku")
        )

        val preview = LearningLabTransfer.preview(incoming, existing)

        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.conflictCount)
        assertEquals(2, preview.importCount)
        assertEquals("diterima", existing.single().status)
        assertEquals("Proses tumbuhan membuat makanan.", existing.single().correction)
    }

    @Test
    fun learningTransferFlagsDifferentAnswersForSameNewQuestion() {
        val incoming = listOf(
            TransferLearningRecord("Apa itu energi?", "Kemampuan melakukan usaha."),
            TransferLearningRecord("Apa itu energi?", "Sesuatu yang dapat menyebabkan perubahan.")
        )
        val preview = LearningLabTransfer.preview(incoming, emptyList())
        assertEquals(1, preview.newRecords.size)
        assertEquals(1, preview.conflictCount)
        assertEquals(2, preview.importCount)
    }

    @Test
    fun learningTransferUsesStableQuestionIdentityAcrossFormatting() {
        val first = LearningLabTransfer.questionIdentity("Apa itu Tata AI?")
        val normalized = LearningLabTransfer.questionIdentity("  APA   ITU tata ai? ")
        val other = LearningLabTransfer.questionIdentity("Apa itu Tata?")
        assertEquals(first, normalized)
        assertTrue(first != other)
    }

    @Test
    fun learningTransferFingerprintIgnoresCaseAndWhitespaceButNotCorrection() {
        val first = LearningLabTransfer.fingerprint("Apa itu AI?", "Kecerdasan buatan.")
        val normalized = LearningLabTransfer.fingerprint("  APA   ITU ai? ", "Kecerdasan   buatan.")
        val changed = LearningLabTransfer.fingerprint("Apa itu AI?", "Program komputer biasa.")

        assertEquals(first, normalized)
        assertTrue(first != changed)
    }

    @Test
    fun identifiesCommonTataIdentityQuestions() {
        assertTrue(TataIdentity.isIdentityQuestion("Siapa Tata AI?"))
        assertTrue(TataIdentity.isIdentityQuestion("siapa kamu"))
        assertTrue(TataIdentity.isIdentityQuestion("Halo, siapa nama kamu?"))
        assertTrue(TataIdentity.isIdentityQuestion("Apa kemampuanmu?"))
        assertEquals(false, TataIdentity.isIdentityQuestion("Siapa presiden Indonesia?"))
    }

    @Test
    fun answersIdentityQuestionsWithOnlyRelevantInformation() {
        assertEquals(
            "Saya Tata AI, asisten digital Android yang sedang dikembangkan untuk membantu Anda.",
            TataIdentity.answer("Siapa Tata AI?")
        )
        assertEquals(
            "AI adalah singkatan dari Artificial Intelligence, yaitu kecerdasan buatan.",
            TataIdentity.answer("Apa arti AI?")
        )
        assertTrue(TataIdentity.answer("Versi berapa?", "2.4.0").contains("2.4.0"))
        assertTrue(TataIdentity.answer("Apa kemampuanmu?").contains("pengetahuan lokal"))
    }

    @Test
    fun localConversationContextUsesHistoryOnlyForFollowUps() {
        val context = LocalConversationContext(maxTurns = 2, promptLimit = 1400)
        context.remember("Apa itu fotosintesis?", "Fotosintesis adalah proses tumbuhan membuat makanan dengan bantuan cahaya.")
        val standalone = context.buildPrompt("Jelaskan sejarah Kerajaan Majapahit.")
        assertTrue(standalone.startsWith("Pertanyaan pengguna:"))
        assertFalse(standalone.contains("fotosintesis"))

        val followUp = context.buildPrompt("Contohnya bagaimana?")
        assertTrue(followUp.contains("fotosintesis"))
        assertTrue(followUp.contains("Contohnya bagaimana?"))
    }

    @Test
    fun localConversationContextKeepsOnlyRecentTurnsAndCanClear() {
        val context = LocalConversationContext(maxTurns = 2)
        context.remember("Pertanyaan satu", "Jawaban satu")
        context.remember("Pertanyaan dua", "Jawaban dua")
        context.remember("Pertanyaan tiga", "Jawaban tiga")
        assertEquals(2, context.size())
        val prompt = context.buildPrompt("Lanjutkan")
        assertFalse(prompt.contains("Pertanyaan satu"))
        assertTrue(prompt.contains("Pertanyaan dua"))
        assertTrue(prompt.contains("Pertanyaan tiga"))
        context.clear()
        assertEquals(0, context.size())
        assertTrue(context.buildPrompt("Lanjutkan").startsWith("Pertanyaan pengguna:"))
    }

    @Test
    fun localConversationContextBoundsPromptAndStoredTurns() {
        val context = LocalConversationContext(maxTurns = 2, promptLimit = 900)
        context.remember("Q".repeat(500), "A".repeat(500))
        val prompt = context.buildPrompt("Jelaskan lagi")
        assertTrue(prompt.length <= 900)
        assertTrue(context.size() <= 2)
    }

    @Test
    fun ignoresNonMathQuestions() {
        assertEquals(null, engine.calculate("siapa nama kamu"))
    }

    @Test
    fun webSearchRequiresAnExplicitRequest() {
        assertTrue(ResearchIntent.isExplicitWebSearch("Cari di internet tentang sejarah Kediri"))
        assertTrue(ResearchIntent.isExplicitWebSearch("Tolong telusuri web harga sepeda"))
        assertFalse(ResearchIntent.isExplicitWebSearch("Jelaskan sejarah Kediri"))
        assertFalse(ResearchIntent.isExplicitWebSearch("Apa itu kecerdasan buatan"))
    }

    @Test
    fun webSearchPrefixIsRemovedFromQuery() {
        assertEquals("sejarah Kediri", ResearchIntent.cleanQuery("Cari di internet tentang sejarah Kediri"))
        assertEquals("harga sepeda", ResearchIntent.cleanQuery("tolong telusuri web harga sepeda"))
    }

    @Test
    fun codeBasedGenerativeEngineCreatesGoalSpecificPlansWithoutInventingFacts() {
        val answer = TataGenerativeEngine().respond("Buatkan rencana untuk belajar Kotlin")
        assertNotNull(answer)
        assertTrue(answer!!.contains("belajar Kotlin"))
        assertTrue(answer.contains("Tentukan hasil"))
        assertTrue(answer.contains("target pertama"))
    }

    @Test
    fun codeBasedGenerativeEngineBuildsComparisonFrameworkAndRequestsMissingOptions() {
        val answer = TataGenerativeEngine().respond("Bandingkan Android dan iPhone")
        assertNotNull(answer)
        assertTrue(answer!!.contains("Android dan iPhone"))
        assertTrue(answer.contains("kelebihan"))
        assertTrue(answer.contains("Sebutkan dua pilihan"))
    }

    @Test
    fun codeBasedGenerativeEngineDoesNotInterceptOrdinaryFactualQuestions() {
        val generator = TataGenerativeEngine()
        assertEquals(null, generator.respond("Siapa presiden Indonesia?"))
        assertEquals(null, generator.respond("Apa itu fotosintesis?"))
    }
}
