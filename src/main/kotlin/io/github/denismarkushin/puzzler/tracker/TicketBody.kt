package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle

/**
 * Ticket text assembled from a puzzle.
 * The code location goes here rather than into the identity, so it's going stale is harmless.
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
        appendLine("Source: ${location(puzzle)}")
        puzzle.estimate?.let { estimate -> appendLine("Estimate: $estimate") }
        appendLine("Filed by puzzler, the puzzle lives in the code")
    }

    private fun location(puzzle: Puzzle): String =
        repo.permalink
            ?.replace("{sha}", context.sha ?: "HEAD")
            ?.replace("{path}", puzzle.path)
            ?.replace("{line}", puzzle.line.toString())
            ?: "${puzzle.path}:${puzzle.line}"
}
