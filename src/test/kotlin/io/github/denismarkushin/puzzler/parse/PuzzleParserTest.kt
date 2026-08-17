package io.github.denismarkushin.puzzler.parse

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class PuzzleParserTest {
    @Test
    fun `parser reads bare marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.subject, "bare TODO was not recognized").isEqualTo("extract cache")
    }

    @Test
    fun `parser reads type attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.type, "type attribute was dropped").isEqualTo("debt")
    }

    @Test
    fun `parser reads estimate attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt, 30min): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.estimate, "estimate attribute was dropped").isEqualTo("30min")
    }

    @Test
    fun `parser falls back to marker when type is absent`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "FIXME: fix retries")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.type, "marker did not stand in for the missing type").isEqualTo("FIXME")
    }

    @Test
    fun `parser collects indented body`() {
        val block = CommentBlock(
            "Cache.kt",
            listOf(
                CommentLine(7, 1, "TODO: extract cache"),
                CommentLine(8, 2, "needs TTL"),
                CommentLine(9, 2, "and metrics"),
            ),
        )
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.description, "indented body was not collected").isEqualTo("needs TTL\nand metrics")
    }

    @Test
    fun `parser stops body at flat line`() {
        val block = CommentBlock(
            "Cache.kt",
            listOf(
                CommentLine(7, 1, "TODO: extract cache"),
                CommentLine(8, 2, "needs TTL"),
                CommentLine(9, 1, "ordinary comment"),
                CommentLine(10, 2, "and this indent belongs to someone else"),
            ),
        )
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.description, "body did not stop at the flat line").isEqualTo("needs TTL")
    }

    @Test
    fun `parser ignores block without marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "ordinary comment")))
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
        val block = CommentBlock("Cache.kt", listOf(CommentLine(42, 1, "TODO: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.line, "puzzle lost its position in the file").isEqualTo(42)
    }

    @Test
    fun `parser accepts a custom pattern without optional groups`() {
        val pattern = Regex("^TASK\\s+(?<subject>.+)$")
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TASK extract cache")))
        val puzzle = PuzzleParser(pattern).parse(block)
        assertThat(puzzle?.type, "a pattern without a type group did not yield a null type").isNull()
    }

    @Test
    fun `parser reads labels attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt, 30min) [ perf ,  security ]: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.labels, "labels attribute was dropped").isEqualTo(listOf("perf", "security"))
    }

    @Test
    fun `parser leaves labels empty without a bracket`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzle?.labels, "a puzzle without a bracket invented labels").isEqualTo(emptyList<String>())
    }
}
