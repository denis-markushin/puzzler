package io.github.denismarkushin.puzzler.parse

import io.github.denismarkushin.puzzler.hash.PuzzleHash

/**
 * Turning a comment block into a puzzle.
 * The headline is parsed with the configured regexp, the body is the following lines with a deeper indent.
 */
class PuzzleParser(
    private val pattern: Regex,
) {
    private val declared = groupName.findAll(pattern.pattern).map { match -> match.groupValues[1] }.toSet()

    fun parse(block: CommentBlock): Puzzle? {
        val head = block.lines.firstOrNull() ?: return null
        val match = pattern.find(head.text.trim()) ?: return null
        val subject = match.named("subject")?.takeIf { value -> value.isNotBlank() } ?: return null
        val body = block.lines.asSequence()
            .drop(1)
            .takeWhile { line -> line.indent > head.indent }
            .joinToString("\n") { line -> line.text.trim() }
        return Puzzle(
            hash = PuzzleHash.of(subject, body),
            subject = subject,
            description = body,
            type = match.named("type") ?: match.named("marker"),
            estimate = match.named("estimate"),
            assignee = match.named("assignee"),
            path = block.path,
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
                "^(?<marker>TODO|FIXME|HACK)(?:\\((?<type>[\\w-]+)(?:,\\s*(?<estimate>[^)]+))?\\))?" +
                    "(?:\\s*\\[(?<labels>[^]]*)])?:\\s*(?<subject>.+)$",
            )
    }
}
