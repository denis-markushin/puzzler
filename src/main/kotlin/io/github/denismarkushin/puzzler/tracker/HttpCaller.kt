package io.github.denismarkushin.puzzler.tracker

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * A tracker call error that survived the retries.
 */
class TrackerError(
    message: String,
) : RuntimeException(message)

/**
 * The single point of exit to the network.
 * Retries 429s, five-hundreds and dropped connections; client errors fail immediately.
 * Waiting is bounded on both sides: without timeouts a hung tracker would stall the whole run forever.
 * `InterruptedException` is deliberately not caught — it means the process is shutting down.
 */
class HttpCaller(
    private val attempts: Int = 3,
    private val pause: (Long) -> Unit = Thread::sleep,
) {
    private val client = HttpClient.newBuilder().connectTimeout(CONNECT).build()

    fun call(request: HttpRequest): String {
        val bounded = bounded(request)
        var last = ""
        repeat(attempts) { attempt ->
            val response = try {
                client.send(bounded, HttpResponse.BodyHandlers.ofString())
            } catch (error: IOException) {
                last = "${error.javaClass.simpleName} ${error.message}"
                null
            }
            if (response != null && response.statusCode() in 200..299) {
                return response.body()
            }
            if (response != null) {
                last = "${response.statusCode()} ${response.body().take(500)}"
                if (!isTransient(response.statusCode())) {
                    throw TrackerError("tracker rejected ${request.method()} ${request.uri()} with $last")
                }
            }
            log.warn { "tracker answered $last for ${request.uri()}, attempt ${attempt + 1} of $attempts" }
            if (attempt + 1 < attempts) {
                pause(BACKOFF * (1L shl attempt))
            }
        }
        throw TrackerError("tracker kept failing on ${request.method()} ${request.uri()} with $last")
    }

    private fun bounded(request: HttpRequest) =
        if (request.timeout().isPresent) {
            request
        } else {
            HttpRequest.newBuilder(request) { _, _ -> true }.timeout(RESPONSE).build()
        }

    private fun isTransient(status: Int) = status == 429 || status >= 500

    private companion object {
        const val BACKOFF = 500L
        val CONNECT: Duration = Duration.ofSeconds(10)
        val RESPONSE: Duration = Duration.ofSeconds(30)
    }
}
