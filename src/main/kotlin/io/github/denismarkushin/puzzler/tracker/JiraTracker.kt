package io.github.denismarkushin.puzzler.tracker

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.TrackerConfig
import io.github.denismarkushin.puzzler.parse.Puzzle
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers

/**
 * Tracker on top of Jira REST v3.
 * The transition into the closed state is resolved by name, because transition ids are specific to each workflow.
 */
class JiraTracker(
    private val config: TrackerConfig,
    puzzle: PuzzleConfig,
    private val body: TicketBody,
    private val caller: HttpCaller,
    private val repoLabel: String,
) : TrackerPort {
    private val types = puzzle
    private val mapper = jacksonObjectMapper()
    private val base = "${config.url}/rest/api/3"

    override fun tickets(repo: String): List<Ticket> {
        val collected = mutableListOf<Ticket>()
        var start = 0
        while (true) {
            val query = mapper.createObjectNode().apply {
                put("jql", "project = ${config.project} AND labels = \"${PuzzleLabels.repo(repo)}\" AND statusCategory != Done")
                put("startAt", start)
                put("maxResults", PAGE)
                putArray("fields").add("labels")
            }
            val page = mapper.readTree(
                caller.call(request(URI.create("$base/search")).POST(BodyPublishers.ofString(query.toString())).build()),
            )
            val issues = page.path("issues")
            if (!issues.isArray || issues.isEmpty) {
                return collected
            }
            issues.forEach { issue -> ticket(issue)?.let(collected::add) }
            if (issues.size() < PAGE) {
                return collected
            }
            start += issues.size()
        }
    }

    override fun create(puzzle: Puzzle): String {
        val payload = mapper.createObjectNode().apply {
            putObject("fields").apply {
                putObject("project").put("key", config.project)
                putObject("issuetype").put("name", types.ticketType(puzzle.type) ?: config.issueType)
                put("summary", puzzle.subject)
                put("description", body.of(puzzle))
                putArray("labels").apply {
                    add(PuzzleLabels.repo(repoLabel))
                    add(PuzzleLabels.hash(puzzle.hash))
                }
            }
        }
        val created = mapper.readTree(
            caller.call(request(URI.create("$base/issue")).POST(BodyPublishers.ofString(payload.toString())).build()),
        )
        return created.path("key").asText()
    }

    override fun close(id: String, reason: String) {
        val comment = mapper.createObjectNode().put("body", reason)
        caller.call(request(URI.create("$base/issue/$id/comment")).POST(BodyPublishers.ofString(comment.toString())).build())
        val available = mapper.readTree(caller.call(request(URI.create("$base/issue/$id/transitions")).GET().build()))
            .path("transitions")
        val target = available.firstOrNull { transition -> transition.path("name").asText() == config.closeTransition }
            ?: throw TrackerError(
                "transition ${config.closeTransition} is not available for $id, workflow offers " +
                    available.joinToString(", ") { transition -> transition.path("name").asText() },
            )
        val move = mapper.createObjectNode().apply { putObject("transition").put("id", target.path("id").asText()) }
        caller.call(request(URI.create("$base/issue/$id/transitions")).POST(BodyPublishers.ofString(move.toString())).build())
    }

    private fun ticket(issue: JsonNode): Ticket? {
        val labels = issue.path("fields").path("labels").map { label -> label.asText() }
        return PuzzleLabels.hashOf(labels)?.let { hash -> Ticket(issue.path("key").asText(), hash) }
    }

    private fun request(uri: URI): HttpRequest.Builder =
        HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

    private companion object {
        const val PAGE = 100
    }
}
