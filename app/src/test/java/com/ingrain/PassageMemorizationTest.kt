package com.ingrain

import com.ingrain.ui.screens.ClozePromptConfig
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_1
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_2
import com.ingrain.ui.screens.PASSAGE_HINT_STAGE_3
import com.ingrain.ui.screens.PassageGrade
import com.ingrain.ui.screens.PassagePromptType
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
}
