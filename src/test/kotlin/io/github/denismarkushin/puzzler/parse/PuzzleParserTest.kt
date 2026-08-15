package io.github.denismarkushin.puzzler.parse

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class PuzzleParserTest {
    @Test
    fun `parser reads bare marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO: вынести кэш")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.subject, "bare TODO was not recognized").isEqualTo("вынести кэш")
    }

    @Test
    fun `parser reads type attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt): вынести кэш")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.type, "type attribute was dropped").isEqualTo("debt")
    }

    @Test
    fun `parser reads estimate attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt, 30min): вынести кэш")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.estimate, "estimate attribute was dropped").isEqualTo("30min")
    }

    @Test
    fun `parser falls back to marker when type is absent`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "FIXME: чинить ретраи")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.type, "marker did not stand in for the missing type").isEqualTo("FIXME")
    }

    @Test
    fun `parser collects indented body`() {
        val block = CommentBlock(
            "Cache.kt",
            listOf(
                CommentLine(7, 1, "TODO: вынести кэш"),
                CommentLine(8, 2, "нужен TTL"),
                CommentLine(9, 2, "и метрики"),
            ),
        )
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.description, "indented body was not collected").isEqualTo("нужен TTL\nи метрики")
    }

    @Test
    fun `parser stops body at flat line`() {
        val block = CommentBlock(
            "Cache.kt",
            listOf(
                CommentLine(7, 1, "TODO: вынести кэш"),
                CommentLine(8, 2, "нужен TTL"),
                CommentLine(9, 1, "обычный комментарий"),
                CommentLine(10, 2, "и это уже чужой отступ"),
            ),
        )
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.description, "body did not stop at the flat line").isEqualTo("нужен TTL")
    }

    @Test
    fun `parser ignores block without marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "обычный комментарий")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle, "an ordinary comment was mistaken for a puzzle").isNull()
    }

    @Test
    fun `parser ignores marker without subject`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO:")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle, "an empty TODO produced a ticketable puzzle").isNull()
    }

    @Test
    fun `parser records the marker line number`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(42, 1, "TODO: вынести кэш")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.line, "puzzle lost its position in the file").isEqualTo(42)
    }

    @Test
    fun `parser accepts a custom pattern without optional groups`() {
        val pattern = Regex("^ЗАДАЧА\\s+(?<subject>.+)$")
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "ЗАДАЧА вынести кэш")))
        val puzzle = PuzzleParser(pattern).parse(block)
        assertThat(puzzle?.type, "a pattern without a type group did not yield a null type").isNull()
    }
}
