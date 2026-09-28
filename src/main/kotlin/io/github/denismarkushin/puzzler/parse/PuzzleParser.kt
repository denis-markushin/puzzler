package io.github.denismarkushin.puzzler.parse

import io.github.denismarkushin.puzzler.hash.PuzzleHash

/**
 * Turning a comment block into puzzles.
 * Every line matching the configured regexp starts a puzzle, its body is the following lines with a deeper indent.
 * A matching line inside such a body stays in it, so a puzzle added below never changes the hash of the one above.
 */
class PuzzleParser(
    private val pattern: Regex,
) {
    private val declared = groupName.findAll(pattern.pattern).map { match -> match.groupValues[1] }.toSet()

    fun parse(block: CommentBlock): List<Puzzle> = buildList {
        var index = 0
        while (index < block.lines.size) {
            val head = block.lines[index]
            val body = block.lines.subList(index + 1, block.lines.size)
                .takeWhile { line -> line.indent > head.indent }
            val puzzle = puzzle(block.path, head, body)
            puzzle?.let { found -> add(found) }
            index += if (puzzle == null) 1 else 1 + body.size
        }
    }

    private fun puzzle(path: String, head: CommentLine, lines: List<CommentLine>): Puzzle? {
        val match = pattern.find(head.text.trim()) ?: return null
        val subject = match.named("subject")?.takeIf { value -> value.isNotBlank() } ?: return null
        val body = lines.joinToString("\n") { line -> line.text.trim() }
        return Puzzle(
            hash = PuzzleHash.of(subject, body),
            subject = subject,
            description = body,
            type = match.named("type") ?: match.named("marker"),
            estimate = match.named("estimate"),
            assignee = match.named("assignee"),
            path = path,
            line = head.number,
            labels = labels(match),
        )
    }

    private fun MatchResult.named(name: String): String? =
        if (name in declared) groups[name]?.value else null

    private fun labels(match: MatchResult): List<String> =
        match.named("labels").orEmpty().split(',').map { label -> label.trim() }.filter { label -> label.isNotEmpty() }

    companion object {
        private val groupName = Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>")

        val DEFAULT_PATTERN =
            Regex(
                "^(?<marker>TODO|FIXME|HACK)(?:\\((?<type>[\\p{L}\\p{N}_-]+)(?:,\\s*(?<estimate>[^)]+))?\\))?" +
                    "(?:\\s*\\[(?<labels>[^]]*)])?:\\s*(?<subject>.+)$",
            )
    }
}
