package io.github.denismarkushin.puzzler.parse

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class PuzzleParserTest {
    @Test
    fun `parser reads bare marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.subject, "bare TODO was not recognized").isEqualTo("extract cache")
    }

    @Test
    fun `parser reads type attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.type, "type attribute was dropped").isEqualTo("debt")
    }

    @Test
    fun `parser reads a cyrillic type`() {
        val block = CommentBlock("Кэш.kt", listOf(CommentLine(7, 1, "TODO(баг): почистить кэш")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.type, "a cyrillic type was not recognized").isEqualTo("баг")
    }

    @Test
    fun `parser reads estimate attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt, 30min): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.estimate, "estimate attribute was dropped").isEqualTo("30min")
    }

    @Test
    fun `parser falls back to marker when type is absent`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "FIXME: fix retries")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
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
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
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
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.description, "body did not stop at the flat line").isEqualTo("needs TTL")
    }

    @Test
    fun `parser ignores block without marker`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "ordinary comment")))
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles, "an ordinary comment was mistaken for a puzzle").isEmpty()
    }

    @Test
    fun `parser ignores marker without subject`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO:")))
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles, "an empty TODO produced a ticketable puzzle").isEmpty()
    }

    @Test
    fun `parser records the marker line number`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(42, 1, "TODO: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.line, "puzzle lost its position in the file").isEqualTo(42)
    }

    @Test
    fun `parser accepts a custom pattern without optional groups`() {
        val pattern = Regex("^TASK\\s+(?<subject>.+)$")
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TASK extract cache")))
        val puzzle = PuzzleParser(pattern).parse(block).singleOrNull()
        assertThat(puzzle?.type, "a pattern without a type group did not yield a null type").isNull()
    }

    @Test
    fun `parser reads labels attribute`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt, 30min) [ perf ,  security ]: extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.labels, "labels attribute was dropped").isEqualTo(listOf("perf", "security"))
    }

    @Test
    fun `parser leaves labels empty without a bracket`() {
        val block = CommentBlock("Cache.kt", listOf(CommentLine(7, 1, "TODO(debt): extract cache")))
        val puzzle = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block).singleOrNull()
        assertThat(puzzle?.labels, "a puzzle without a bracket invented labels").isEqualTo(emptyList<String>())
    }

    @Test
    fun `parser keeps the hash of a single puzzle block`() {
        val block = CommentBlock(
            "Sessions.kt",
            listOf(
                CommentLine(12, 1, "TODO(debt, 30min) [perf]: evict stale sessions"),
                CommentLine(13, 3, "sweep every 90 seconds"),
                CommentLine(14, 3, "keep the ones touched in the last hour"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.map { puzzle -> puzzle.hash }, "a single puzzle block changed its hash and would be filed again").isEqualTo(listOf("ff1c204b56dc"))
    }

    @Test
    fun `parser keeps the hash of the first puzzle when a second one follows`() {
        val block = CommentBlock(
            "export.js",
            listOf(
                CommentLine(3, 1, "TODO(debt, 30min) [stories]: drop the legacy exporter"),
                CommentLine(4, 3, "still read by the nightly report"),
                CommentLine(5, 1, "TODO(debt, 30min) [stories]: rename the export flag"),
                CommentLine(6, 3, "keep the old name as an alias"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.first().hash, "the first puzzle changed its hash once a second one was added below").isEqualTo("48560032359b")
    }

    @Test
    fun `parser reads both puzzles of one comment block`() {
        val block = CommentBlock(
            "export.js",
            listOf(
                CommentLine(3, 1, "TODO(debt, 30min) [stories]: drop the legacy exporter"),
                CommentLine(4, 3, "still read by the nightly report"),
                CommentLine(5, 1, "TODO(debt, 30min) [stories]: rename the export flag"),
                CommentLine(6, 3, "keep the old name as an alias"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.map { puzzle -> puzzle.subject }, "the second puzzle of the block was swallowed").isEqualTo(listOf("drop the legacy exporter", "rename the export flag"))
    }

    @Test
    fun `parser gives each puzzle of a block its own body`() {
        val block = CommentBlock(
            "Billing.kt",
            listOf(
                CommentLine(21, 1, "FIXME: invoices round half down"),
                CommentLine(22, 2, "use HALF_EVEN like the ledger"),
                CommentLine(23, 1, "HACK(debt): tax table is hardcoded"),
                CommentLine(24, 2, "load it from the rates service"),
                CommentLine(25, 2, "once it ships"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.map { puzzle -> puzzle.description }, "the bodies of neighbouring puzzles got mixed up").isEqualTo(listOf("use HALF_EVEN like the ledger", "load it from the rates service\nonce it ships"))
    }

    @Test
    fun `parser reads a headline below an ordinary comment`() {
        val block = CommentBlock(
            "session.py",
            listOf(
                CommentLine(40, 1, "session cache, shared by workers"),
                CommentLine(41, 1, "FIXME: sessions leak on logout"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.map { puzzle -> puzzle.subject }, "a headline under an ordinary comment was ignored").isEqualTo(listOf("sessions leak on logout"))
    }

    @Test
    fun `parser keeps a deeper headline inside the body`() {
        val block = CommentBlock(
            "import.sql",
            listOf(
                CommentLine(8, 1, "TODO: split the importer"),
                CommentLine(9, 3, "HACK: csv branch copies the json one"),
            ),
        )
        val puzzles = PuzzleParser(PuzzleParser.DEFAULT_PATTERN).parse(block)
        assertThat(puzzles.map { puzzle -> puzzle.description }, "a headline nested in a body was torn out of it").isEqualTo(listOf("HACK: csv branch copies the json one"))
    }
}
