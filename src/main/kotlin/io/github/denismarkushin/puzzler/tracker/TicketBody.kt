package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle

/**
 * Текст тикета, собранный из пазла.
 * Позиция в коде идёт сюда, а не в идентичность, поэтому её устаревание безопасно.
 */
class TicketBody(
    private val repo: RepoConfig,
    private val context: GitContext,
) {
    fun of(puzzle: Puzzle): String = buildString {
        if (puzzle.description.isNotBlank()) {
            appendLine(puzzle.description)
            appendLine()
        }
        appendLine("Источник: ${location(puzzle)}")
        puzzle.estimate?.let { estimate -> appendLine("Оценка: $estimate") }
        appendLine("Заведено puzzler, пазл живёт в коде")
    }

    private fun location(puzzle: Puzzle): String =
        repo.permalink
            ?.replace("{sha}", context.sha ?: "HEAD")
            ?.replace("{path}", puzzle.path)
            ?.replace("{line}", puzzle.line.toString())
            ?: "${puzzle.path}:${puzzle.line}"
}
