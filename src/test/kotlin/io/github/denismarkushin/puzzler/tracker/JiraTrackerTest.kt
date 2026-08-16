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
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.RepoConfig
import io.github.denismarkushin.puzzler.config.TrackerConfig
import io.github.denismarkushin.puzzler.git.GitContext
import io.github.denismarkushin.puzzler.parse.Puzzle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Base64

private fun server(): WireMockServer = WireMockServer(options().dynamicPort()).apply { start() }

private fun jira(baseUrl: String, token: String = "secret", version: Int = 2) = JiraTracker(
    config = TrackerConfig(type = "jira", url = baseUrl, project = "PROJ", issueType = "Task", closeTransition = "Done", token = token),
    puzzle = PuzzleConfig(),
    body = TicketBody(RepoConfig("puzzler"), GitContext("main", "abc123")),
    caller = HttpCaller(pause = {}),
    repoLabel = "puzzler",
    version = version,
)

private fun puzzle() = Puzzle("aaa111bbb222", "extract cache", "", null, null, null, "Cache.kt", 1)

class JiraTrackerTest {
    @Test
    @Timeout(30)
    fun `tracker reads hashes from labels`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlEqualTo("/rest/api/2/search")).willReturn(
                    aResponse().withStatus(200).withBody(
                        """{"issues":[{"key":"PROJ-7","fields":{"labels":["puzzler-repo-puzzler","puzzler-hash-aaa111bbb222"]}}],"total":1}""",
                    ),
                ),
            )
            val tickets = jira(wiremock.baseUrl()).tickets("puzzler")
            assertThat(tickets, "hash was not recovered from issue labels").containsExactly(Ticket("PROJ-7", "aaa111bbb222"))
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker sends the hash label together with the issue`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue")).willReturn(aResponse().withStatus(201).withBody("""{"key":"PROJ-9"}""")))
            jira(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/issue"))).single().bodyAsString,
            )
            val labels = sent.path("fields").path("labels").map { label -> label.asText() }
            assertThat(labels, "hash label was not part of the create request fields.labels").contains("puzzler-hash-aaa111bbb222")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker labels the issue with the configured repository`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue")).willReturn(aResponse().withStatus(201).withBody("""{"key":"PROJ-9"}""")))
            jira(wiremock.baseUrl()).create(puzzle())
            val sent = jacksonObjectMapper().readTree(
                wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/issue"))).single().bodyAsString,
            )
            val labels = sent.path("fields").path("labels").map { label -> label.asText() }
            assertThat(labels, "repo label was not taken from the injected repoLabel").contains("puzzler-repo-puzzler")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker returns the created issue key`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue")).willReturn(aResponse().withStatus(201).withBody("""{"key":"PROJ-9"}""")))
            val id = jira(wiremock.baseUrl()).create(puzzle())
            assertThat(id, "created issue key was not returned").isEqualTo("PROJ-9")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker resolves the transition by name`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue/PROJ-9/comment")).willReturn(aResponse().withStatus(201).withBody("{}")))
            wiremock.stubFor(
                get(urlEqualTo("/rest/api/2/issue/PROJ-9/transitions")).willReturn(
                    aResponse().withStatus(200).withBody("""{"transitions":[{"id":"31","name":"Done"},{"id":"11","name":"In Progress"}]}"""),
                ),
            )
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue/PROJ-9/transitions")).willReturn(aResponse().withStatus(204)))
            jira(wiremock.baseUrl()).close("PROJ-9", "puzzle removed in abc123")
            val sent = wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/issue/PROJ-9/transitions"))).single().bodyAsString
            assertThat(sent, "transition name was not resolved to its identifier").contains("31")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker reports an unknown transition`() {
        val wiremock = server()
        try {
            wiremock.stubFor(post(urlEqualTo("/rest/api/2/issue/PROJ-9/comment")).willReturn(aResponse().withStatus(201).withBody("{}")))
            wiremock.stubFor(
                get(urlEqualTo("/rest/api/2/issue/PROJ-9/transitions")).willReturn(
                    aResponse().withStatus(200).withBody("""{"transitions":[{"id":"11","name":"In Progress"}]}"""),
                ),
            )
            val failure = runCatching { jira(wiremock.baseUrl()).close("PROJ-9", "puzzle removed in abc123") }.exceptionOrNull()
            assertThat(failure is TrackerError, "a missing transition did not raise TrackerError").isEqualTo(true)
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker targets the version two api`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlEqualTo("/rest/api/2/search"))
                    .willReturn(aResponse().withStatus(200).withBody("""{"issues":[],"total":0}""")),
            )
            jira(wiremock.baseUrl()).tickets("puzzler")
            val count = wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/search"))).size
            assertThat(count, "requests did not target the version two api").isEqualTo(1)
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker sends basic authorization when the token carries a user`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlEqualTo("/rest/api/2/search"))
                    .willReturn(aResponse().withStatus(200).withBody("""{"issues":[],"total":0}""")),
            )
            jira(wiremock.baseUrl(), token = "denis:secret").tickets("puzzler")
            val sent = wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/search"))).single().getHeader("Authorization")
            assertThat(sent, "a token carrying a user did not produce basic authorization")
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("denis:secret".toByteArray(Charsets.UTF_8)))
        } finally {
            wiremock.stop()
        }
    }

    @Test
    @Timeout(30)
    fun `tracker sends bearer authorization for a plain token`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                post(urlEqualTo("/rest/api/2/search"))
                    .willReturn(aResponse().withStatus(200).withBody("""{"issues":[],"total":0}""")),
            )
            jira(wiremock.baseUrl(), token = "personalaccesstoken").tickets("puzzler")
            val sent = wiremock.findAll(postRequestedFor(urlEqualTo("/rest/api/2/search"))).single().getHeader("Authorization")
            assertThat(sent, "a plain token did not stay on bearer authorization").isEqualTo("Bearer personalaccesstoken")
        } finally {
            wiremock.stop()
        }
    }
}
