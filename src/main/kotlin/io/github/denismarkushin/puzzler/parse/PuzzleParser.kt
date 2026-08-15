package io.github.denismarkushin.puzzler.parse

import io.github.denismarkushin.puzzler.hash.PuzzleHash

/**
 * Превращение блока комментария в пазл.
 * Заголовок разбирается заданным regexp, телом становятся следующие строки с бо́льшим отступом.
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
            .map { line -> line.text.trim() }
            .joinToString("\n")
        return Puzzle(
            hash = PuzzleHash.of(subject, body),
            subject = subject,
            description = body,
            type = match.named("type") ?: match.named("marker"),
            estimate = match.named("estimate"),
            assignee = match.named("assignee"),
            path = block.path,
            line = head.number,
        )
    }

    private fun MatchResult.named(name: String): String? =
        if (name in declared) groups[name]?.value else null

    companion object {
        private val groupName = Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>")

        val DEFAULT_PATTERN =
            Regex("^(?<marker>TODO|FIXME|HACK)(?:\\((?<type>[\\w-]+)(?:,\\s*(?<estimate>[^)]+))?\\))?:\\s*(?<subject>.+)$")
    }
}
