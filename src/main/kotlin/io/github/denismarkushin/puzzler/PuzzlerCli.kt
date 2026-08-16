package io.github.denismarkushin.puzzler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import io.github.denismarkushin.puzzler.config.ConfigError
import io.github.denismarkushin.puzzler.config.ConfigLoader
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.git.ProcessGitCommand
import io.github.denismarkushin.puzzler.parse.PuzzleParser
import io.github.denismarkushin.puzzler.reconcile.GuardViolation
import io.github.denismarkushin.puzzler.reconcile.Reconciler
import io.github.denismarkushin.puzzler.scan.NotARepository
import io.github.denismarkushin.puzzler.scan.SourceScanner
import io.github.denismarkushin.puzzler.tracker.TrackerError
import io.github.denismarkushin.puzzler.tracker.Trackers
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

internal fun exitCode(error: RuntimeException): Int = if (error is GuardViolation) 2 else 1

/**
 * Точка входа инструмента.
 * Отвечает за разбор аргументов и коды выхода: 1 — ошибка, 2 — отказ guard'а.
 */
class PuzzlerCli : CliktCommand(name = "puzzler") {
    private val config by option("--config").default(".puzzler.yml")
    private val dryRun by option("--dry-run").flag()
    private val force by option("--force").flag()
    private val branch by option("--branch")
    private val sha by option("--sha")
    private val defaultBranch by option("--default-branch").default("main")

    init {
        versionOption(names = setOf("--version"), version = "puzzler 0.1.0", message = { it })
    }

    override fun run() {
        val root = Path.of("").toAbsolutePath()
        val git = ProcessGitCommand(root)
        try {
            val loaded = ConfigLoader.load(root.resolve(config), System.getenv())
            val context = GitContext.detect(branch, sha, System.getenv(), git)
            val tracker = Trackers.of(loaded, context, root)
            val result = PuzzlerRun(
                config = loaded,
                scanner = SourceScanner(root, loaded.scan.exclude, git),
                parser = PuzzleParser(loaded.puzzle.regex()),
                reconciler = Reconciler(tracker),
                tracker = tracker,
                context = context,
                defaultBranch = defaultBranch,
                dryRun = dryRun,
                force = force,
            ).execute()
            echo("created ${result.created}, closed ${result.closed}${if (result.dryRun) " (planned only)" else ""}")
        } catch (error: GuardViolation) {
            log.error { error.message }
            exitProcess(exitCode(error))
        } catch (error: ConfigError) {
            log.error { error.message }
            exitProcess(exitCode(error))
        } catch (error: NotARepository) {
            log.error { error.message }
            exitProcess(exitCode(error))
        } catch (error: TrackerError) {
            log.error { error.message }
            exitProcess(exitCode(error))
        }
    }
}

fun main(args: Array<String>) = PuzzlerCli().main(args)
