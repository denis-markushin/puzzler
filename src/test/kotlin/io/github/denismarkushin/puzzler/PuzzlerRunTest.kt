package io.github.denismarkushin.puzzler

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.PuzzlerConfig
import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.config.ScanConfig
import io.github.denismarkushin.puzzler.config.TrackerConfig
import io.github.denismarkushin.puzzler.git.GitCommand
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle
import io.github.denismarkushin.puzzler.parse.PuzzleParser
import io.github.denismarkushin.puzzler.reconcile.Reconciler
import io.github.denismarkushin.puzzler.scan.SourceScanner
import io.github.denismarkushin.puzzler.tracker.Ticket
import io.github.denismarkushin.puzzler.tracker.TrackerPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

private class RecordingTracker(
    private val existing: MutableList<Ticket>,
) : TrackerPort {
    val created = mutableListOf<Puzzle>()
    val closed = mutableListOf<String>()

    override fun tickets(repo: String) = existing.toList()

    override fun create(puzzle: Puzzle): String {
        created.add(puzzle)
        return "NEW-${created.size}"
    }

    override fun close(id: String, reason: String) {
        closed.add(id)
    }
}

private class ListingGit(
    private val listing: String,
) : GitCommand {
    override fun run(vararg args: String): String = listing
}

private fun config() = PuzzlerConfig(
    tracker = TrackerConfig(type = "github", project = "denis-markushin/puzzler", token = "secret"),
    repo = RepoConfig("puzzler"),
    scan = ScanConfig(),
    puzzle = PuzzleConfig(),
)

private fun run(root: Path, tracker: TrackerPort, branch: String, dryRun: Boolean) = PuzzlerRun(
    config = config(),
    scanner = SourceScanner(root, emptyList(), ListingGit("Cache.kt")),
    parser = PuzzleParser(PuzzleParser.DEFAULT_PATTERN),
    reconciler = Reconciler(tracker),
    tracker = tracker,
    context = GitContext(branch, "abc123"),
    defaultBranch = "main",
    dryRun = dryRun,
    force = false,
)

class PuzzlerRunTest {
    @Test
    @Timeout(30)
    fun `run creates a ticket for a new puzzle`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        run(root, tracker, "main", false).execute()
        assertThat(tracker.created.single().subject, "a new puzzle did not reach the tracker").isEqualTo("вынести кэш")
    }

    @Test
    @Timeout(30)
    fun `run closes a ticket whose puzzle is gone`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: живой пазл")
        val hash = PuzzleParser(PuzzleParser.DEFAULT_PATTERN)
            .parse(SourceScanner(root, emptyList(), ListingGit("Cache.kt")).blocks().single())!!.hash
        val tracker = RecordingTracker(mutableListOf(Ticket("OLD-1", hash), Ticket("OLD-2", "gonehash1234")))
        run(root, tracker, "main", false).execute()
        assertThat(tracker.closed, "an orphaned ticket was not closed").isEqualTo(listOf("OLD-2"))
    }

    @Test
    @Timeout(30)
    fun `dry run touches nothing`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        run(root, tracker, "main", true).execute()
        assertThat(tracker.created.size, "dry run created a ticket anyway").isEqualTo(0)
    }

    @Test
    @Timeout(30)
    fun `feature branch degrades into a dry run`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        val result = run(root, tracker, "feature/cache", false).execute()
        assertThat(result.dryRun, "a feature branch was allowed to apply changes").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `unknown branch degrades into a dry run`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        val result = run(root, tracker, "", false).execute()
        assertThat(result.dryRun, "an unknown branch was allowed to apply changes").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `feature branch never calls tracker create`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        run(root, tracker, "feature/cache", false).execute()
        assertThat(tracker.created.size, "a feature branch was allowed to create tickets").isEqualTo(0)
    }

    @Test
    @Timeout(30)
    fun `dry run on default branch never calls tracker create`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("// TODO: вынести кэш")
        val tracker = RecordingTracker(mutableListOf())
        run(root, tracker, "main", true).execute()
        assertThat(tracker.created.size, "a dry run was allowed to create tickets").isEqualTo(0)
    }
}
