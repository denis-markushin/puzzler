package io.github.denismarkushin.puzzler.tracker

import io.github.denismarkushin.puzzler.config.PuzzlerConfig
import io.github.denismarkushin.puzzler.git.GitContext
import java.nio.file.Path

/**
 * Selection of a tracker implementation from the configuration.
 * The only place where the type from the config turns into an object.
 */
object Trackers {
    fun of(config: PuzzlerConfig, context: GitContext, root: Path): TrackerPort {
        val body = TicketBody(config.repo, context)
        val caller = HttpCaller()
        return when (config.tracker.type) {
            "jira" -> JiraTracker(config.tracker, config.puzzle, body, caller, config.repo.name, 2)
            "github" -> GithubTracker(config.tracker, config.puzzle, body, caller, config.repo.name)
            "gitlab" -> GitlabTracker(config.tracker, config.puzzle, body, caller, config.repo.name)
            "exec" -> ExecTracker(config.tracker.command.orEmpty(), root, config.puzzle, body, config.repo.name)
            else -> throw TrackerError("unsupported tracker type ${config.tracker.type}")
        }
    }
}
