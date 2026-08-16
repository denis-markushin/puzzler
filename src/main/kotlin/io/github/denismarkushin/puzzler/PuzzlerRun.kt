package io.github.denismarkushin.puzzler

import io.github.denismarkushin.puzzler.config.PuzzlerConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.PuzzleParser
import io.github.denismarkushin.puzzler.reconcile.GuardViolation
import io.github.denismarkushin.puzzler.reconcile.Reconciler
import io.github.denismarkushin.puzzler.scan.SourceScanner
import io.github.denismarkushin.puzzler.tracker.TrackerPort
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Outcome of a run.
 */
data class RunResult(
    val created: Int,
    val closed: Int,
    val dryRun: Boolean,
)

/**
 * Scenario of a single run.
 * Applying changes is allowed only on the default branch; in every other case, including an unknown branch, the run degrades into a plan.
 */
class PuzzlerRun(
    private val config: PuzzlerConfig,
    private val scanner: SourceScanner,
    private val parser: PuzzleParser,
    private val reconciler: Reconciler,
    private val tracker: TrackerPort,
    private val context: GitContext,
    private val defaultBranch: String,
    private val dryRun: Boolean,
    private val force: Boolean,
) {
    fun execute(): RunResult {
        val onDefaultBranch = !context.branch.isNullOrBlank() && context.branch == defaultBranch
        val planned = dryRun || !onDefaultBranch
        val puzzles = scanner.blocks().mapNotNull { block -> parser.parse(block) }
        if (planned) {
            val plan = try {
                reconciler.plan(config.repo.name, puzzles, force)
            } catch (violation: GuardViolation) {
                log.warn { violation.message }
                return RunResult(0, 0, true)
            }
            log.info { "planning only: ${plan.create.size} to create, ${plan.close.size} to close in ${config.repo.name}" }
            return RunResult(plan.create.size, plan.close.size, true)
        }
        val plan = reconciler.plan(config.repo.name, puzzles, force)
        plan.create.forEach { puzzle ->
            val id = tracker.create(puzzle)
            log.info { "created $id for puzzle ${puzzle.hash} at ${puzzle.path}:${puzzle.line}" }
        }
        plan.close.forEach { ticket ->
            tracker.close(ticket.id, "puzzle removed in ${context.sha ?: "HEAD"}")
            log.info { "closed ${ticket.id} because puzzle ${ticket.hash} left the code" }
        }
        return RunResult(plan.create.size, plan.close.size, false)
    }
}
