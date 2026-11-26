package io.github.denismarkushin.puzzler.reconcile

import io.github.denismarkushin.puzzler.parse.Puzzle
import io.github.denismarkushin.puzzler.tracker.Ticket
import io.github.denismarkushin.puzzler.tracker.TrackerPort
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * A refusal to apply changes because of a suspicious difference in state.
 */
class GuardViolation(
    message: String,
) : RuntimeException(message)

/**
 * The difference between the code and the tracker.
 */
data class ReconcilePlan(
    val create: List<Puzzle>,
    val close: List<Ticket>,
)

/**
 * Reconciliation of the code against the tracker.
 * The guards filter out cases where the difference is explained by a broken environment rather than completed work.
 * A closure of one or two tickets always passes, otherwise a small backlog could never be emptied.
 */
class Reconciler(
    private val tracker: TrackerPort,
) {
    fun plan(repo: String, puzzles: List<Puzzle>, files: Int, force: Boolean): ReconcilePlan {
        val existing = tracker.tickets(repo)
        val duplicated = existing.groupBy { ticket -> ticket.hash }.filterValues { group -> group.size > 1 }.keys
        duplicated.forEach { hash ->
            log.warn { "hash $hash is attached to several tickets in $repo, leaving them untouched" }
        }
        val healthy = existing.filterNot { ticket -> ticket.hash in duplicated }
        val known = existing.map { ticket -> ticket.hash }.toSet()
        val declared = puzzles.map { puzzle -> puzzle.hash }.toSet()
        if (files == 0 && existing.isNotEmpty()) {
            throw GuardViolation("scan read no files while $repo has ${existing.size} open tickets, refusing to close them")
        }
        val close = healthy.filterNot { ticket -> ticket.hash in declared }
        if (!force && close.size > TOLERANCE && close.size * 2 > healthy.size) {
            throw GuardViolation("closing ${close.size} of ${healthy.size} tickets in $repo exceeds the safety limit, rerun with --force if intended")
        }
        return ReconcilePlan(
            create = puzzles.filterNot { puzzle -> puzzle.hash in known }.distinctBy { puzzle -> puzzle.hash },
            close = close,
        )
    }

    private companion object {
        const val TOLERANCE = 2
    }
}
