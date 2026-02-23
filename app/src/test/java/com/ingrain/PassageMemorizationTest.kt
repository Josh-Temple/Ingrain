package com.ingrain

import com.ingrain.ui.screens.ClozePromptConfig
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_1
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_2
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_3
import com.ingrain.ui.screens.PassageGrade
import com.ingrain.ui.screens.PassagePromptType
import com.ingrain.ui.screens.PassageClozeVariants
import com.ingrain.ui.screens.defaultClozeVariantIndex
import com.ingrain.ui.screens.nextClozeVariantIndex
import com.ingrain.ui.screens.resolveClozePrompt
import com.ingrain.ui.screens.allowedPassageGrades
import com.ingrain.ui.screens.buildClozePrompt
import com.ingrain.ui.screens.buildFirstWordCue
import com.ingrain.ui.screens.buildPassageHint
import com.ingrain.ui.screens.buildPassagePrompt
import com.ingrain.ui.screens.buildKeywordCue
import com.ingrain.ui.screens.splitIntoSentencesOrLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageMemorizationTest {
    private val sample = """
        In the beginning was the Word.
        And the Word was with God.
        And the Word was God.
    """.trimIndent()

    @Test
    fun splitIntoSentencesOrLines_prefersNonEmptyLines() {
        val lines = splitIntoSentencesOrLines(sample)
        assertEquals(3, lines.size)
        assertEquals("In the beginning was the Word.", lines.first())
    }

    @Test
    fun promptBuilders_areDeterministicAndNonEmpty() {
        val firstWordOnce = buildFirstWordCue(sample)
        val firstWordTwice = buildFirstWordCue(sample)
        assertEquals(firstWordOnce, firstWordTwice)
        assertTrue(firstWordOnce.isNotBlank())

        val keyword = buildKeywordCue(sample)
        assertTrue(keyword.isNotBlank())

        val cloze = buildClozePrompt(sample, ClozePromptConfig(revealPrefixChars = 1))
        assertTrue(cloze.isNotBlank())
        assertEquals(cloze, buildClozePrompt(sample, ClozePromptConfig(revealPrefixChars = 1)))
    }

    @Test
    fun hints_areProgressiveAndDistinct() {
        val h1 = buildPassageHint(sample, PASSAGE_HINT_STAGE_1)
        val h2 = buildPassageHint(sample, PASSAGE_HINT_STAGE_2)
        val h3 = buildPassageHint(sample, PASSAGE_HINT_STAGE_3)

        assertTrue(h1.isNotBlank())
        assertTrue(h2.isNotBlank())
        assertTrue(h3.isNotBlank())
        assertNotEquals(h1, h2)
        assertNotEquals(h2, h3)
    }

    @Test
    fun promptTypeChangesPromptOutput() {
        val free = buildPassagePrompt(sample, PassagePromptType.FreeRecall)
        val first = buildPassagePrompt(sample, PassagePromptType.FirstWordCue)
        val cloze = buildPassagePrompt(sample, PassagePromptType.ClozeRecall)

        assertTrue(free.contains("Recall"))
        assertNotEquals(free, first)
        assertNotEquals(first, cloze)
    }

    @Test
    fun gradeAvailability_respectsHintUse() {
        assertEquals(
            listOf(PassageGrade.Again, PassageGrade.MinorErrors, PassageGrade.Exact),
            allowedPassageGrades(hintUsed = false),
        )
        assertEquals(
            listOf(PassageGrade.Again, PassageGrade.Hinted),
            allowedPassageGrades(hintUsed = true),
        )
    }


    @Test
    fun clozeRecall_usesPregeneratedVariantWhenAvailable() {
        val variants = PassageClozeVariants(
            cloze1 = "Stored cloze one",
            cloze2 = "Stored cloze two",
        )

        val prompt = buildPassagePrompt(
            back = sample,
            promptType = PassagePromptType.ClozeRecall,
            clozeVariants = variants,
            selectedClozeVariantIndex = 2,
        )

        assertEquals("Stored cloze two", prompt)
    }

    @Test
    fun clozeVariantSelection_skipsMissingVariantsAndCycles() {
        val variants = PassageClozeVariants(
            cloze1 = "C1",
            cloze3 = "C3",
        )

        assertEquals(1, defaultClozeVariantIndex(variants))
        assertEquals(3, nextClozeVariantIndex(1, variants))
        assertEquals(1, nextClozeVariantIndex(3, variants))
    }

    @Test
    fun clozeRecall_missingSelectedVariant_fallsBackSafely() {
        val variants = PassageClozeVariants(
            cloze1 = "C1",
            cloze3 = "C3",
        )

        val (prompt, resolved) = resolveClozePrompt(sample, variants, selectedIndex = 2)
        assertEquals("C1", prompt)
        assertEquals(1, resolved)
    }

    @Test
    fun clozeRecall_withoutPregeneratedVariants_usesGeneratedCloze() {
        val variants = PassageClozeVariants()

        val prompt = buildPassagePrompt(
            back = sample,
            promptType = PassagePromptType.ClozeRecall,
            clozeVariants = variants,
            selectedClozeVariantIndex = 1,
        )

        assertEquals(buildClozePrompt(sample), prompt)
    }

}
