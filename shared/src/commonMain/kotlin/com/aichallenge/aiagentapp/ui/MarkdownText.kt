package com.aichallenge.aiagentapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class OrderedList(val items: List<String>) : MdBlock
}

/**
 * Рендеринг markdown: заголовки, списки, **жирный**, *курсив*, `код`.
 * Таблицы (`| ... |`) не парсятся — отображаются как обычный текст.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseMarkdownBlocks(text)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> MarkdownHeading(block, color)
                is MdBlock.Paragraph -> Text(
                    text = parseInlineMarkdown(block.text, style),
                    style = style,
                    color = color
                )
                is MdBlock.BulletList -> MarkdownBulletList(block, style, color)
                is MdBlock.OrderedList -> MarkdownOrderedList(block, style, color)
            }
        }
    }
}

@Composable
private fun MarkdownHeading(block: MdBlock.Heading, color: Color) {
    val headingStyle = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.labelLarge
    }
    Text(
        text = parseInlineMarkdown(block.text, headingStyle),
        style = headingStyle,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp)
    )
}

@Composable
private fun MarkdownBulletList(block: MdBlock.BulletList, baseStyle: TextStyle, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•  ",
                    style = baseStyle,
                    color = color
                )
                Text(
                    text = parseInlineMarkdown(item, baseStyle),
                    style = baseStyle,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarkdownOrderedList(block: MdBlock.OrderedList, baseStyle: TextStyle, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.  ",
                    style = baseStyle,
                    color = color
                )
                Text(
                    text = parseInlineMarkdown(item, baseStyle),
                    style = baseStyle,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun preprocessMarkdown(text: String): String =
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    if (text.isBlank()) return emptyList()
    val lines = preprocessMarkdown(text).split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.isEmpty()) {
            i++
            continue
        }
        val heading = parseHeading(trimmed)
        if (heading != null) {
            blocks.add(heading)
            i++
            continue
        }
        if (BULLET_ITEM.matches(trimmed)) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val match = BULLET_ITEM.find(lines[i].trim()) ?: break
                items.add(match.groupValues[1])
                i++
            }
            blocks.add(MdBlock.BulletList(items))
            continue
        }
        if (ORDERED_ITEM.matches(trimmed)) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val match = ORDERED_ITEM.find(lines[i].trim()) ?: break
                items.add(match.groupValues[2])
                i++
            }
            blocks.add(MdBlock.OrderedList(items))
            continue
        }
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                break
            }
            if (isBlockStart(line)) break
            paragraphLines.add(line)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }
    return blocks.ifEmpty { listOf(MdBlock.Paragraph(text)) }
}

private fun parseHeading(line: String): MdBlock.Heading? {
    val match = HEADING.find(line) ?: return null
    return MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
}

private fun isBlockStart(line: String): Boolean =
    HEADING.matches(line) ||
        BULLET_ITEM.matches(line) ||
        ORDERED_ITEM.matches(line)

fun parseInlineMarkdown(text: String, baseStyle: TextStyle): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }
            text.startsWith("*", index) && !text.startsWith("**", index) -> {
                val end = text.indexOf('*', index + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = baseStyle.fontSize * 0.95f
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}

/** @deprecated Используйте [parseInlineMarkdown] */
fun parseBasicMarkdown(text: String, baseStyle: TextStyle): AnnotatedString =
    parseInlineMarkdown(text, baseStyle)

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val BULLET_ITEM = Regex("^[-*•]\\s+(.+)$")
private val ORDERED_ITEM = Regex("^(\\d+)\\.\\s+(.+)$")
