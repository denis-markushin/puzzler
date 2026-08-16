package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpRequest

private fun server(): WireMockServer = WireMockServer(options().dynamicPort()).apply { start() }

class HttpCallerTest {
    @Test
    fun `caller returns the body of a successful response`() {
        val wiremock = server()
        try {
            wiremock.stubFor(get(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200).withBody("""{"id":"1"}""")))
            val body = HttpCaller().call(HttpRequest.newBuilder(URI.create("${wiremock.baseUrl()}/ok")).GET().build())
            assertThat(body, "successful response body was not returned").isEqualTo("""{"id":"1"}""")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    fun `caller retries a server error and then succeeds`() {
        val wiremock = server()
        try {
            wiremock.stubFor(
                get(urlEqualTo("/flaky")).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/flaky")).inScenario("flaky").whenScenarioStateIs("recovered")
                    .willReturn(aResponse().withStatus(200).withBody("done")),
            )
            val body = HttpCaller(pause = {}).call(HttpRequest.newBuilder(URI.create("${wiremock.baseUrl()}/flaky")).GET().build())
            assertThat(body, "caller did not retry a transient server error").isEqualTo("done")
        } finally {
            wiremock.stop()
        }
    }

    @Test
    fun `caller gives up after the last attempt`() {
        val wiremock = server()
        wiremock.stubFor(get(urlEqualTo("/down")).willReturn(aResponse().withStatus(500)))
        val failure = runCatching {
            HttpCaller(pause = {}).call(HttpRequest.newBuilder(URI.create("${wiremock.baseUrl()}/down")).GET().build())
        }.exceptionOrNull()
        wiremock.stop()
        assertThat(failure is TrackerError, "a permanently failing endpoint did not raise TrackerError").isEqualTo(true)
    }

    @Test
    fun `caller does not retry a client error`() {
        val wiremock = server()
        wiremock.stubFor(get(urlEqualTo("/denied")).willReturn(aResponse().withStatus(403)))
        runCatching {
            HttpCaller(pause = {}).call(HttpRequest.newBuilder(URI.create("${wiremock.baseUrl()}/denied")).GET().build())
        }
        val count = wiremock.findAll(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/denied"))).size
        wiremock.stop()
        assertThat(count, "a client error was retried instead of failing fast").isEqualTo(1)
    }

    @Test
    fun `caller wraps a dropped connection as a tracker error after retrying`() {
        val wiremock = server()
        val deadUrl = wiremock.baseUrl()
        wiremock.stop()
        val failure = runCatching {
            HttpCaller(pause = {}).call(HttpRequest.newBuilder(URI.create("$deadUrl/unreachable")).GET().build())
        }.exceptionOrNull()
        assertThat(failure is TrackerError, "a dropped connection was not retried and wrapped as TrackerError").isEqualTo(true)
    }
}
