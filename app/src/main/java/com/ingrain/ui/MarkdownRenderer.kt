package com.ingrain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private enum class StyleToken {
    Heading3,
    Paragraph,
    ListItem,
    Strong,
    Emphasis,
}

@Composable
fun MarkdownTokenText(
    markdown: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            val (token, text) = when {
                line.startsWith("### ") -> StyleToken.Heading3 to line.removePrefix("### ")
                line.startsWith("- ") -> StyleToken.ListItem to "• ${line.removePrefix("- ")}"
                else -> StyleToken.Paragraph to line
            }

            Text(
                text = buildInlineMarkdown(text),
                style = tokenStyle(token),
                textAlign = textAlign,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun tokenStyle(token: StyleToken) = when (token) {
    StyleToken.Heading3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    StyleToken.Paragraph -> MaterialTheme.typography.bodyLarge
    StyleToken.ListItem -> MaterialTheme.typography.bodyLarge
    StyleToken.Strong -> MaterialTheme.typography.bodyLarge
    StyleToken.Emphasis -> MaterialTheme.typography.bodyLarge
}

private fun buildInlineMarkdown(text: String): AnnotatedString {
    val strong = Regex("\\*\\*(.+?)\\*\\*")
    val em = Regex("\\*(.+?)\\*")

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val strongMatch = strong.find(text, i)
            val emMatch = em.find(text, i)
            val next = listOfNotNull(strongMatch, emMatch).minByOrNull { it.range.first }

            if (next == null) {
                append(text.substring(i))
                break
            }

            if (next.range.first > i) append(text.substring(i, next.range.first))

            val token = if (next.value.startsWith("**")) StyleToken.Strong else StyleToken.Emphasis
            val content = next.groupValues[1]
            pushStyle(
                when (token) {
                    StyleToken.Strong -> SpanStyle(fontWeight = FontWeight.Bold)
                    StyleToken.Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> SpanStyle()
                },
            )
            append(content)
            pop()

            i = next.range.last + 1
        }
    }
}
