package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.parse.Puzzle

/**
 * An open ticket filed by a puzzle.
 * The hash is recovered from the label and is the only link back to the code.
 */
data class Ticket(
    val id: String,
    val hash: String,
)

/**
 * Contract of a tracker.
 * The hash label must be set by the same request that creates the ticket.
 */
interface TrackerPort {
    fun tickets(repo: String): List<Ticket>

    fun create(puzzle: Puzzle): String

    fun close(id: String, reason: String)
}
