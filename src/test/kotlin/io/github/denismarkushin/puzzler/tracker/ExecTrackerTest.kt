package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

private fun hook(root: Path, script: String): String {
    val file = root.resolve("hook.sh")
    file.writeText("#!/bin/sh\n$script\n")
    file.toFile().setExecutable(true)
    return "sh $file"
}

private fun tracker(root: Path, command: String, timeout: Duration = Duration.ofSeconds(60)) = ExecTracker(
    command = command,
    root = root,
    puzzle = PuzzleConfig(),
    body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")),
    repoLabel = "puzzler",
    timeout = timeout,
)

private fun puzzle() = Puzzle("aaa111bbb222", "вынести кэш", "", null, null, null, "Cache.kt", 1)

class ExecTrackerTest {
    @Test
    @Timeout(30)
    fun `tracker parses tickets from the hook`(@TempDir root: Path) {
        val command = hook(root, """echo '{"tickets":[{"id":"PROJ-7","hash":"aaa111bbb222"}]}'""")
        val tickets = tracker(root, command).tickets("puzzler")
        assertThat(tickets, "hook output was not parsed into tickets").containsExactly(Ticket("PROJ-7", "aaa111bbb222"))
    }

    @Test
    @Timeout(30)
    fun `tracker returns the identifier produced by the hook`(@TempDir root: Path) {
        val command = hook(root, """echo '{"id":"PROJ-9"}'""")
        val id = tracker(root, command).create(puzzle())
        assertThat(id, "hook identifier was not returned").isEqualTo("PROJ-9")
    }

    @Test
    @Timeout(30)
    fun `tracker passes the hash in the hash field of the create request`(@TempDir root: Path) {
        val captured = root.resolve("captured.json")
        val command = hook(root, "cat > '$captured'; echo '{\"id\":\"PROJ-9\"}'")
        tracker(root, command).create(puzzle())
        val sent = jacksonObjectMapper().readTree(captured.toFile().readText())
        assertThat(sent.path("hash").asText(), "hash field did not carry the puzzle hash").isEqualTo("aaa111bbb222")
    }

    @Test
    @Timeout(30)
    fun `tracker fails when the hook exits nonzero`(@TempDir root: Path) {
        val command = hook(root, "exit 3")
        val failure = runCatching { tracker(root, command).tickets("puzzler") }.exceptionOrNull()
        assertThat(failure is TrackerError, "a failing hook did not raise TrackerError").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `tracker fails on unparsable hook output`(@TempDir root: Path) {
        val command = hook(root, "echo not-json")
        val failure = runCatching { tracker(root, command).tickets("puzzler") }.exceptionOrNull()
        assertThat(failure is TrackerError, "unparsable hook output did not raise TrackerError").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `tracker gives up on a hook that never exits`(@TempDir root: Path) {
        val command = hook(root, "sleep 20")
        val failure = runCatching { tracker(root, command, Duration.ofSeconds(1)).tickets("puzzler") }.exceptionOrNull()
        assertThat(failure is TrackerError, "a hung hook was not bounded by the timeout").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `tracker rejects a search response without a tickets array`(@TempDir root: Path) {
        val command = hook(root, "echo '{}'")
        val failure = runCatching { tracker(root, command).tickets("puzzler") }.exceptionOrNull()
        assertThat(failure is TrackerError, "an empty search response was not rejected as a hook failure").isEqualTo(true)
    }

    @Test
    @Timeout(30)
    fun `tracker rejects a create response without an id`(@TempDir root: Path) {
        val command = hook(root, "echo '{}'")
        val failure = runCatching { tracker(root, command).create(puzzle()) }.exceptionOrNull()
        assertThat(failure is TrackerError, "an empty create response was not rejected as a hook failure").isEqualTo(true)
    }
}
