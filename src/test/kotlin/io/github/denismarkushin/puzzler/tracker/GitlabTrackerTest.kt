package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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

private fun gitlab(baseUrl: String, labels: List<String> = emptyList()) = GitlabTracker(
    config = TrackerConfig(type = "gitlab", url = baseUrl, project = "42", token = "secret", labels = labels),
    puzzle = PuzzleConfig(),
    body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")),
    caller = HttpCaller(pause = {}),
    repoLabel = "puzzler",
)

private fun puzzle(labels: List<String> = emptyList()) =
    Puzzle("aaa111bbb222", "extract cache", "", null, null, null, "Cache.kt", 1, labels)

class GitlabTrackerTest {
    @Test
    @Timeout(30)
    fun `tracker reads hashes from labels`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                get(urlPathEqualTo("/api/v4/projects/42/issues")).willReturn(
                    aResponse().withStatus(200)
                        .withBody("""[{"iid":7,"labels":["puzzler-repo-puzzler","puzzler-hash-aaa111bbb222"]}]"""),
                ),
            )
            val tickets = gitlab(wiremock.baseUrl()).tickets("puzzler")
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
            wiremock.stubFor(post(urlPathEqualTo("/api/v4/projects/42/issues")).willReturn(aResponse().withStatus(201).withBody("""{"iid":9}""")))
            gitlab(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlPathEqualTo("/api/v4/projects/42/issues"))).single().bodyAsString,
            )
            val labels = sent.path("labels").asText().split(",")
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
            wiremock.stubFor(post(urlPathEqualTo("/api/v4/projects/42/issues")).willReturn(aResponse().withStatus(201).withBody("""{"iid":9}""")))
            gitlab(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlPathEqualTo("/api/v4/projects/42/issues"))).single().bodyAsString,
            )
            val labels = sent.path("labels").asText().split(",")
            assertThat(labels, "repo label was not taken from the injected repoLabel").contains("puzzler-repo-puzzler")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker returns the created issue iid`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlPathEqualTo("/api/v4/projects/42/issues")).willReturn(aResponse().withStatus(201).withBody("""{"iid":9}""")))
            val id = gitlab(wiremock.baseUrl()).create(puzzle())
            assertThat(id, "created issue iid was not returned").isEqualTo("9")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker closes an issue with a state event`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/api/v4/projects/42/issues/9/notes")).willReturn(aResponse().withStatus(201).withBody("{}")))
            wiremock.stubFor(put(urlEqualTo("/api/v4/projects/42/issues/9")).willReturn(aResponse().withStatus(200).withBody("{}")))
            gitlab(wiremock.baseUrl()).close("9", "puzzle removed in abc123")
            val sent = wiremock.findAll(putRequestedFor(urlEqualTo("/api/v4/projects/42/issues/9"))).single().bodyAsString
            assertThat(sent, "issue was not closed through a state event").contains("close")
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
                post(urlEqualTo("/api/v4/projects/42/issues")).willReturn(aResponse().withStatus(201).withBody("""{"iid":9}""")),
            )
            gitlab(wiremock.baseUrl(), labels = listOf("tech-debt")).create(puzzle(listOf("perf")))
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlEqualTo("/api/v4/projects/42/issues"))).single().bodyAsString,
            )
            assertThat(sent.path("labels").asText(), "labels from the config and the puzzle did not both reach the create request")
                .isEqualTo("puzzler-repo-puzzler,puzzler-hash-aaa111bbb222,tech-debt,perf")
        } finally {
            wiremock.stop()
        }
    }
}
