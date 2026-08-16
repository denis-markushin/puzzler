package io.github.denismarkushin.puzzler.parse

/**
 * A unit of work declared in the code.
 * The hash serves as identity, the path and line are only reference material in the ticket body.
 */
data class Puzzle(
    val hash: String,
    val subject: String,
    val description: String,
    val type: String?,
    val estimate: String?,
    val assignee: String?,
    val path: String,
    val line: Int,
)
