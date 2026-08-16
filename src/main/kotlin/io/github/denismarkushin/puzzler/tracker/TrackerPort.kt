package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.parse.Puzzle

/**
 * Открытый тикет, заведённый пазлом.
 * Хэш восстанавливается из метки и служит единственной связью с кодом.
 */
data class Ticket(
    val id: String,
    val hash: String,
)

/**
 * Контракт трекера.
 * Метка с хэшем обязана проставляться тем же запросом, что создаёт тикет.
 */
interface TrackerPort {
    fun tickets(repo: String): List<Ticket>

    fun create(puzzle: Puzzle): String

    fun close(id: String, reason: String)
}
