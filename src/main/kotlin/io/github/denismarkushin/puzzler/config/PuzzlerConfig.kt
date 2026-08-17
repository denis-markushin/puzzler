package io.github.denismarkushin.puzzler.config

import io.github.denismarkushin.puzzler.parse.PuzzleParser

/**
 * Tracker settings.
 * The url and project fields are interpreted differently by each implementation; command is used only by type exec.
 * The labels are added to every ticket this repository files, on top of whatever the puzzle itself declares.
 */
data class TrackerConfig(
    val type: String,
    val url: String? = null,
    val project: String? = null,
    val issueType: String = "Task",
    val closeTransition: String = "Done",
    val token: String? = null,
    val command: String? = null,
    val labels: List<String> = emptyList(),
)

/**
 * Binding of puzzles to a repository.
 * The name goes into the label and acts as a filter, the link template is reference material in the ticket body.
 */
data class RepoConfig(
    val name: String,
    val permalink: String? = null,
)

/**
 * Boundaries of the working tree traversal.
 */
data class ScanConfig(
    val exclude: List<String> = emptyList(),
)

/**
 * Puzzle format and the mapping of its type to a ticket type.
 */
data class PuzzleConfig(
    val pattern: String? = null,
    val typeMapping: Map<String, String> = emptyMap(),
) {
    fun regex(): Regex = pattern?.let { value -> Regex(value) } ?: PuzzleParser.DEFAULT_PATTERN

    fun ticketType(raw: String?): String? = raw?.let { value -> typeMapping[value] ?: value }
}

/**
 * Complete configuration of a run.
 */
data class PuzzlerConfig(
    val tracker: TrackerConfig,
    val repo: RepoConfig,
    val scan: ScanConfig = ScanConfig(),
    val puzzle: PuzzleConfig = PuzzleConfig(),
)
