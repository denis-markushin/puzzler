package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.config.TrackerConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

private fun server(): WireMockServer = WireMockServer(options().dynamicPort()).apply { start() }

private fun github(baseUrl: String, labels: List<String> = emptyList()) = GithubTracker(
    config = TrackerConfig(
        type = "github",
        url = baseUrl,
        project = "denis-markushin/puzzler",
        token = "secret",
        labels = labels,
    ),
    puzzle = PuzzleConfig(),
    body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")),
    caller = HttpCaller(pause = {}),
    repoLabel = "puzzler",
)

private fun puzzle(labels: List<String> = emptyList()) =
    Puzzle("aaa111bbb222", "extract cache", "", null, null, null, "Cache.kt", 1, labels)

class GithubTrackerTest {
    @Test
    @Timeout(30)
    fun `tracker reads hashes from labels`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                get(urlPathEqualTo("/repos/denis-markushin/puzzler/issues")).willReturn(
                    aResponse().withStatus(200).withBody(
                        """[{"number":7,"labels":[{"name":"puzzler-repo-puzzler"},{"name":"puzzler-hash-aaa111bbb222"}]}]""",
                    ),
                ),
            )
            val tickets = github(wiremock.baseUrl()).tickets("puzzler")
            assertThat(tickets, "hash was not recovered from issue labels").containsExactly(Ticket("7", "aaa111bbb222"))
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker sends the hash label together with the issue`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlPathEqualTo("/repos/denis-markushin/puzzler/issues"))
                    .willReturn(aResponse().withStatus(201).withBody("""{"number":9}""")),
            )
            github(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlPathEqualTo("/repos/denis-markushin/puzzler/issues"))).single().bodyAsString,
            )
            val labels = sent.path("labels").map { label -> label.asText() }
            assertThat(labels, "hash label was not part of the create request labels").contains("puzzler-hash-aaa111bbb222")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker labels the issue with the configured repository`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlPathEqualTo("/repos/denis-markushin/puzzler/issues"))
                    .willReturn(aResponse().withStatus(201).withBody("""{"number":9}""")),
            )
            github(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlPathEqualTo("/repos/denis-markushin/puzzler/issues"))).single().bodyAsString,
            )
            val labels = sent.path("labels").map { label -> label.asText() }
            assertThat(labels, "repo label was not taken from the injected repoLabel").contains("puzzler-repo-puzzler")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker returns the created issue number`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlPathEqualTo("/repos/denis-markushin/puzzler/issues"))
                    .willReturn(aResponse().withStatus(201).withBody("""{"number":9}""")),
            )
            val id = github(wiremock.baseUrl()).create(puzzle())
            assertThat(id, "created issue number was not returned").isEqualTo("9")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker closes an issue`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/repos/denis-markushin/puzzler/issues/9/comments")).willReturn(aResponse().withStatus(201).withBody("{}")))
            wiremock.stubFor(patch(urlEqualTo("/repos/denis-markushin/puzzler/issues/9")).willReturn(aResponse().withStatus(200).withBody("{}")))
            github(wiremock.baseUrl()).close("9", "puzzle removed in abc123")
            val patched = wiremock.findAll(patchRequestedFor(urlEqualTo("/repos/denis-markushin/puzzler/issues/9"))).single().bodyAsString
            assertThat(patched, "issue was not switched to the closed state").contains("closed")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker sends the configured and the puzzle labels`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlEqualTo("/repos/denis-markushin/puzzler/issues"))
                    .willReturn(aResponse().withStatus(201).withBody("""{"number":9}""")),
            )
            github(wiremock.baseUrl(), labels = listOf("tech-debt")).create(puzzle(listOf("perf")))
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlEqualTo("/repos/denis-markushin/puzzler/issues"))).single().bodyAsString,
            )
            val labels = sent.path("labels").map { label -> label.asText() }
            assertThat(labels, "labels from the config and the puzzle did not both reach the create request")
                .containsAtLeast("tech-debt", "perf")
        } finally {
            wiremock.stop()
        }
    }
}
