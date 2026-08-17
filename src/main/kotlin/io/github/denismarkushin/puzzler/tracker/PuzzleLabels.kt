package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.parse.Puzzle
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Labels the tracker uses to store state, and the rule that judges the labels a user supplies.
 * Colons, commas and whitespace are avoided rather than special-cased per tracker: Jira is the fussiest and sets the bar for all of them.
 * The puzzler prefix is reserved, otherwise a user label could be read back as state.
 */
object PuzzleLabels {
    private const val RESERVED = "puzzler-"
    private const val REPO_PREFIX = "${RESERVED}repo-"
    private const val HASH_PREFIX = "${RESERVED}hash-"
    private const val FORBIDDEN = ",:"

    fun repo(name: String) = "$REPO_PREFIX$name"

    fun hash(hash: String) = "$HASH_PREFIX$hash"

    fun hashOf(labels: List<String>) = labels.firstOrNull { label -> label.startsWith(HASH_PREFIX) }?.removePrefix(HASH_PREFIX)

    fun valid(label: String) =
        label.isNotBlank() && label.none { char -> char.isWhitespace() || char in FORBIDDEN } && !label.startsWith(RESERVED)

    fun extra(configured: List<String>, puzzle: Puzzle): List<String> {
        val (usable, rejected) = (configured + puzzle.labels).partition { label -> valid(label) }
        rejected.forEach { label -> log.warn { "label $label at ${puzzle.path}:${puzzle.line} is not usable, skipping it" } }
        return usable.distinct()
    }
}
