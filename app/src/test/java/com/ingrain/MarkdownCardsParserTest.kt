package com.ingrain

import com.ingrain.importing.BulkImportParser
import com.ingrain.importing.MarkdownCardsParser
import com.ingrain.importing.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCardsParserTest {
    @Test
    fun parse_markdownCards_success() {
        val input = """
            ---
            deck: Biology
            tags:
              - cell
              - basics
            ---

            ### Front
            What is **mitosis**?

            ### Back
            Cell division for growth.
            ===
            ---
            deck: Biology
            tags: [exam]
            ---

            ### Front
            *Meiosis* role?

            ### Back
            Produces gametes.
        """.trimIndent()

        val results = MarkdownCardsParser.parse(input)
        assertEquals(2, results.size)
        val first = results[0] as ParseResult.Success
        assertEquals("Biology", first.card.deck)
        assertEquals(listOf("cell", "basics"), first.card.tags)
        assertTrue(first.card.front.contains("mitosis"))
    }

    @Test
    fun parse_markdownCards_missingBack_error() {
        val input = """
            ---
            deck: Biology
            tags: []
            ---

            ### Front
            Q only
        """.trimIndent()

        val result = MarkdownCardsParser.parse(input).single()
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("Back"))
    }


    @Test
    fun parse_markdownCards_accepts_h2_sections() {
        val input = """
            ---
            deck: Biology
            tags: [exam]
            ---

            ## Front
            What is ATP?

            ## Back
            Cellular energy currency.
        """.trimIndent()

        val result = MarkdownCardsParser.parse(input).single()
        assertTrue(result is ParseResult.Success)
    }

    @Test
    fun parse_markdownCards_missingTags_error() {
        val input = """
            ---
            deck: Biology
            ---

            ### Front
            Q

            ### Back
            A
        """.trimIndent()

        val result = MarkdownCardsParser.parse(input).single()
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("front matter"))
    }

    @Test
    fun bulkParser_fallback_jsonLines() {
        val input = """{"deck":"Test","front":"F","back":"B"}"""
        val result = BulkImportParser.parse(input).single()
        assertTrue(result is ParseResult.Success)
    }

    @Test
    fun parse_markdownCards_supports_passageMetadata() {
        val input = """
            ---
            deck: Speech
            tags: [memorization]
            study_mode: passage_memorization
            strictness: near_exact
            hint_policy: disabled
            ---

            ## Front
            Recite.

            ## Back
            Four score and seven years ago...
        """.trimIndent()

        val result = MarkdownCardsParser.parse(input).single() as ParseResult.Success
        assertEquals("passage_memorization", result.card.study_mode)
        assertEquals("near_exact", result.card.strictness)
        assertEquals("disabled", result.card.hint_policy)
    }
}
