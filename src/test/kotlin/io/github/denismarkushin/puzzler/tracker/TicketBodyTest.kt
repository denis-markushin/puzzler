package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.contains
import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle
import org.junit.jupiter.api.Test

private fun puzzle() = Puzzle("aaa111bbb222", "extract cache", "needs TTL", "debt", "30min", null, "src/Cache.kt", 42)

class TicketBodyTest {
    @Test
    fun `body carries the puzzle description`() {
        val body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")).of(puzzle())
        assertThat(body, "puzzle description is missing from the ticket body").contains("needs TTL")
    }

    @Test
    fun `body carries the estimate`() {
        val body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")).of(puzzle())
        assertThat(body, "estimate is missing or rendered under the wrong label").contains("Estimate: 30min")
    }

    @Test
    fun `body renders the permalink with the commit`() {
        val repo = RepoConfig("puzzler", "https://example.com/blob/{sha}/{path}#L{line}")
        val body = TicketBody(repo, GitContext("main", "abc123")).of(puzzle())
        assertThat(body, "permalink was not rendered from the template under its own label")
            .contains("Source: https://example.com/blob/abc123/src/Cache.kt#L42")
    }

    @Test
    fun `body falls back to HEAD when the commit is unknown`() {
        val repo = RepoConfig("puzzler", "https://example.com/blob/{sha}/{path}#L{line}")
        val body = TicketBody(repo, GitContext("main", null)).of(puzzle())
        assertThat(body, "unknown commit did not fall back to HEAD").contains("Source: https://example.com/blob/HEAD/src/Cache.kt#L42")
    }

    @Test
    fun `body falls back to a plain location without a template`() {
        val body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")).of(puzzle())
        assertThat(body, "plain location is missing or rendered under the wrong label").contains("Source: src/Cache.kt:42")
    }
}
